package org.una.programmingIII.UTEMP_Project.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.una.programmingIII.UTEMP_Project.exceptions.InvalidDataException;
import org.una.programmingIII.UTEMP_Project.exceptions.ResourceNotFoundException;
import org.una.programmingIII.UTEMP_Project.models.*;
import org.una.programmingIII.UTEMP_Project.repositories.GradeRepository;
import org.una.programmingIII.UTEMP_Project.repositories.NotificationRepository;
import org.una.programmingIII.UTEMP_Project.repositories.SubmissionRepository;
import org.una.programmingIII.UTEMP_Project.services.autoReview.AutoReviewServiceImplementation;
import org.una.programmingIII.UTEMP_Project.services.autoReview.SubmissionFileUploadedEvent;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoReviewServiceImplementationTest {

    private static final long SUBMISSION_ID = 1L;

    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private GradeRepository gradeRepository;
    @Mock
    private NotificationRepository notificationRepository;

    private AutoReviewServiceImplementation autoReviewService;
    private Submission submission;
    private User student;

    @BeforeEach
    void setUp() {
        autoReviewService = new AutoReviewServiceImplementation(submissionRepository, gradeRepository, notificationRepository);

        student = new User();
        student.setId(3L);
        student.setEmail("student@example.com");
        Assignment assignment = new Assignment();
        assignment.setTitle("Final project");
        submission = new Submission();
        submission.setId(SUBMISSION_ID);
        submission.setStudent(student);
        submission.setAssignment(assignment);
    }

    private void givenUploadedFile(long fileId) {
        FileMetadatum file = new FileMetadatum();
        file.setId(fileId);
        submission.getFileMetadata().add(file);
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        // Some tests replace or never reach these, so they are lenient
        lenient().when(gradeRepository.findBySubmissionId(SUBMISSION_ID)).thenReturn(Optional.empty());
        lenient().when(gradeRepository.save(any(Grade.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void reviewStoresAPreliminaryGradeForTheTeacherToConfirm() {
        givenUploadedFile(10L);

        Grade grade = autoReviewService.autoReviewSubmission(SUBMISSION_ID);

        verify(gradeRepository).save(grade);
        assertSame(submission, grade.getSubmission());
        assertTrue(grade.getReviewedByAi());
        assertEquals(GradeState.PENDING_REVIEW, grade.getState());
        assertTrue(grade.getGrade() >= 0.0 && grade.getGrade() <= 10.0, "grade out of range: " + grade.getGrade());
        assertEquals(grade.getGrade(), Math.round(grade.getGrade() * 10) / 10.0, "grade is rounded to one decimal");
    }

    @Test
    void commentComesFromTheCommentTable() {
        givenUploadedFile(10L);

        Grade grade = autoReviewService.autoReviewSubmission(SUBMISSION_ID);

        assertNotNull(grade.getComments());
        assertNotEquals("Comment not found.", grade.getComments());
        assertNotEquals("Message not captured.", grade.getComments());
    }

    @Test
    void theSameFileAlwaysGetsTheSameGradeAndComment() {
        givenUploadedFile(10L);

        Grade first = autoReviewService.autoReviewSubmission(SUBMISSION_ID);
        Grade second = autoReviewService.autoReviewSubmission(SUBMISSION_ID);

        assertEquals(first.getGrade(), second.getGrade());
        assertEquals(first.getComments(), second.getComments());
    }

    @Test
    void whenAFileIsReuploadedTheLatestOneIsReviewed() {
        givenUploadedFile(10L);
        FileMetadatum latestFile = new FileMetadatum();
        latestFile.setId(11L);
        submission.getFileMetadata().add(latestFile);

        Grade withBothFiles = autoReviewService.autoReviewSubmission(SUBMISSION_ID);
        submission.getFileMetadata().removeIf(file -> file.getId() == 10L);
        Grade withOnlyTheLatestFile = autoReviewService.autoReviewSubmission(SUBMISSION_ID);

        // Grades are cached per file, so equal grades mean the same file (11) was reviewed both times
        assertEquals(withOnlyTheLatestFile.getGrade(), withBothFiles.getGrade());
        assertEquals(withOnlyTheLatestFile.getComments(), withBothFiles.getComments());
    }

    @Test
    void aPendingAiGradeIsUpdatedInsteadOfCreatingAnotherOne() {
        givenUploadedFile(10L);
        Grade pending = new Grade();
        pending.setId(7L);
        pending.setState(GradeState.PENDING_REVIEW);
        when(gradeRepository.findBySubmissionId(SUBMISSION_ID)).thenReturn(Optional.of(pending));

        Grade grade = autoReviewService.autoReviewSubmission(SUBMISSION_ID);

        assertSame(pending, grade);
        assertEquals(7L, grade.getId());
        verify(gradeRepository).save(pending);
    }

    @Test
    void aGradeFinalizedByTheTeacherIsNeverOverwritten() {
        givenUploadedFile(10L);
        Grade finalized = new Grade();
        finalized.setGrade(9.5);
        finalized.setState(GradeState.FINALIZED);
        when(gradeRepository.findBySubmissionId(SUBMISSION_ID)).thenReturn(Optional.of(finalized));

        Grade grade = autoReviewService.autoReviewSubmission(SUBMISSION_ID);

        assertSame(finalized, grade);
        assertEquals(9.5, grade.getGrade());
        verify(gradeRepository, never()).save(any());
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void theStudentIsNotified() {
        givenUploadedFile(10L);

        Grade grade = autoReviewService.autoReviewSubmission(SUBMISSION_ID);

        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notification.capture());
        assertSame(student, notification.getValue().getUser());
        assertEquals(NotificationStatus.UNREAD, notification.getValue().getStatus());
        assertTrue(notification.getValue().getMessage().contains("Final project"));
        assertTrue(notification.getValue().getMessage().contains(String.valueOf(grade.getGrade())));
    }

    @Test
    void submissionWithoutFileIsNotReviewed() {
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));

        assertThrows(InvalidDataException.class, () -> autoReviewService.autoReviewSubmission(SUBMISSION_ID));
        verifyNoInteractions(gradeRepository, notificationRepository);
    }

    @Test
    void unknownSubmissionIsRejected() {
        when(submissionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> autoReviewService.autoReviewSubmission(99L));
        verifyNoInteractions(gradeRepository);
    }

    @Test
    void invalidSubmissionIdIsRejected() {
        assertThrows(InvalidDataException.class, () -> autoReviewService.autoReviewSubmission(0L));
        assertThrows(InvalidDataException.class, () -> autoReviewService.autoReviewSubmission(null));
        verifyNoInteractions(submissionRepository, gradeRepository);
    }

    @Test
    void uploadEventTriggersTheReviewAndFailuresDoNotPropagate() {
        givenUploadedFile(10L);
        autoReviewService.onSubmissionFileUploaded(new SubmissionFileUploadedEvent(SUBMISSION_ID));
        verify(gradeRepository).save(any(Grade.class));

        // Runs on a background thread nobody waits for: errors are logged, not thrown
        when(submissionRepository.findById(99L)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> autoReviewService.onSubmissionFileUploaded(new SubmissionFileUploadedEvent(99L)));
    }
}
