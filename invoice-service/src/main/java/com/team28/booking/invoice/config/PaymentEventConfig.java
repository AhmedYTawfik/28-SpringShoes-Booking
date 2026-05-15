package com.team28.booking.invoice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentEventConfig {
    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange("payment.events");
    }

    @Bean
    public TopicExchange paymentBookingEventsExchange() {
        return new TopicExchange("booking.events");
    }

    @Bean
    public Queue paymentSagaQueue() {
        return QueueBuilder.durable("payment.saga-listener")
                .withArgument("x-dead-letter-exchange", "payment.dlx")
                .withArgument("x-dead-letter-routing-key", "payment.saga-listener.dlq")
                .build();
    }

    @Bean
    public TopicExchange paymentDlx() {
        return new TopicExchange("payment.dlx");
    }

    @Bean
    public Queue paymentSagaDlq() {
        return QueueBuilder.durable("payment.saga-listener.dlq").build();
    }

    @Bean
    public Binding paymentDlqBinding() {
        return BindingBuilder.bind(paymentSagaDlq()).to(paymentDlx()).with("payment.saga-listener.dlq");
    }

    @Bean
    public Binding invoiceBookingCompletedBinding() {
        return BindingBuilder.bind(paymentSagaQueue()).to(paymentBookingEventsExchange()).with("booking.completed");
    }

    @Bean
    public Binding invoiceBookingCancelledBinding() {
        return BindingBuilder.bind(paymentSagaQueue()).to(paymentBookingEventsExchange()).with("booking.cancelled");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
