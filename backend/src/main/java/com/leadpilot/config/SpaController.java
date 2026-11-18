package com.leadpilot.config;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Hidden
@Controller
@ConditionalOnResource(resources = "classpath:static/index.html")
class SpaController {
    @GetMapping({"/login", "/leads", "/leads/new", "/leads/{id:[0-9]+}", "/follow-ups", "/users"})
    String applicationRoute() {
        return "forward:/index.html";
    }
}
