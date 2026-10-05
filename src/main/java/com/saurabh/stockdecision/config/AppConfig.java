package com.saurabh.stockdecision.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

import static com.saurabh.stockdecision.security.ApiKeyFilter.API_KEY_HEADER;

@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public OpenAPI openAPI() {
        String scheme = "apiKey";
        return new OpenAPI()
                .info(new Info()
                        .title("Stock Decision Engine")
                        .version("1.0.0")
                        .description("Returns BUY / SELL / HOLD from average purchase price and current price, "
                                + "optionally scored by Jev (TypeSafe AI) when TYPESAFE_API_KEY is set."))
                .components(new Components().addSecuritySchemes(scheme, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(API_KEY_HEADER)))
                .addSecurityItem(new SecurityRequirement().addList(scheme));
    }
}
