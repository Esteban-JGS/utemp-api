package org.una.programmingIII.UTEMP_Project.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.una.programmingIII.UTEMP_Project.TestcontainersConfiguration;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Errors raised by the security filters, before any controller runs, have the same body as the rest of the API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ErrorResponseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestWithoutTokenGetsAJsonError() throws Exception {
        assertApiError(mockMvc.perform(get("/utemp/users/me")), 401);
    }

    @Test
    void requestWithAnInvalidTokenGetsAJsonError() throws Exception {
        assertApiError(mockMvc.perform(get("/utemp/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")), 401);
    }

    private static void assertApiError(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.statusCode").value(status))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
