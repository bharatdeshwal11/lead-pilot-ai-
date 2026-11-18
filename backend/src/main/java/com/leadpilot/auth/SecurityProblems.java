package com.leadpilot.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadpilot.common.ApiErrors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

final class SecurityProblems {
    private final ObjectMapper objectMapper;

    SecurityProblems(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getOutputStream(), ApiErrors.problem(status, detail, request));
    }
}
