package com.team28.booking.booking.messaging;

import com.team28.booking.booking.model.Booking;
import com.team28.booking.booking.repository.BookingRepository;
import com.team28.booking.contracts.events.BookingCancelledEvent;
import com.team28.booking.contracts.events.PaymentFailedEvent;
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
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class BookingPaymentEventListenerRabbitIT {

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"));

    @Test
    void paymentFailureEventMutatesBookingAndPublishesCompensation() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(
                rabbit.getHost(), rabbit.getAmqpPort());
        connectionFactory.setUsername(rabbit.getAdminUsername());
        connectionFactory.setPassword(rabbit.getAdminPassword());

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);

        Queue feedbackQueue = new Queue("booking.saga-feedback.test", false);
        Queue compensationQueue = new Queue("booking.compensation.test", false);
        DirectExchange paymentExchange = new DirectExchange("payment.events.test");
        DirectExchange bookingExchange = new DirectExchange("booking.events.test");
        admin.declareQueue(feedbackQueue);
        admin.declareQueue(compensationQueue);
        admin.declareExchange(paymentExchange);
        admin.declareExchange(bookingExchange);
        admin.declareBinding(BindingBuilder.bind(feedbackQueue).to(paymentExchange).with("payment.failed"));
        admin.declareBinding(BindingBuilder.bind(compensationQueue).to(bookingExchange).with("booking.cancelled"));

        AtomicReference<Booking.Status> localStatus = new AtomicReference<>(Booking.Status.PAYMENT_PENDING);
        Booking booking = new Booking();
        booking.setId(55L);
        booking.setUserId(7L);
        booking.setProviderId(9L);

        BookingRepository repository = mock(BookingRepository.class);
        when(repository.updateStatusToPaymentFailed(55L)).thenAnswer(invocation -> {
            localStatus.set(Booking.Status.PAYMENT_FAILED);
            return 1;
        });
        when(repository.findById(55L)).thenReturn(Optional.of(booking));

        BookingEventPublisher publisher = new BookingEventPublisher(template) {
            @Override
            public void publishBookingCancelled(Long bookingId, Long userId, Long providerId, String reason) {
                template.convertAndSend("booking.events.test", "booking.cancelled",
                        new BookingCancelledEvent(bookingId, userId, providerId, reason));
            }
        };

        MessageListenerAdapter adapter = new MessageListenerAdapter(
                new BookingPaymentEventListener(repository, publisher), "handlePaymentFailed");
        adapter.setMessageConverter(converter);
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(feedbackQueue.getName());
        container.setMessageListener(adapter);
        container.start();

        try {
            template.convertAndSend("payment.events.test", "payment.failed",
                    new PaymentFailedEvent(100L, 55L, "card declined"));

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(localStatus.get()).isEqualTo(Booking.Status.PAYMENT_FAILED));
            BookingCancelledEvent compensation = (BookingCancelledEvent) template.receiveAndConvert(
                    compensationQueue.getName(), 5000);
            assertThat(compensation).isNotNull();
            assertThat(compensation.bookingId()).isEqualTo(55L);
            assertThat(compensation.reason()).isEqualTo("card declined");
        } finally {
            container.stop();
            connectionFactory.destroy();
        }
    }
}
