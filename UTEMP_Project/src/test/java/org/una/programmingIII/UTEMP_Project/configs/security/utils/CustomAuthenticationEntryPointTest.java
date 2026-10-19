package org.una.programmingIII.UTEMP_Project.configs.security.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.una.programmingIII.UTEMP_Project.security.utils.CustomAuthenticationEntryPoint;

import jakarta.servlet.ServletException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class CustomAuthenticationEntryPointTest {

    private CustomAuthenticationEntryPoint entryPoint;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        entryPoint = new CustomAuthenticationEntryPoint();
        response = new MockHttpServletResponse();
    }

    @Test
    void commence_ShouldWriteUnauthorizedApiResponse() throws IOException, ServletException {
        entryPoint.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("Test authentication error"));

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType().split(";")[0]);
        assertEquals("{\"data\":null,\"statusCode\":401,\"message\":\"Authentication required\"}", response.getContentAsString());
    }
}
