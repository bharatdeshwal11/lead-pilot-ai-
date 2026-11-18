package com.leadpilot.config;

import com.leadpilot.auth.UserPrincipal;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {
    private static final Set<String> PUBLIC_ROUTES = Set.of("/api/auth/login", "/api/auth/csrf", "/health");

    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(UserPrincipal.class);
    }

    @Bean
    OpenAPI leadPilotOpenApi() {
        return new OpenAPI()
                .info(new Info().title("LeadPilot AI API").version("1.0")
                        .description("Use GET /api/auth/csrf before writing. Send its token in X-CSRF-TOKEN. "
                                + "After signing in, get a fresh token and keep the session cookie."));
    }

    @Bean
    OpenApiCustomizer apiSecurity() {
        return api -> {
            api.schemaRequirement("sessionCookie", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE).name("LEADPILOT_SESSION"));
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                if (!PUBLIC_ROUTES.contains(path)) {
                    operation.addSecurityItem(new SecurityRequirement().addList("sessionCookie"));
                }
                if (Set.of(PathItem.HttpMethod.POST, PathItem.HttpMethod.PATCH, PathItem.HttpMethod.DELETE).contains(method)) {
                    operation.addParametersItem(new Parameter().name("X-CSRF-TOKEN").in("header")
                            .description("Token returned by GET /api/auth/csrf for this session")
                            .required(true).schema(new io.swagger.v3.oas.models.media.StringSchema()));
                }
            }));
        };
    }
}
