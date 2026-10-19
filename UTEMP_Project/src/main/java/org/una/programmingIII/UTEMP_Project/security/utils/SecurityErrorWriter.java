package org.una.programmingIII.UTEMP_Project.security.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.una.programmingIII.UTEMP_Project.controllers.responses.ApiResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Security errors happen in filters, before GlobalExceptionHandler can see them,
 * so they are written here with the same body as every other API error.
 */
public final class SecurityErrorWriter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private SecurityErrorWriter() {
    }

    public static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new ApiResponse<Void>(status, message));
    }
}
