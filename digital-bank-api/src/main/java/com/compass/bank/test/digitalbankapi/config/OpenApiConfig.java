package com.compass.bank.test.digitalbankapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI digitalBankOpenAPI() {
		return new OpenAPI().info(new Info().title("Digital Bank API").version("0.0.1"));
	}
}
