package com.team28.booking.calendar;

import com.team28.booking.calendar.cassandra.CalendarAvailabilityEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=" +
    "org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration," +
    "org.springframework.boot.autoconfigure.data.cassandra.CassandraDataAutoConfiguration," +
    "org.springframework.boot.autoconfigure.data.cassandra.CassandraRepositoriesAutoConfiguration"
})
class CalendarServiceApplicationTests {

    @MockitoBean
    CalendarAvailabilityEventRepository cassandraRepository;

    @Test
    void contextLoads() {
    }
}
