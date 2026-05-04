package com.team28.booking.provider.config;

import org.springframework.data.elasticsearch.client.elc.ElasticsearchClients;
import java.util.Arrays;

public class ClassDumper {
    public static void main(String[] args) {
        System.out.println("Inner classes of ElasticsearchClients:");
        for (Class<?> c : ElasticsearchClients.class.getDeclaredClasses()) {
            System.out.println(c.getName());
        }
    }
}
