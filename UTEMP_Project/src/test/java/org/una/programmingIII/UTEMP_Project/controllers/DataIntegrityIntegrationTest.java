package org.una.programmingIII.UTEMP_Project.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.una.programmingIII.UTEMP_Project.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.una.programmingIII.UTEMP_Project.controllers.TestData.bearer;

/**
 * Changes rejected by the database constraints are reported as conflicts, not as server errors.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DataIntegrityIntegrationTest {

    private static final String TEACHER = "200000071";
    private static final String STUDENT = "300000071";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestData data;
    private Long courseId;
    private Long studentId;

    @BeforeEach
    void setUp() {
        data = new TestData(mockMvc, jdbcTemplate);
        courseId = data.course("Test Integrity", data.user(TEACHER, "TEACHER", TestData.TEACHER_PERMISSIONS));
        studentId = data.user(STUDENT, "STUDENT", TestData.STUDENT_PERMISSIONS);
    }

    @Test
    void deletingAnAssignmentThatHasSubmissionsIsAConflict() throws Exception {
        Long assignmentId = data.assignment(courseId, "Handed in");
        data.submission(assignmentId, studentId);

        mockMvc.perform(delete("/utemp/assignments/{id}", assignmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(data.login(TestData.ADMIN))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.statusCode").value(409));
        assertThat(data.count("SELECT COUNT(*) FROM assignments WHERE id = ?", assignmentId)).isOne();
    }

    @Test
    void enrollingTheSameStudentTwiceIsAConflict() throws Exception {
        jdbcTemplate.update("DELETE FROM enrollments WHERE course_id = ? AND student_id = ?", courseId, studentId);
        String admin = data.login(TestData.ADMIN);
        String enrollment = """
                {"course": {"id": %d}, "student": {"id": %d}, "state": "ENROLLED"}
                """.formatted(courseId, studentId);

        mockMvc.perform(post("/utemp/enrollments").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(enrollment))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/utemp/enrollments").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(enrollment))
                .andExpect(status().isConflict());

        assertThat(data.count("SELECT COUNT(*) FROM enrollments WHERE course_id = ? AND student_id = ?", courseId, studentId))
                .isOne();
    }

    @Test
    void aSubmissionCannotHaveTwoGrades() {
        Long submissionId = data.submission(data.assignment(courseId, "Graded once"), studentId);
        String insertGrade = """
                INSERT INTO grades (submission_id, grade, state, created_at, last_update)
                VALUES (?, 8, 'FINALIZED', NOW(), NOW())
                """;
        jdbcTemplate.update("DELETE FROM grades WHERE submission_id = ?", submissionId);
        jdbcTemplate.update(insertGrade, submissionId);

        // The services look the grade up with findBySubmissionId, which fails if there are two
        assertThatThrownBy(() -> jdbcTemplate.update(insertGrade, submissionId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
