package com.team28.booking.calendar.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CalendarEventConfig {
    @Bean
    public TopicExchange calendarEventsExchange() {
        return new TopicExchange("calendar.events");
    }

    @Bean
    public TopicExchange calendarBookingEventsExchange() {
        return new TopicExchange("booking.events");
    }

    @Bean
    public Queue calendarBookingSagaQueue() {
        return QueueBuilder.durable("calendar.booking.saga-listener")
                .withArgument("x-dead-letter-exchange", "calendar.dlx")
                .withArgument("x-dead-letter-routing-key", "calendar.booking.saga-listener.dlq")
                .build();
    }

    @Bean
    public TopicExchange calendarDlx() {
        return new TopicExchange("calendar.dlx");
    }

    @Bean
    public Queue calendarBookingSagaDlq() {
        return QueueBuilder.durable("calendar.booking.saga-listener.dlq").build();
    }

    @Bean
    public Binding calendarDlqBinding() {
        return BindingBuilder.bind(calendarBookingSagaDlq()).to(calendarDlx()).with("calendar.booking.saga-listener.dlq");
    }

    @Bean
    public Binding calendarBookingPlacedBinding() {
        return BindingBuilder.bind(calendarBookingSagaQueue()).to(calendarBookingEventsExchange()).with("booking.placed");
    }

    @Bean
    public Binding calendarBookingCompletedBinding() {
        return BindingBuilder.bind(calendarBookingSagaQueue()).to(calendarBookingEventsExchange()).with("booking.completed");
    }

    @Bean
    public Binding calendarBookingCancelledBinding() {
        return BindingBuilder.bind(calendarBookingSagaQueue()).to(calendarBookingEventsExchange()).with("booking.cancelled");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
