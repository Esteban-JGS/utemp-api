package org.una.programmingIII.UTEMP_Project.controllers;

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
import org.springframework.test.web.servlet.ResultActions;
import org.una.programmingIII.UTEMP_Project.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.una.programmingIII.UTEMP_Project.controllers.TestData.bearer;

/**
 * Updating a user only changes the password when a new one is sent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UserUpdateIntegrationTest {

    private static final String STUDENT = "300000072";
    private static final String NEW_USER = "300000073";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestData data;
    private Long studentId;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        data = new TestData(mockMvc, jdbcTemplate);
        studentId = data.user(STUDENT, "STUDENT", TestData.STUDENT_PERMISSIONS);
        // Back to the demo password, in case a previous test changed it
        jdbcTemplate.update("UPDATE users SET password = (SELECT password FROM users WHERE identification_number = ?) WHERE id = ?",
                TestData.ADMIN, studentId);
        admin = data.login(TestData.ADMIN);
    }

    @Test
    void updatingAUserWithoutAPasswordKeepsTheCurrentOne() throws Exception {
        updateStudent("\"state\": \"SUSPENDED\"").andExpect(status().isOk());
        updateStudent("\"state\": \"ACTIVE\"").andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM users WHERE id = ?", String.class, studentId)).isEqualTo("Renamed");
        login(STUDENT, "Demo1234!").andExpect(status().isOk());
    }

    @Test
    void updatingAUserWithAPasswordReplacesIt() throws Exception {
        updateStudent("\"state\": \"ACTIVE\", \"password\": \"Another1234!\"").andExpect(status().isOk());

        login(STUDENT, "Another1234!").andExpect(status().isOk());
        login(STUDENT, "Demo1234!").andExpect(status().isUnauthorized());
    }

    @Test
    void creatingAUserStillRequiresAPassword() throws Exception {
        mockMvc.perform(post("/utemp/users").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "No Password", "email": "%1$s@utemp.example", "identificationNumber": "%1$s",
                                 "role": "STUDENT", "state": "ACTIVE"}
                                """.formatted(NEW_USER)))
                .andExpect(status().isBadRequest());

        assertThat(data.count("SELECT COUNT(*) FROM users WHERE identification_number = ?", NEW_USER)).isZero();
    }

    private ResultActions updateStudent(String fields) throws Exception {
        return mockMvc.perform(put("/utemp/users/{id}", studentId).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Renamed", "email": "%1$s@utemp.example", "identificationNumber": "%1$s",
                         "role": "STUDENT", %2$s}
                        """.formatted(STUDENT, fields)));
    }

    private ResultActions login(String identificationNumber, String password) throws Exception {
        return mockMvc.perform(post("/utemp/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"identificationNumber": "%s", "password": "%s"}
                        """.formatted(identificationNumber, password)));
    }
}
