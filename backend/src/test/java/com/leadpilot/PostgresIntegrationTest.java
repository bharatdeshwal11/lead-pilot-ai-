package com.leadpilot;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = {
        "leadpilot.demo.enabled=false",
        "leadpilot.security.login-limit=0",
        "leadpilot.security.ai-limit=0",
        "server.servlet.session.cookie.secure=false"
})
public abstract class PostgresIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("TEST_DB_URL");
        if (url != null && !url.isBlank()) {
            properties.add("spring.datasource.url", () -> url);
            properties.add("spring.datasource.username", () -> System.getenv("TEST_DB_USERNAME"));
            properties.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
            return;
        }
        var database = TestDatabase.INSTANCE;
        properties.add("spring.datasource.url", database::getJdbcUrl);
        properties.add("spring.datasource.username", database::getUsername);
        properties.add("spring.datasource.password", database::getPassword);
    }

    private static class TestDatabase {
        private static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:17-alpine");

        static {
            INSTANCE.start();
        }
    }
}
