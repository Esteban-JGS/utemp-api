package org.una.programmingIII.UTEMP_Project.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.una.programmingIII.UTEMP_Project.TestcontainersConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.una.programmingIII.UTEMP_Project.controllers.TestData.bearer;

/**
 * Suspending an account cuts its access right away, including the tokens it already has.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AccountStateIntegrationTest {

    private static final String STUDENT = "300000094";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void suspendedUserCannotUseOrRefreshExistingTokens() throws Exception {
        TestData data = new TestData(mockMvc, jdbcTemplate);
        Long userId = data.user(STUDENT, "STUDENT", TestData.STUDENT_PERMISSIONS);
        String accessToken = data.login(STUDENT);
        String refreshToken = data.refreshToken(STUDENT);

        jdbcTemplate.update("UPDATE users SET state = 'SUSPENDED' WHERE id = ?", userId);

        mockMvc.perform(get("/utemp/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/utemp/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }
}
