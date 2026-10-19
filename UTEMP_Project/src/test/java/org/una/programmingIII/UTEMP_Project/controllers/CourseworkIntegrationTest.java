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
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.una.programmingIII.UTEMP_Project.controllers.TestData.bearer;

/**
 * Everyday flows of a course: listing and grading submissions, enrolling students and changing the teacher.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CourseworkIntegrationTest {

    private static final String TEACHER = "200000081";
    private static final String NEW_TEACHER = "200000082";
    private static final String STUDENT_WITHOUT_FILE = "300000081";
    private static final String STUDENT_WITH_FILES = "300000082";
    private static final String STUDENT_TO_ENROLL = "300000083";
    private static final String STUDENT_TO_GRADE = "300000084";
    private static final String STUDENT_TO_REVIEW = "300000085";
    private static final String STUDENT_TO_UPDATE = "300000086";

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

    @Test
    void gradingASubmissionNotifiesTheStudent() throws Exception {
        Long teacherId = data.userId(TEACHER);
        Long studentId = data.user(STUDENT_TO_GRADE, "STUDENT", TestData.STUDENT_PERMISSIONS);
        Long submissionId = data.submission(data.assignment(courseId, "Quiz"), studentId);
        jdbcTemplate.update("DELETE FROM grades WHERE submission_id = ?", submissionId);
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id IN (?, ?)", studentId, teacherId);

        mockMvc.perform(post("/utemp/submissions/{id}/grades", submissionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(data.login(TEACHER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"submission": {"id": %d}, "grade": 9.5, "comments": "Well done", "state": "FINALIZED"}
                                """.formatted(submissionId)))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForList("SELECT message FROM notifications WHERE user_id = ?", String.class, studentId))
                .singleElement().asString().contains("Quiz", "9.5");
        // The teacher is the one who graded; they used to get the notification instead of the student
        assertThat(data.count("SELECT COUNT(*) FROM notifications WHERE user_id = ?", teacherId)).isZero();
    }

    @Test
    void manualReviewStoresTheCommentAsWritten() throws Exception {
        Long studentId = data.user(STUDENT_TO_REVIEW, "STUDENT", TestData.STUDENT_PERMISSIONS);
        Long submissionId = data.submission(data.assignment(courseId, "Lab"), studentId);
        jdbcTemplate.update("DELETE FROM grades WHERE submission_id = ?", submissionId);
        jdbcTemplate.update("""
                INSERT INTO grades (submission_id, grade, comments, reviewed_by_ai, state, created_at, last_update)
                VALUES (?, 6, 'Preliminary', TRUE, 'PENDING_REVIEW', NOW(), NOW())
                """, submissionId);

        mockMvc.perform(put("/utemp/submissions/{id}/{grade}", submissionId, 8.5)
                        .header(HttpHeaders.AUTHORIZATION, bearer(data.login(TEACHER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comments": "Nice work"}
                                """))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForMap("SELECT grade, comments, state FROM grades WHERE submission_id = ?", submissionId))
                .containsEntry("grade", 8.5)
                .containsEntry("comments", "Nice work")
                .containsEntry("state", "FINALIZED");
    }

    @Test
    void enrollingAStudentReportsTheRealOutcome() throws Exception {
        Long studentId = data.user(STUDENT_TO_ENROLL, "STUDENT", TestData.STUDENT_PERMISSIONS);
        String admin = data.login(TestData.ADMIN);

        mockMvc.perform(post("/utemp/users/{userId}/enrollments/{courseId}", studentId, courseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isCreated());
        assertThat(data.count("SELECT COUNT(*) FROM enrollments WHERE course_id = ? AND student_id = ?", courseId, studentId))
                .isOne();

        mockMvc.perform(post("/utemp/users/{userId}/enrollments/{courseId}", studentId, courseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/utemp/users/{userId}/enrollments/{courseId}", studentId, Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
        assertThat(data.count("SELECT COUNT(*) FROM enrollments WHERE student_id = ?", studentId)).isOne();
    }

    @Test
    void updatingAnEnrollmentSucceedsEveryTime() throws Exception {
        Long studentId = data.user(STUDENT_TO_UPDATE, "STUDENT", TestData.STUDENT_PERMISSIONS);
        data.enroll(studentId, courseId);
        Long enrollmentId = jdbcTemplate.queryForObject("SELECT id FROM enrollments WHERE course_id = ? AND student_id = ?",
                Long.class, courseId, studentId);
        String admin = data.login(TestData.ADMIN);

        // The update used to hand its entities to an @Async method, which read them from another thread while the
        // request was still using the same Hibernate session; repeating it makes that race show up
        for (int attempt = 1; attempt <= 20; attempt++) {
            String state = attempt % 2 == 0 ? "ENROLLED" : "COMPLETED";
            mockMvc.perform(put("/utemp/enrollments/{id}", enrollmentId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"course": {"id": %d}, "student": {"id": %d}, "state": "%s"}
                                    """.formatted(courseId, studentId, state)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value(state));
        }
    }

    @Test
    void theTeacherOfACourseIsChangedByUpdatingTheCourse() throws Exception {
        Long teacherId = data.userId(TEACHER);
        Long courseToHandOver = data.course("Test Handover", teacherId);
        Long newTeacherId = data.user(NEW_TEACHER, "TEACHER", TestData.TEACHER_PERMISSIONS);
        String admin = data.login(TestData.ADMIN);

        // The old endpoint left the course without a teacher, which the schema does not allow
        mockMvc.perform(delete("/utemp/users/{userId}/courses/{courseId}", teacherId, courseToHandOver)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(put("/utemp/courses/{id}", courseToHandOver)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Test Handover", "description": "Test course", "state": "ACTIVE",
                                 "teacher": {"id": %d}}
                                """.formatted(newTeacherId)))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT teacher_id FROM courses WHERE id = ?", Long.class, courseToHandOver))
                .isEqualTo(newTeacherId);
    }

    @Test
    void onlyATeacherCanTeachACourse() throws Exception {
        Long teacherId = data.userId(TEACHER);
        Long studentId = data.user(STUDENT_WITHOUT_FILE, "STUDENT", TestData.STUDENT_PERMISSIONS);
        Long departmentId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM departments", Long.class);
        String admin = data.login(TestData.ADMIN);
        String newCourse = """
                {"name": "Test Taught By", "description": "Test course", "state": "ACTIVE",
                 "teacher": {"id": %d}, "department": {"id": %d}}
                """;

        mockMvc.perform(post("/utemp/courses").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(newCourse.formatted(studentId, departmentId)))
                .andExpect(status().isBadRequest());
        assertThat(data.count("SELECT COUNT(*) FROM courses WHERE name = 'Test Taught By'")).isZero();

        mockMvc.perform(put("/utemp/courses/{id}", courseId).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Test Coursework", "description": "Test course", "state": "ACTIVE",
                                 "teacher": {"id": %d}}
                                """.formatted(studentId)))
                .andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject("SELECT teacher_id FROM courses WHERE id = ?", Long.class, courseId))
                .isEqualTo(teacherId);

        mockMvc.perform(post("/utemp/courses").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(newCourse.formatted(teacherId, departmentId)))
                .andExpect(status().isCreated());
    }

    @Test
    void usersAreListedByRoleOnlyThroughThePaginatedEndpoint() throws Exception {
        String admin = data.login(TestData.ADMIN);

        mockMvc.perform(get("/utemp/users/usersByRole/{role}", "TEACHER").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].role", everyItem(is("TEACHER"))));
        // The single-result variant failed for every role and was removed
        mockMvc.perform(get("/utemp/users/role/{role}", "TEACHER").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    private Long insertFile(Long submissionId, Long studentId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO file_metadata (submission_id, student_id, file_name, file_size, storage_path, created_at, last_update)
                VALUES (?, ?, 'essay.txt', 4, 'not-on-disk', NOW(), NOW()) RETURNING id
                """, Long.class, submissionId, studentId);
    }
}
