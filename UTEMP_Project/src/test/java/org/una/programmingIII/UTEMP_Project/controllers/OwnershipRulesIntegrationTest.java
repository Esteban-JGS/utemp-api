package org.una.programmingIII.UTEMP_Project.controllers;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MvcResult;
import org.una.programmingIII.UTEMP_Project.TestcontainersConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Per-user endpoints (notifications, enrollments) must only expose the caller's own data,
 * and management endpoints must not be reachable by students.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OwnershipRulesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String studentOneToken;
    private Long studentOneId;
    private Long studentTwoId;

    @BeforeEach
    void setUp() throws Exception {
        studentOneToken = login("300000001");
        studentOneId = userId("300000001");
        studentTwoId = userId("300000002");
    }

    @Test
    void studentCanOnlyReadTheirOwnNotifications() throws Exception {
        mockMvc.perform(get("/utemp/notifications/user/{id}", studentTwoId).header(HttpHeaders.AUTHORIZATION, bearer(studentOneToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/utemp/notifications/user/{id}", studentOneId).header(HttpHeaders.AUTHORIZATION, bearer(studentOneToken)))
                .andExpect(status().isOk());
    }

    @Test
    void studentCanOnlyReadTheirOwnEnrollments() throws Exception {
        mockMvc.perform(get("/utemp/enrollments/student/{id}", studentTwoId).header(HttpHeaders.AUTHORIZATION, bearer(studentOneToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/utemp/enrollments/student/{id}", studentOneId).header(HttpHeaders.AUTHORIZATION, bearer(studentOneToken)))
                .andExpect(status().isOk());
    }

    @Test
    void studentCannotManageNotifications() throws Exception {
        mockMvc.perform(get("/utemp/notifications").header(HttpHeaders.AUTHORIZATION, bearer(studentOneToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/utemp/notifications/send")
                        .param("userId", String.valueOf(studentTwoId))
                        .param("message", "spam")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentOneToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordHashIsNeverReturned() throws Exception {
        mockMvc.perform(get("/utemp/users/me").header(HttpHeaders.AUTHORIZATION, bearer(studentOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    private Long userId(String identificationNumber) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE identification_number = ?", Long.class, identificationNumber);
    }

    private String login(String identificationNumber) throws Exception {
        MvcResult result = mockMvc.perform(post("/utemp/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identificationNumber": "%s", "password": "Demo1234!"}
                                """.formatted(identificationNumber)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
