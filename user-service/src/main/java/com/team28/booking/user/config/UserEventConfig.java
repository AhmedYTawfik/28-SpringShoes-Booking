package com.team28.booking.user.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserEventConfig {
    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange("user.events");
    }

    @Bean
    public TopicExchange userBookingEventsExchange() {
        return new TopicExchange("booking.events");
    }

    @Bean
    public Queue userBookingSagaQueue() {
        return QueueBuilder.durable("user.booking.saga-listener")
                .withArgument("x-dead-letter-exchange", "user.dlx")
                .withArgument("x-dead-letter-routing-key", "user.booking.saga-listener.dlq")
                .build();
    }

    @Bean
    public TopicExchange userDlx() {
        return new TopicExchange("user.dlx");
    }

    @Bean
    public Queue userBookingSagaDlq() {
        return QueueBuilder.durable("user.booking.saga-listener.dlq").build();
    }

    @Bean
    public Binding userDlqBinding() {
        return BindingBuilder.bind(userBookingSagaDlq()).to(userDlx()).with("user.booking.saga-listener.dlq");
    }

    @Bean
    public Binding userBookingCompletedBinding() {
        return BindingBuilder.bind(userBookingSagaQueue()).to(userBookingEventsExchange()).with("booking.completed");
    }

    @Bean
    public Binding userBookingCancelledBinding() {
        return BindingBuilder.bind(userBookingSagaQueue()).to(userBookingEventsExchange()).with("booking.cancelled");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
