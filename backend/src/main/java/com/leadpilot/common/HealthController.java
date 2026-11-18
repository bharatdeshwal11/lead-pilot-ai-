package com.leadpilot.common;

import java.util.Map;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final JdbcTemplate jdbc;

    private final ApplicationAvailability availability;

    public HealthController(JdbcTemplate jdbc, ApplicationAvailability availability) {
        this.jdbc = jdbc;
        this.availability = availability;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "STARTING"));
        }
        try {
            jdbc.queryForObject("select 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (DataAccessException unavailable) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
        }
    }
}
