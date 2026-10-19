package org.una.programmingIII.UTEMP_Project.controllers;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login and extra rows for integration tests. Every test class shares the same database, so the helpers only add
 * users, courses and submissions of their own and never modify the demo data other tests rely on.
 */
class TestData {

    static final String ADMIN = "100000001";
    static final String TEACHER = "200000001";
    static final String STUDENT_ONE = "300000001";
    static final String STUDENT_TWO = "300000002";
    static final String DEMO_COURSE = "Programming III";

    // BCrypt hash of Demo1234!, the same one used by the demo data
    private static final String PASSWORD_HASH = "$2a$10$Q1mUicBGsDnn5dE4kICCl.GQSg4rL3EbwiVZP3h5L1E6foIQpAe.O";
    static final String TEACHER_PERMISSIONS = "GET_TEACHER_COURSES,GET_COURSE_ASSIGNMENTS,ADD_COURSE_ASSIGNMENTS,"
            + "REMOVE_COURSE_ASSIGNMENTS,MANAGE_ASSIGNMENTS,GET_ASSIGNMENT_SUBMISSIONS,EVALUATE_SUBMISSIONS,"
            + "ADD_SUBMISSION_GRADES,REMOVE_SUBMISSION_GRADES,GET_SUBMISSION_GRADES";
    static final String STUDENT_PERMISSIONS = "GET_STUDENT_ENROLLMENTS,GET_COURSE_ASSIGNMENTS,ADD_ASSIGNMENT_SUBMISSION,"
            + "ADD_SUBMISSION_FILES,REMOVE_SUBMISSION_FILES,GET_SUBMISSION_GRADES";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    TestData(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    String login(String identificationNumber) throws Exception {
        return (String) loginResponse(identificationNumber, "$.data.token");
    }

    String refreshToken(String identificationNumber) throws Exception {
        return (String) loginResponse(identificationNumber, "$.data.refreshToken");
    }

    private Object loginResponse(String identificationNumber, String path) throws Exception {
        MvcResult result = mockMvc.perform(post("/utemp/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identificationNumber": "%s", "password": "Demo1234!"}
                                """.formatted(identificationNumber)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    Long userId(String identificationNumber) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE identification_number = ?", Long.class, identificationNumber);
    }

    /** Creates the user if needed and makes sure it is ACTIVE. */
    Long user(String identificationNumber, String role, String permissions) {
        jdbcTemplate.update("""
                INSERT INTO users (name, email, password, identification_number, role, state, permissions, created_at, last_update)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, NOW(), NOW())
                ON CONFLICT (identification_number) DO UPDATE SET state = 'ACTIVE'
                """, "Test " + identificationNumber, identificationNumber + "@utemp.example", PASSWORD_HASH,
                identificationNumber, role, permissions);
        return userId(identificationNumber);
    }

    Long courseId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM courses WHERE name = ?", Long.class, name);
    }

    /** Creates a course taught by the given teacher, in the demo department. */
    Long course(String name, Long teacherId) {
        jdbcTemplate.update("""
                INSERT INTO courses (name, description, teacher_id, department_id, state, created_at, last_update)
                SELECT ?, 'Test course', ?, (SELECT MIN(id) FROM departments), 'ACTIVE', NOW(), NOW()
                WHERE NOT EXISTS (SELECT 1 FROM courses WHERE name = ?)
                """, name, teacherId, name);
        return courseId(name);
    }

    /** Creates an assignment with this title in the course if needed. */
    Long assignment(Long courseId, String title) {
        jdbcTemplate.update("""
                INSERT INTO assignments (title, description, deadline, course_id, state, created_at, last_update)
                SELECT ?, 'Test assignment', NOW() + INTERVAL '7 days', ?, 'ONGOING', NOW(), NOW()
                WHERE NOT EXISTS (SELECT 1 FROM assignments WHERE course_id = ? AND title = ?)
                """, title, courseId, courseId, title);
        return jdbcTemplate.queryForObject("SELECT id FROM assignments WHERE course_id = ? AND title = ?",
                Long.class, courseId, title);
    }

    Long assignmentOf(Long courseId) {
        return jdbcTemplate.queryForObject("SELECT MIN(id) FROM assignments WHERE course_id = ?", Long.class, courseId);
    }

    void enroll(Long studentId, Long courseId) {
        enroll(studentId, courseId, "ENROLLED");
    }

    void enroll(Long studentId, Long courseId, String state) {
        jdbcTemplate.update("""
                INSERT INTO enrollments (course_id, student_id, state, created_at, last_update)
                SELECT ?, ?, ?, NOW(), NOW()
                WHERE NOT EXISTS (SELECT 1 FROM enrollments WHERE course_id = ? AND student_id = ?)
                """, courseId, studentId, state, courseId, studentId);
    }

    /** The first submission of the student for the assignment, created if needed. */
    Long submission(Long assignmentId, Long studentId) {
        jdbcTemplate.update("""
                INSERT INTO submissions (assignment_id, student_id, file_name, state, created_at, last_update)
                SELECT ?, ?, 'pending-upload', 'SUBMITTED', NOW(), NOW()
                WHERE NOT EXISTS (SELECT 1 FROM submissions WHERE assignment_id = ? AND student_id = ?)
                """, assignmentId, studentId, assignmentId, studentId);
        return jdbcTemplate.queryForObject("SELECT MIN(id) FROM submissions WHERE assignment_id = ? AND student_id = ?",
                Long.class, assignmentId, studentId);
    }

    int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }
}
