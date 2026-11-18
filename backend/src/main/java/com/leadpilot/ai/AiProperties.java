package com.leadpilot.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("leadpilot.ai")
record AiProperties(
        String apiKey,
        @DefaultValue("gpt-4.1-mini") String model,
        @DefaultValue("https://api.openai.com/v1") String baseUrl,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("20s") Duration requestTimeout
) {}
