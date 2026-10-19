package org.una.programmingIII.UTEMP_Project.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.una.programmingIII.UTEMP_Project.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Errors raised by the security filters, before any controller runs, have the same body as the rest of the API,
 * and expected client errors are logged once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ErrorResponseIntegrationTest {

    private static final long MISSING_ID = 999_999;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void requestWithoutTokenGetsAJsonError() throws Exception {
        assertApiError(mockMvc.perform(get("/utemp/users/me")), 401);
    }

    @Test
    void requestWithAnInvalidTokenGetsAJsonError() throws Exception {
        assertApiError(mockMvc.perform(get("/utemp/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")), 401);
    }

    @Test
    void aMissingResourceIsLoggedOnceAsAWarning(CapturedOutput output) throws Exception {
        String admin = new TestData(mockMvc, jdbcTemplate).login(TestData.ADMIN);
        String[] paths = {"/utemp/courses/{id}", "/utemp/assignments/{id}", "/utemp/users/{id}", "/utemp/submissions/{id}",
                "/utemp/grades/{id}", "/utemp/enrollments/{id}", "/utemp/universities/{id}", "/utemp/faculties/{id}",
                "/utemp/departments/{id}", "/utemp/notifications/{id}"};

        for (String path : paths) {
            int before = output.getOut().length();
            mockMvc.perform(get(path, MISSING_ID).header(HttpHeaders.AUTHORIZATION, TestData.bearer(admin)))
                    .andExpect(status().isNotFound());
            assertLoggedOnce(output.getOut().substring(before), "GET " + path);
        }
        for (String path : new String[]{"/utemp/assignments/{id}", "/utemp/courses/{id}"}) {
            int before = output.getOut().length();
            mockMvc.perform(delete(path, MISSING_ID).header(HttpHeaders.AUTHORIZATION, TestData.bearer(admin)))
                    .andExpect(status().isNotFound());
            assertLoggedOnce(output.getOut().substring(before), "DELETE " + path);
        }
    }

    private static void assertLoggedOnce(String log, String request) {
        // A 404 is the client's mistake: one warning from the exception handler, no errors
        assertThat(log.lines().filter(line -> line.contains(" WARN ") || line.contains(" ERROR ")))
                .as(request)
                .singleElement().asString().contains(" WARN ", "GlobalExceptionHandler");
    }

    private static void assertApiError(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.statusCode").value(status))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
