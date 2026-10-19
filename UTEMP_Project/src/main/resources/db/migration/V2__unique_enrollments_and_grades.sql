-- A student is enrolled in a course at most once, and a submission has a single grade
-- (the services read it with findBySubmissionId, which fails if there are two)
ALTER TABLE enrollments ADD CONSTRAINT uq_enrollments_course_student UNIQUE (course_id, student_id);
ALTER TABLE grades ADD CONSTRAINT uq_grades_submission UNIQUE (submission_id);
