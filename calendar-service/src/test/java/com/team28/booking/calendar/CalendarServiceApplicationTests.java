package com.team28.booking.calendar;

import com.team28.booking.calendar.cassandra.CalendarAvailabilityEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=" +
    "org.springframework.boot.cassandra.autoconfigure.CassandraAutoConfiguration," +
    "org.springframework.boot.cassandra.autoconfigure.health.CassandraHealthContributorAutoConfiguration," +
    "org.springframework.boot.cassandra.autoconfigure.health.CassandraReactiveHealthContributorAutoConfiguration," +
    "org.springframework.boot.data.cassandra.autoconfigure.DataCassandraAutoConfiguration," +
    "org.springframework.boot.data.cassandra.autoconfigure.DataCassandraRepositoriesAutoConfiguration," +
    "org.springframework.boot.data.cassandra.autoconfigure.DataCassandraReactiveAutoConfiguration," +
    "org.springframework.boot.data.cassandra.autoconfigure.DataCassandraReactiveRepositoriesAutoConfiguration"
})
class CalendarServiceApplicationTests {

    @MockitoBean
    CalendarAvailabilityEventRepository cassandraRepository;

    @Test
    void contextLoads() {
    }
}
