package com.leadpilot;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LeadPilotApplication {
    public static void main(String[] args) {
        SpringApplication.run(LeadPilotApplication.class, args);
    }

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}
