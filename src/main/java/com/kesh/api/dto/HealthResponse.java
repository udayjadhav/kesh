package com.kesh.api.dto;

public record HealthResponse(
        String status,
        String provider,
        String model,
        String baseUrl
) {
}
