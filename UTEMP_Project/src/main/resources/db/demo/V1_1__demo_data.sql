-- Demo data. Only loaded when SPRING_FLYWAY_LOCATIONS includes classpath:db/demo (docker compose does this).
-- Every demo user has the password: Demo1234!

INSERT INTO users (name, email, password, identification_number, role, state, permissions, created_at, last_update)
VALUES ('Demo Admin', 'admin@utemp.example', '$2a$10$Q1mUicBGsDnn5dE4kICCl.GQSg4rL3EbwiVZP3h5L1E6foIQpAe.O', '100000001', 'ADMIN', 'ACTIVE',
        'MANAGE_USERS,MANAGE_UNIVERSITIES,MANAGE_FACULTIES,MANAGE_DEPARTMENTS,MANAGE_COURSES,MANAGE_ASSIGNMENTS,MANAGE_SUBMISSIONS,MANAGE_ENROLLMENTS,MANAGE_GRADES,GET_TEACHER_COURSES,ADD_TEACHER_COURSES,REMOVE_TEACHER_COURSE,GET_STUDENT_ENROLLMENTS,ADD_STUDENT_COURSES,REMOVE_STUDENT_COURSES,ADD_UNIVERSITY_FACULTIES,REMOVE_UNIVERSITY_FACULTIES,GET_UNIVERSITY_FACILITIES,ADD_FACULTY_DEPARTMENTS,REMOVE_FACULTY_DEPARTMENTS,GET_FACULTY_DEPARTMENTS,ADD_DEPARTMENT_COURSES,REMOVE_DEPARTMENT_COURSES,GET_DEPARTMENT_COURSES',
        NOW(), NOW()),
       ('Demo Teacher', 'teacher@utemp.example', '$2a$10$Q1mUicBGsDnn5dE4kICCl.GQSg4rL3EbwiVZP3h5L1E6foIQpAe.O', '200000001', 'TEACHER', 'ACTIVE',
        'GET_TEACHER_COURSES,GET_COURSE_ASSIGNMENTS,ADD_COURSE_ASSIGNMENTS,REMOVE_COURSE_ASSIGNMENTS,MANAGE_ASSIGNMENTS,GET_ASSIGNMENT_SUBMISSIONS,EVALUATE_SUBMISSIONS,ADD_SUBMISSION_GRADES,REMOVE_SUBMISSION_GRADES,GET_SUBMISSION_GRADES',
        NOW(), NOW()),
       ('Demo Student One', 'student1@utemp.example', '$2a$10$Q1mUicBGsDnn5dE4kICCl.GQSg4rL3EbwiVZP3h5L1E6foIQpAe.O', '300000001', 'STUDENT', 'ACTIVE',
        'GET_STUDENT_ENROLLMENTS,GET_COURSE_ASSIGNMENTS,ADD_ASSIGNMENT_SUBMISSION,ADD_SUBMISSION_FILES,REMOVE_SUBMISSION_FILES,GET_SUBMISSION_GRADES',
        NOW(), NOW()),
       ('Demo Student Two', 'student2@utemp.example', '$2a$10$Q1mUicBGsDnn5dE4kICCl.GQSg4rL3EbwiVZP3h5L1E6foIQpAe.O', '300000002', 'STUDENT', 'ACTIVE',
        'GET_STUDENT_ENROLLMENTS,GET_COURSE_ASSIGNMENTS,ADD_ASSIGNMENT_SUBMISSION,ADD_SUBMISSION_FILES,REMOVE_SUBMISSION_FILES,GET_SUBMISSION_GRADES',
        NOW(), NOW());

INSERT INTO universities (name, location, created_at, last_update)
VALUES ('Demo University', 'Heredia, Costa Rica', NOW(), NOW());

INSERT INTO faculties (name, university_id, created_at, last_update)
SELECT 'Exact Sciences', id, NOW(), NOW() FROM universities WHERE name = 'Demo University';

INSERT INTO departments (name, faculty_id, created_at, last_update)
SELECT 'Computer Science', id, NOW(), NOW() FROM faculties WHERE name = 'Exact Sciences';

INSERT INTO courses (name, description, teacher_id, department_id, state, created_at, last_update)
SELECT 'Programming III', 'Software design with Java and Spring Boot',
       (SELECT id FROM users WHERE identification_number = '200000001'), d.id, 'ACTIVE', NOW(), NOW()
FROM departments d WHERE d.name = 'Computer Science';

INSERT INTO enrollments (course_id, student_id, state, created_at, last_update)
SELECT c.id, u.id, 'ENROLLED', NOW(), NOW()
FROM courses c CROSS JOIN users u
WHERE c.name = 'Programming III' AND u.role = 'STUDENT';

INSERT INTO assignments (title, description, deadline, course_id, state, created_at, last_update)
SELECT 'Final project', 'Build a REST API with authentication and role-based access control',
       NOW() + INTERVAL '30 days', id, 'ONGOING', NOW(), NOW()
FROM courses WHERE name = 'Programming III';

INSERT INTO submissions (assignment_id, student_id, file_name, state, created_at, last_update)
SELECT a.id, u.id, 'pending-upload', 'SUBMITTED', NOW(), NOW()
FROM assignments a CROSS JOIN users u
WHERE a.title = 'Final project' AND u.role = 'STUDENT';
