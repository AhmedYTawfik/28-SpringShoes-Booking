package com.team28.booking.invoice.messaging;

import com.team28.booking.contracts.events.BookingCompletedEvent;
import com.team28.booking.contracts.events.PaymentInitiatedEvent;
import com.team28.booking.contracts.feign.BookingServiceClient;
import com.team28.booking.invoice.model.Invoice;
import com.team28.booking.invoice.repository.InvoiceRepository;
import com.team28.booking.invoice.strategy.RefundStrategySelector;
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
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class InvoiceBookingEventListenerRabbitIT {

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"))
            .withEnv("RABBITMQ_NODENAME", "rabbit@localhost")
            .waitingFor(Wait.forHttp("/api/overview")
                    .withBasicCredentials("guest", "guest")
                    .forPort(15672)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    @Test
    void bookingCompletedEventCreatesInvoiceAndPublishesPaymentInitiated() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(
                rabbit.getHost(), rabbit.getAmqpPort());
        connectionFactory.setUsername(rabbit.getAdminUsername());
        connectionFactory.setPassword(rabbit.getAdminPassword());

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);

        Queue sagaQueue = new Queue("payment.saga-listener.test", false);
        Queue initiatedQueue = new Queue("payment.initiated.test", false);
        DirectExchange bookingExchange = new DirectExchange("booking.events.test");
        DirectExchange paymentExchange = new DirectExchange("payment.events.test");
        admin.declareQueue(sagaQueue);
        admin.declareQueue(initiatedQueue);
        admin.declareExchange(bookingExchange);
        admin.declareExchange(paymentExchange);
        admin.declareBinding(BindingBuilder.bind(sagaQueue).to(bookingExchange).with("booking.completed"));
        admin.declareBinding(BindingBuilder.bind(initiatedQueue).to(paymentExchange).with("payment.initiated"));

        AtomicReference<Invoice> localInvoice = new AtomicReference<>();
        InvoiceRepository repository = mock(InvoiceRepository.class);
        when(repository.findByBookingIdForUpdate(77L)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            invoice.setId(501L);
            localInvoice.set(invoice);
            return invoice;
        });

        PaymentEventPublisher publisher = new PaymentEventPublisher(template) {
            @Override
            public void publishPaymentInitiated(Long invoiceId, Long bookingId, Double amount) {
                template.convertAndSend("payment.events.test", "payment.initiated",
                        new PaymentInitiatedEvent(invoiceId, bookingId, amount));
            }
        };

        InvoiceBookingEventListener listener = new InvoiceBookingEventListener(
                repository, publisher, mock(BookingServiceClient.class), mock(RefundStrategySelector.class));
        MessageListenerAdapter adapter = new MessageListenerAdapter(listener, "handleBookingCompleted");
        adapter.setMessageConverter(converter);
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(sagaQueue.getName());
        container.setMessageListener(adapter);
        container.start();

        try {
            template.convertAndSend("booking.events.test", "booking.completed",
                    new BookingCompletedEvent(77L, 7L, 9L, 150.0));

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(localInvoice.get()).isNotNull());
            assertThat(localInvoice.get().getBookingId()).isEqualTo(77L);
            assertThat(localInvoice.get().getAmount()).isEqualByComparingTo("150.0");

            PaymentInitiatedEvent initiated = (PaymentInitiatedEvent) template.receiveAndConvert(
                    initiatedQueue.getName(), 5000);
            assertThat(initiated).isNotNull();
            assertThat(initiated.invoiceId()).isEqualTo(501L);
            assertThat(initiated.bookingId()).isEqualTo(77L);
        } finally {
            container.stop();
            connectionFactory.destroy();
        }
    }
}
