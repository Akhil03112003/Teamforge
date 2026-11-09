package com.teamforge.platform.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public final class ApiErrors {
    private ApiErrors() {}
    public record ErrorBody(String code, String message, String requestId, Map<String, String> fieldErrors) {}
    public static ErrorBody body(String code, String message, Map<String, String> fields) {
        return new ErrorBody(code, message, UUID.randomUUID().toString(), fields);
    }
    public static void write(HttpServletResponse response, ObjectMapper mapper,
                             int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), body(code, message, Map.of()));
    }
}
