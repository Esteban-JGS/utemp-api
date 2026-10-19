package org.una.programmingIII.UTEMP_Project.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.una.programmingIII.UTEMP_Project.TestcontainersConfiguration;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.una.programmingIII.UTEMP_Project.controllers.TestData.bearer;

/**
 * Everyday flows of a course: listing submissions, enrolling students and changing the teacher.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CourseworkIntegrationTest {

    private static final String TEACHER = "200000081";
    private static final String STUDENT_WITHOUT_FILE = "300000081";
    private static final String STUDENT_WITH_FILES = "300000082";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestData data;
    private Long courseId;
    private Long assignmentId;

    @BeforeEach
    void setUp() {
        data = new TestData(mockMvc, jdbcTemplate);
        Long teacherId = data.user(TEACHER, "TEACHER", TestData.TEACHER_PERMISSIONS);
        courseId = data.course("Test Coursework", teacherId);
        assignmentId = data.assignment(courseId, "Essay");
    }

    @Test
    void teacherListsSubmissionsWithAndWithoutFiles() throws Exception {
        Long withoutFile = data.submission(assignmentId, data.user(STUDENT_WITHOUT_FILE, "STUDENT", TestData.STUDENT_PERMISSIONS));
        Long studentWithFiles = data.user(STUDENT_WITH_FILES, "STUDENT", TestData.STUDENT_PERMISSIONS);
        Long withFiles = data.submission(assignmentId, studentWithFiles);
        insertFile(withFiles, studentWithFiles);
        Long latestFile = insertFile(withFiles, studentWithFiles);

        mockMvc.perform(get("/utemp/submissions/assignment/{id}", assignmentId)
                        .param("sort", "id")
                        .header(HttpHeaders.AUTHORIZATION, bearer(data.login(TEACHER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(withoutFile))
                .andExpect(jsonPath("$.content[0].metadataID").value(nullValue()))
                .andExpect(jsonPath("$.content[1].id").value(withFiles))
                // Like the automatic review, the listing points to the latest upload
                .andExpect(jsonPath("$.content[1].metadataID").value(latestFile));
    }

    private Long insertFile(Long submissionId, Long studentId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO file_metadata (submission_id, student_id, file_name, file_size, storage_path, created_at, last_update)
                VALUES (?, ?, 'essay.txt', 4, 'not-on-disk', NOW(), NOW()) RETURNING id
                """, Long.class, submissionId, studentId);
    }
}
