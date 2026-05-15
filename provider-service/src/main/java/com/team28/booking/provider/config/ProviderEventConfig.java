package com.team28.booking.provider.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProviderEventConfig {
    @Bean
    public TopicExchange providerEventsExchange() {
        return new TopicExchange("provider.events");
    }

    @Bean
    public TopicExchange providerBookingEventsExchange() {
        return new TopicExchange("booking.events");
    }

    @Bean
    public Queue providerBookingSagaQueue() {
        return QueueBuilder.durable("provider.booking.saga-listener")
                .withArgument("x-dead-letter-exchange", "provider.dlx")
                .withArgument("x-dead-letter-routing-key", "provider.booking.saga-listener.dlq")
                .build();
    }

    @Bean
    public TopicExchange providerDlx() {
        return new TopicExchange("provider.dlx");
    }

    @Bean
    public Queue providerBookingSagaDlq() {
        return QueueBuilder.durable("provider.booking.saga-listener.dlq").build();
    }

    @Bean
    public Binding providerDlqBinding() {
        return BindingBuilder.bind(providerBookingSagaDlq()).to(providerDlx()).with("provider.booking.saga-listener.dlq");
    }

    @Bean
    public Binding providerBookingPlacedBinding() {
        return BindingBuilder.bind(providerBookingSagaQueue()).to(providerBookingEventsExchange()).with("booking.placed");
    }

    @Bean
    public Binding providerBookingCompletedBinding() {
        return BindingBuilder.bind(providerBookingSagaQueue()).to(providerBookingEventsExchange()).with("booking.completed");
    }

    @Bean
    public Binding providerBookingCancelledBinding() {
        return BindingBuilder.bind(providerBookingSagaQueue()).to(providerBookingEventsExchange()).with("booking.cancelled");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
