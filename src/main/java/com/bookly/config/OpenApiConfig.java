package com.bookly.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger configuration with global error response schemas.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Bookly API")
                        .version("1.0.0")
                        .description("REST API documentation for the Bookly multi-tenant appointment booking platform.")
                        .contact(new Contact()
                                .name("Bookly Team")
                                .email("support@bookly.app")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"))
                        .addResponses("BadRequest", errorResponse("Validation failed or invalid request"))
                        .addResponses("Unauthorized", errorResponse("Missing or invalid authentication token"))
                        .addResponses("Forbidden", errorResponse("Insufficient permissions for this resource"))
                        .addResponses("NotFound", errorResponse("Requested resource not found"))
                        .addResponses("Conflict", errorResponse("Resource already exists (duplicate email/subdomain)"))
                        .addResponses("TooManyRequests", errorResponse("Rate limit exceeded — see Retry-After header"))
                        .addResponses("InternalError", errorResponse("Unexpected server error"))
                );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ApiResponse errorResponse(String description) {
        Schema schema = new Schema()
                .addProperty("success", new Schema().type("boolean").example(false))
                .addProperty("message", new Schema().type("string").example(description))
                .addProperty("data", new Schema().type("object").nullable(true));

        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(schema)));
    }
}
