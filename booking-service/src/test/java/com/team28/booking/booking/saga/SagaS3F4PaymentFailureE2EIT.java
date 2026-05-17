package com.team28.booking.booking.saga;

import com.team28.booking.booking.cache.CacheInvalidator;
import com.team28.booking.booking.messaging.BookingEventPublisher;
import com.team28.booking.booking.messaging.BookingPaymentEventListener;
import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.observer.MongoEventLogger;
import com.team28.booking.booking.repository.BookingItemRepository;
import com.team28.booking.booking.repository.BookingRepository;
import com.team28.booking.booking.service.BookingService;
import com.team28.booking.contracts.dto.ProviderDTO;
import com.team28.booking.contracts.dto.TimeSlotDTO;
import com.team28.booking.contracts.dto.UserDTO;
import com.team28.booking.contracts.events.BookingCancelledEvent;
import com.team28.booking.contracts.events.BookingCompletedEvent;
import com.team28.booking.contracts.events.PaymentFailedEvent;
import com.team28.booking.contracts.events.PaymentInitiatedEvent;
import com.team28.booking.contracts.feign.BookingServiceClient;
import com.team28.booking.contracts.feign.CalendarServiceClient;
import com.team28.booking.contracts.feign.InvoiceServiceClient;
import com.team28.booking.contracts.feign.ProviderServiceClient;
import com.team28.booking.contracts.feign.UserServiceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.cache.CacheManager;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class SagaS3F4PaymentFailureE2EIT {

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"))
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void completingBookingInitiatesPaymentThenPaymentFailureCompensatesBooking() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(rabbit.getHost(), rabbit.getAmqpPort());
        connectionFactory.setUsername(rabbit.getAdminUsername());
        connectionFactory.setPassword(rabbit.getAdminPassword());

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);

        DirectExchange bookingExchange = new DirectExchange("saga.booking.events");
        DirectExchange paymentExchange = new DirectExchange("saga.payment.events");
        Queue invoiceQueue = new Queue("saga.payment.listener", false);
        Queue feedbackQueue = new Queue("saga.booking.feedback", false);
        Queue initiatedQueue = new Queue("saga.payment.initiated", false);
        Queue compensationQueue = new Queue("saga.booking.cancelled", false);
        admin.declareExchange(bookingExchange);
        admin.declareExchange(paymentExchange);
        admin.declareQueue(invoiceQueue);
        admin.declareQueue(feedbackQueue);
        admin.declareQueue(initiatedQueue);
        admin.declareQueue(compensationQueue);
        admin.declareBinding(BindingBuilder.bind(invoiceQueue).to(bookingExchange).with("booking.completed"));
        admin.declareBinding(BindingBuilder.bind(compensationQueue).to(bookingExchange).with("booking.cancelled"));
        admin.declareBinding(BindingBuilder.bind(feedbackQueue).to(paymentExchange).with("payment.failed"));
        admin.declareBinding(BindingBuilder.bind(initiatedQueue).to(paymentExchange).with("payment.initiated"));

        Booking booking = new Booking();
        booking.setId(77L);
        booking.setUserId(7L);
        booking.setProviderId(9L);
        booking.setStatus(Booking.Status.IN_PROGRESS);
        booking.setTotalPrice(new BigDecimal("150.00"));
        booking.setAppointmentDate(LocalDate.of(2026, 5, 17));
        booking.setStartTime(LocalTime.of(10, 0));

        BookingRepository bookingRepository = mock(BookingRepository.class);
        when(bookingRepository.findById(77L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AtomicReference<Booking.Status> bookingStatus = new AtomicReference<>(Booking.Status.IN_PROGRESS);
        when(bookingRepository.updateStatusToPaymentFailed(77L)).thenAnswer(invocation -> {
            bookingStatus.set(Booking.Status.PAYMENT_FAILED);
            return 1;
        });

        UserServiceClient userClient = mock(UserServiceClient.class);
        ProviderServiceClient providerClient = mock(ProviderServiceClient.class);
        CalendarServiceClient calendarClient = mock(CalendarServiceClient.class);
        when(userClient.getUser(7L)).thenReturn(new UserDTO(7L, "User", "u@test.com", "USER", "ACTIVE", null, Map.of(), null));
        when(providerClient.getProvider(9L)).thenReturn(new ProviderDTO(9L, 7L, "Provider", "Shoes", "BUSY", 5.0, 1, BigDecimal.TEN, Map.of()));
        when(calendarClient.getSlotForBooking(9L, "2026-05-17", "10:00")).thenReturn(
                new TimeSlotDTO(3L, 9L, LocalDate.of(2026, 5, 17), LocalTime.of(10, 0), LocalTime.of(11, 0), false, Map.of()));

        BookingEventPublisher bookingPublisher = new BookingEventPublisher(template) {
            @Override
            public void publishBookingCompleted(Long bookingId, Long userId, Long providerId, Double totalPrice) {
                template.convertAndSend("saga.booking.events", "booking.completed",
                        new BookingCompletedEvent(bookingId, userId, providerId, totalPrice));
            }

            @Override
            public void publishBookingCancelled(Long bookingId, Long userId, Long providerId, String reason) {
                template.convertAndSend("saga.booking.events", "booking.cancelled",
                        new BookingCancelledEvent(bookingId, userId, providerId, reason));
            }
        };

        SimpleMessageListenerContainer bookingContainer = listenerContainer(connectionFactory, converter, feedbackQueue.getName(),
                new BookingPaymentEventListener(bookingRepository, bookingPublisher),
                "handlePaymentFailed");
        bookingContainer.start();

        try {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                    "admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            BookingService service = new BookingService(
                    bookingRepository,
                    mock(BookingItemRepository.class),
                    mock(MongoEventLogger.class),
                    mock(CacheInvalidator.class),
                    mock(CacheManager.class),
                    mock(Neo4jClient.class),
                    bookingPublisher,
                    providerClient,
                    mock(InvoiceServiceClient.class),
                    userClient,
                    calendarClient);

            service.completeBooking(77L);

            BookingCompletedEvent completed = (BookingCompletedEvent) template.receiveAndConvert(invoiceQueue.getName(), 10000);
            assertThat(completed).isNotNull();
            assertThat(completed.bookingId()).isEqualTo(77L);

            template.convertAndSend("saga.payment.events", "payment.failed",
                    new PaymentFailedEvent(800L, 77L, "simulated failure"));

            BookingCancelledEvent compensation = (BookingCancelledEvent) template.receiveAndConvert(compensationQueue.getName(), 10000);
            assertThat(compensation).isNotNull();
            assertThat(compensation.bookingId()).isEqualTo(77L);
            assertThat(compensation.reason()).isEqualTo("simulated failure");
            assertThat(bookingStatus.get()).isEqualTo(Booking.Status.PAYMENT_FAILED);
        } finally {
            bookingContainer.stop();
            connectionFactory.destroy();
        }
    }

    private SimpleMessageListenerContainer listenerContainer(CachingConnectionFactory connectionFactory,
                                                             Jackson2JsonMessageConverter converter,
                                                             String queueName,
                                                             Object delegate,
                                                             String methodName) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(delegate, methodName);
        adapter.setMessageConverter(converter);
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(queueName);
        container.setMessageListener(adapter);
        return container;
    }
}
