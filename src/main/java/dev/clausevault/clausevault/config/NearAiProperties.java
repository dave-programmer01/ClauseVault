package dev.clausevault.clausevault.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "nearai")
public record NearAiProperties(
    @NotBlank String apiKey,
    @NotBlank String baseUrl,
    @NotBlank String model) {}
