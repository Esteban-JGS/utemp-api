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
import org.una.programmingIII.UTEMP_Project.TestcontainersConfiguration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.una.programmingIII.UTEMP_Project.controllers.TestData.bearer;

/**
 * Teachers manage assignments, submissions and grades only in the courses they teach.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CourseTeacherAccessIntegrationTest {

    private static final String OTHER_TEACHER = "200000091";
    private static final String STUDENT = "300000093";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestData data;
    private Long courseId;
    private Long assignmentId;
    private Long submissionId;
    private Long gradeId;
    private String otherTeacher;

    @BeforeEach
    void setUp() throws Exception {
        data = new TestData(mockMvc, jdbcTemplate);
        courseId = data.courseId(TestData.DEMO_COURSE);
        assignmentId = data.assignmentOf(courseId);

        Long studentId = data.user(STUDENT, "STUDENT", TestData.STUDENT_PERMISSIONS);
        data.enroll(studentId, courseId);
        submissionId = data.submission(assignmentId, studentId);
        jdbcTemplate.update("""
                INSERT INTO grades (submission_id, grade, comments, reviewed_by_ai, state, created_at, last_update)
                SELECT ?, 7.0, 'Preliminary', true, 'PENDING_REVIEW', NOW(), NOW()
                WHERE NOT EXISTS (SELECT 1 FROM grades WHERE submission_id = ?)
                """, submissionId, submissionId);
        gradeId = jdbcTemplate.queryForObject("SELECT id FROM grades WHERE submission_id = ?", Long.class, submissionId);

        Long otherTeacherId = data.user(OTHER_TEACHER, "TEACHER", TestData.TEACHER_PERMISSIONS);
        data.course("Test Databases", otherTeacherId);
        otherTeacher = data.login(OTHER_TEACHER);
    }

    @Test
    void onlyAdminsListEveryAssignment() throws Exception {
        mockMvc.perform(get("/utemp/assignments").header(HttpHeaders.AUTHORIZATION, bearer(otherTeacher)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/utemp/assignments").header(HttpHeaders.AUTHORIZATION, bearer(data.login(TestData.ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void teacherCannotReadOrChangeAssignmentsOfAnotherCourse() throws Exception {
        String title = assignmentTitle();

        mockMvc.perform(get("/utemp/assignments/{id}", assignmentId).header(HttpHeaders.AUTHORIZATION, bearer(otherTeacher)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/utemp/assignments/{id}", assignmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherTeacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Hijacked", "description": "changed", "state": "ONGOING"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/utemp/assignments/{id}", assignmentId).header(HttpHeaders.AUTHORIZATION, bearer(otherTeacher)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/utemp/assignments/course/{id}", courseId).header(HttpHeaders.AUTHORIZATION, bearer(otherTeacher)))
                .andExpect(status().isForbidden());

        assertThat(assignmentTitle()).isEqualTo(title);
    }

    @Test
    void teacherCannotAddOrRemoveAssignmentsOfAnotherCourse() throws Exception {
        int before = data.count("SELECT COUNT(*) FROM assignments WHERE course_id = ?", courseId);

        mockMvc.perform(post("/utemp/courses/{id}/assignments", courseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherTeacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Spam", "description": "x", "state": "PENDING"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/utemp/courses/{id}/assignments/{assignmentId}", courseId, assignmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherTeacher)))
                .andExpect(status().isForbidden());

        assertThat(data.count("SELECT COUNT(*) FROM assignments WHERE course_id = ?", courseId)).isEqualTo(before);
    }

    @Test
    void teacherCannotSeeOrGradeSubmissionsOfAnotherCourse() throws Exception {
        Map<String, Object> gradeBefore = grade();

        mockMvc.perform(get("/utemp/submissions/assignment/{id}", assignmentId).header(HttpHeaders.AUTHORIZATION, bearer(otherTeacher)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/utemp/submissions/{id}/{grade}", submissionId, 0.5)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherTeacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comments\": \"Graded by someone else\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/utemp/submissions/{id}/grades", submissionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherTeacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grade": 1, "comments": "x", "state": "FINALIZED", "submission": {"id": %d}}
                                """.formatted(submissionId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/utemp/submissions/{id}/grades/{gradeId}", submissionId, gradeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherTeacher)))
                .andExpect(status().isForbidden());

        assertThat(grade()).isEqualTo(gradeBefore);
        assertThat(data.count("SELECT COUNT(*) FROM grades WHERE submission_id = ?", submissionId)).isOne();
    }

    @Test
    void courseTeacherCanReviewTheirStudentsWork() throws Exception {
        String teacher = data.login(TestData.TEACHER);

        mockMvc.perform(get("/utemp/assignments/{id}", assignmentId).header(HttpHeaders.AUTHORIZATION, bearer(teacher)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/utemp/submissions/{id}/{grade}", submissionId, 8.5)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comments\": \"Well done\"}"))
                .andExpect(status().isOk());

        assertThat(grade()).containsEntry("state", "FINALIZED");
        assertThat(((Number) grade().get("grade")).doubleValue()).isEqualTo(8.5);
    }

    private String assignmentTitle() {
        return jdbcTemplate.queryForObject("SELECT title FROM assignments WHERE id = ?", String.class, assignmentId);
    }

    private Map<String, Object> grade() {
        return jdbcTemplate.queryForMap("SELECT grade, comments, state FROM grades WHERE id = ?", gradeId);
    }
}
