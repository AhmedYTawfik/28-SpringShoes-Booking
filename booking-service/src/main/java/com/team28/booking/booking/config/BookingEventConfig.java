package com.team28.booking.booking.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BookingEventConfig {
    @Bean
    public TopicExchange bookingEventsExchange() {
        return new TopicExchange("booking.events");
    }

    @Bean
    public TopicExchange bookingPaymentEventsExchange() {
        return new TopicExchange("payment.events");
    }

    @Bean
    public Queue bookingSagaFeedbackQueue() {
        return QueueBuilder.durable("booking.saga-feedback")
                .withArgument("x-dead-letter-exchange", "booking.dlx")
                .withArgument("x-dead-letter-routing-key", "booking.saga-feedback.dlq")
                .build();
    }

    @Bean
    public TopicExchange bookingDlx() {
        return new TopicExchange("booking.dlx");
    }

    @Bean
    public Queue bookingSagaFeedbackDlq() {
        return QueueBuilder.durable("booking.saga-feedback.dlq").build();
    }

    @Bean
    public Binding bookingDlqBinding() {
        return BindingBuilder.bind(bookingSagaFeedbackDlq()).to(bookingDlx()).with("booking.saga-feedback.dlq");
    }

    @Bean
    public Binding paymentInitiatedBinding() {
        return BindingBuilder.bind(bookingSagaFeedbackQueue()).to(bookingPaymentEventsExchange()).with("payment.initiated");
    }

    @Bean
    public Binding paymentCompletedBinding() {
        return BindingBuilder.bind(bookingSagaFeedbackQueue()).to(bookingPaymentEventsExchange()).with("payment.completed");
    }

    @Bean
    public Binding paymentFailedBinding() {
        return BindingBuilder.bind(bookingSagaFeedbackQueue()).to(bookingPaymentEventsExchange()).with("payment.failed");
    }

    @Bean
    public Binding paymentRefundedBinding() {
        return BindingBuilder.bind(bookingSagaFeedbackQueue()).to(bookingPaymentEventsExchange()).with("payment.refunded");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
