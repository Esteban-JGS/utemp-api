package org.una.programmingIII.UTEMP_Project.configs.security.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.una.programmingIII.UTEMP_Project.security.utils.CustomAccessDeniedHandler;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class CustomAccessDeniedHandlerTest {

    private CustomAccessDeniedHandler accessDeniedHandler;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        accessDeniedHandler = new CustomAccessDeniedHandler();
        response = new MockHttpServletResponse();
    }

    @Test
    void handle_ShouldWriteForbiddenApiResponse() throws IOException {
        accessDeniedHandler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("Test access denied error"));

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType().split(";")[0]);
        // The internal reason is not sent to the client
        assertEquals("{\"data\":null,\"statusCode\":403,\"message\":\"Access denied\"}", response.getContentAsString());
    }
}
