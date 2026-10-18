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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.una.programmingIII.UTEMP_Project.controllers.TestData.bearer;

/**
 * A student can only read and change their own submissions, grades and notifications,
 * and can only hand in work for courses they are enrolled in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StudentAccessIntegrationTest {

    private static final String ENROLLED_STUDENT = "300000091";
    private static final String NOT_ENROLLED_STUDENT = "300000092";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestData data;
    private Long studentOneId;
    private Long studentTwoId;
    private Long studentOneSubmissionId;
    private Long assignmentId;

    @BeforeEach
    void setUp() {
        data = new TestData(mockMvc, jdbcTemplate);
        studentOneId = data.userId(TestData.STUDENT_ONE);
        studentTwoId = data.userId(TestData.STUDENT_TWO);
        assignmentId = data.assignmentOf(data.courseId(TestData.DEMO_COURSE));
        studentOneSubmissionId = data.submission(assignmentId, studentOneId);
    }

    @Test
    void studentCannotReadAnotherStudentsGrades() throws Exception {
        mockMvc.perform(get("/utemp/grades/submissions/{id}/grades", studentOneSubmissionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(data.login(TestData.STUDENT_TWO))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/utemp/grades/submissions/{id}/grades", studentOneSubmissionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(data.login(TestData.STUDENT_ONE))))
                .andExpect(status().isOk());
    }

    @Test
    void studentCannotUploadAFileToAnotherStudentsSubmission() throws Exception {
        mockMvc.perform(post("/utemp/files")
                        .header(HttpHeaders.AUTHORIZATION, bearer(data.login(TestData.STUDENT_TWO)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName": "evil.txt", "fileSize": 5, "student": {"id": %d}, "submission": {"id": %d}}
                                """.formatted(studentTwoId, studentOneSubmissionId)))
                .andExpect(status().isForbidden());

        assertThat(data.count("SELECT COUNT(*) FROM file_metadata WHERE submission_id = ? AND student_id = ?",
                studentOneSubmissionId, studentTwoId)).isZero();
    }

    @Test
    void studentCannotAddOrRemoveFilesOfAnotherStudentsSubmission() throws Exception {
        Long fileId = jdbcTemplate.queryForObject("""
                INSERT INTO file_metadata (submission_id, student_id, file_name, file_size, storage_path, created_at, last_update)
                VALUES (?, ?, 'mine.txt', 4, 'not-on-disk', NOW(), NOW()) RETURNING id
                """, Long.class, studentOneSubmissionId, studentOneId);
        String token = data.login(TestData.STUDENT_TWO);

        mockMvc.perform(delete("/utemp/submissions/{id}/file-metadata/{fileId}", studentOneSubmissionId, fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/utemp/submissions/{id}/file-metadata", studentOneSubmissionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName": "x.txt", "fileSize": 3, "student": {"id": %d}, "submission": {"id": %d}}
                                """.formatted(studentTwoId, studentOneSubmissionId)))
                .andExpect(status().is4xxClientError());

        assertThat(data.count("SELECT COUNT(*) FROM file_metadata WHERE id = ?", fileId)).isOne();
        assertThat(data.count("SELECT COUNT(*) FROM file_metadata WHERE submission_id = ? AND student_id = ?",
                studentOneSubmissionId, studentTwoId)).isZero();
    }

    @Test
    void studentCannotReadOrMarkAnotherStudentsNotification() throws Exception {
        Long notificationId = jdbcTemplate.queryForObject("""
                INSERT INTO notifications (user_id, message, status, created_at, last_update)
                VALUES (?, 'Only for student one', 'UNREAD', NOW(), NOW()) RETURNING id
                """, Long.class, studentOneId);
        String otherStudent = data.login(TestData.STUDENT_TWO);

        mockMvc.perform(get("/utemp/notifications/{id}", notificationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherStudent)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/utemp/notifications/markAsRead/{id}", notificationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherStudent)))
                .andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM notifications WHERE id = ?", String.class, notificationId))
                .isEqualTo("UNREAD");

        mockMvc.perform(get("/utemp/notifications/{id}", notificationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(data.login(TestData.STUDENT_ONE))))
                .andExpect(status().isOk());
    }

    @Test
    void enrolledStudentCanHandInTheirOwnWork() throws Exception {
        Long studentId = data.user(ENROLLED_STUDENT, "STUDENT", TestData.STUDENT_PERMISSIONS);
        data.enroll(studentId, data.courseId(TestData.DEMO_COURSE));

        mockMvc.perform(post("/utemp/assignments/{id}/submissions", assignmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(data.login(ENROLLED_STUDENT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionJson(studentId)))
                .andExpect(status().isCreated());

        assertThat(data.count("SELECT COUNT(*) FROM submissions WHERE assignment_id = ? AND student_id = ?",
                assignmentId, studentId)).isOne();
    }

    @Test
    void studentCannotHandInWorkAsAnotherStudent() throws Exception {
        Long studentId = data.user(ENROLLED_STUDENT, "STUDENT", TestData.STUDENT_PERMISSIONS);
        data.enroll(studentId, data.courseId(TestData.DEMO_COURSE));
        int before = data.count("SELECT COUNT(*) FROM submissions WHERE student_id = ?", studentOneId);

        mockMvc.perform(post("/utemp/assignments/{id}/submissions", assignmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(data.login(ENROLLED_STUDENT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionJson(studentOneId)))
                .andExpect(status().isForbidden());

        assertThat(data.count("SELECT COUNT(*) FROM submissions WHERE student_id = ?", studentOneId)).isEqualTo(before);
    }

    @Test
    void studentCannotHandInWorkForACourseTheyAreNotEnrolledIn() throws Exception {
        Long studentId = data.user(NOT_ENROLLED_STUDENT, "STUDENT", TestData.STUDENT_PERMISSIONS);

        mockMvc.perform(post("/utemp/assignments/{id}/submissions", assignmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(data.login(NOT_ENROLLED_STUDENT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionJson(studentId)))
                .andExpect(status().isForbidden());

        assertThat(data.count("SELECT COUNT(*) FROM submissions WHERE student_id = ?", studentId)).isZero();
    }

    private String submissionJson(Long studentId) {
        return """
                {"fileName": "pending-upload", "state": "SUBMITTED", "student": {"id": %d}, "assignment": {"id": %d}}
                """.formatted(studentId, assignmentId);
    }
}
