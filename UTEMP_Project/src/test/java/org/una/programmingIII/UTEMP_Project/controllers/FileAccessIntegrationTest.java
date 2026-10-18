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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end check of file ownership: real login (JWT), real security filter chain and a real PostgreSQL database.
 * Uses the demo users from db/demo: two students enrolled in a course taught by the demo teacher.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FileAccessIntegrationTest {

    private static final String STUDENT_ONE = "300000001";
    private static final String STUDENT_TWO = "300000002";
    private static final String TEACHER = "200000001";
    private static final String CONTENT = "hello world";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long fileId;

    @BeforeEach
    void studentOneUploadsAFile() throws Exception {
        String token = login(STUDENT_ONE);
        Long studentId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE identification_number = ?", Long.class, STUDENT_ONE);
        Long submissionId = jdbcTemplate.queryForObject(
                "SELECT id FROM submissions WHERE student_id = ?", Long.class, studentId);

        MvcResult created = mockMvc.perform(post("/utemp/files")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName": "../../report.txt", "fileType": "text/plain", "fileSize": %d,
                                 "student": {"id": %d}, "submission": {"id": %d}}
                                """.formatted(CONTENT.length(), studentId, submissionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("report.txt"))
                .andExpect(jsonPath("$.storagePath").doesNotExist())
                .andReturn();
        fileId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(post("/utemp/files/receive-chunk")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chunkJson(fileId, CONTENT)))
                .andExpect(status().isOk());
    }

    @Test
    void anotherStudentGetsForbiddenWhenDownloadingSomeoneElsesFile() throws Exception {
        mockMvc.perform(get("/utemp/files/download/{id}", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(STUDENT_TWO))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        mockMvc.perform(get("/utemp/files/{id}", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(login(STUDENT_TWO))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherStudentCannotOverwriteOrDeleteSomeoneElsesFile() throws Exception {
        String token = login(STUDENT_TWO);

        mockMvc.perform(post("/utemp/files/receive-chunk")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chunkJson(fileId, "HACKED!!!!!")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/utemp/files/{id}", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanDownloadTheirFile() throws Exception {
        assertDownloads(login(STUDENT_ONE));
    }

    @Test
    void courseTeacherCanDownloadTheFile() throws Exception {
        assertDownloads(login(TEACHER));
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/utemp/files/download/{id}", fileId))
                .andExpect(status().isUnauthorized());
    }

    private void assertDownloads(String token) throws Exception {
        // The file is written on the request thread, so the response is complete without an async dispatch
        mockMvc.perform(get("/utemp/files/download/{id}", fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(request().asyncNotStarted())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("report.txt")))
                .andExpect(content().bytes(CONTENT.getBytes(StandardCharsets.UTF_8)));
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

    private static String chunkJson(Long id, String content) {
        String base64 = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        return """
                {"id": %d, "fileChunk": "%s", "chunkIndex": 0, "totalChunks": 1}
                """.formatted(id, base64);
    }
}
