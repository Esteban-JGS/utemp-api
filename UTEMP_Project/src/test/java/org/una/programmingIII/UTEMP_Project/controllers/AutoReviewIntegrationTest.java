package org.una.programmingIII.UTEMP_Project.controllers;

import com.jayway.jsonpath.JsonPath;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uploading the last chunk of a submission's file triggers the simulated AI review in the background:
 * a preliminary grade is stored and the student gets a notification.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AutoReviewIntegrationTest {

    private static final String STUDENT = "300000002";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void uploadingTheFileStoresOnePreliminaryAiGrade() throws Exception {
        String token = login(STUDENT);
        Long studentId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE identification_number = ?", Long.class, STUDENT);
        Long submissionId = jdbcTemplate.queryForObject(
                "SELECT id FROM submissions WHERE student_id = ?", Long.class, studentId);

        upload(token, studentId, submissionId, "my final project");

        // The review runs on the async executor after the upload transaction commits
        await().atMost(TIMEOUT).until(() -> reviewNotifications(studentId) == 1);
        List<Map<String, Object>> grades = grades(submissionId);
        assertThat(grades).hasSize(1);
        Map<String, Object> grade = grades.getFirst();
        assertThat(grade.get("reviewed_by_ai")).isEqualTo(true);
        assertThat(grade.get("state")).isEqualTo("PENDING_REVIEW");
        assertThat(((Number) grade.get("grade")).doubleValue()).isBetween(0.0, 10.0);
        assertThat((String) grade.get("comments")).isNotBlank().isNotEqualTo("Comment not found.");

        // Uploading a new version reviews it again but keeps a single grade for the submission
        upload(token, studentId, submissionId, "my final project, v2");

        await().atMost(TIMEOUT).until(() -> reviewNotifications(studentId) == 2);
        assertThat(grades(submissionId)).hasSize(1);
    }

    private void upload(String token, Long studentId, Long submissionId, String content) throws Exception {
        MvcResult created = mockMvc.perform(post("/utemp/files")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName": "project.txt", "fileType": "text/plain", "fileSize": %d,
                                 "student": {"id": %d}, "submission": {"id": %d}}
                                """.formatted(content.length(), studentId, submissionId)))
                .andExpect(status().isCreated())
                .andReturn();
        long fileId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(post("/utemp/files/receive-chunk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": %d, "fileChunk": "%s", "chunkIndex": 0, "totalChunks": 1}
                                """.formatted(fileId, Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)))))
                .andExpect(status().isOk());
    }

    private List<Map<String, Object>> grades(Long submissionId) {
        return jdbcTemplate.queryForList(
                "SELECT grade, comments, state, reviewed_by_ai FROM grades WHERE submission_id = ?", submissionId);
    }

    private int reviewNotifications(Long studentId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND message LIKE '%preliminary grade%'",
                Integer.class, studentId);
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
}
