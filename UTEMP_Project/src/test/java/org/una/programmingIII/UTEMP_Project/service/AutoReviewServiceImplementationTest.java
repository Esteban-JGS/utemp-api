package org.una.programmingIII.UTEMP_Project.service;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.una.programmingIII.UTEMP_Project.exceptions.InvalidDataException;
import org.una.programmingIII.UTEMP_Project.exceptions.ResourceNotFoundException;
import org.una.programmingIII.UTEMP_Project.models.FileMetadatum;
import org.una.programmingIII.UTEMP_Project.models.Grade;
import org.una.programmingIII.UTEMP_Project.models.GradeState;
import org.una.programmingIII.UTEMP_Project.models.Submission;
import org.una.programmingIII.UTEMP_Project.repositories.SubmissionRepository;
import org.una.programmingIII.UTEMP_Project.services.autoReview.AutoReviewServiceImplementation;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public class AutoReviewServiceImplementationTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @InjectMocks
    private AutoReviewServiceImplementation autoReviewService;

    private Submission submission;

    /*
     * autoReviewSubmission hands its work to CompletableFuture.supplyAsync and discards the future, so failures never
     * reach the caller. supplyAsync is intercepted here to run the work on the test thread and keep the resulting
     * future, which lets the tests inspect the real outcome of the review without waiting on another thread.
     */
    private MockedStatic<CompletableFuture> completableFutureMock;
    private CompletableFuture<Object> reviewFuture;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        submission = mock(Submission.class);
        when(submission.getId()).thenReturn(1L);

        completableFutureMock = mockStatic(CompletableFuture.class, CALLS_REAL_METHODS);
        completableFutureMock.when(() -> CompletableFuture.supplyAsync(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> work = invocation.getArgument(0);
            reviewFuture = new CompletableFuture<>();
            try {
                reviewFuture.complete(work.get());
            } catch (Throwable e) {
                reviewFuture.completeExceptionally(e);
            }
            return reviewFuture;
        });
    }

    @AfterEach
    public void tearDown() {
        completableFutureMock.close();
    }

    private void givenSubmissionWithFile(Long submissionId, Long fileId) {
        FileMetadatum file = new FileMetadatum();
        file.setId(fileId);
        when(submission.getFileMetadata()).thenReturn(List.of(file));
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
    }

    private Grade reviewedGrade() {
        assertNotNull(reviewFuture);
        return (Grade) reviewFuture.join();
    }

    @Test
    public void testAutoReviewSubmission_ValidSubmission() {
        givenSubmissionWithFile(1L, 10L);

        autoReviewService.autoReviewSubmission(1L);

        verify(submissionRepository).findById(1L);
        Grade grade = reviewedGrade();
        assertSame(submission, grade.getSubmission());
        assertTrue(grade.getReviewedByAi());
        assertEquals(GradeState.PENDING_REVIEW, grade.getState());
    }

    @Test
    public void testAutoReviewSubmission_InvalidSubmissionId() {

        when(submissionRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> autoReviewService.autoReviewSubmission(1L));

        assertNotNull(reviewFuture);
        CompletionException thrown = assertThrows(CompletionException.class, reviewFuture::join);
        assertInstanceOf(ResourceNotFoundException.class, thrown.getCause());
    }

    @Test
    public void testGenerateGrade_ValidRange() {
        givenSubmissionWithFile(1L, 10L);

        autoReviewService.autoReviewSubmission(1L);

        double grade = reviewedGrade().getGrade();
        assertTrue(grade >= 0.0 && grade <= 10.0);
    }

    @Test
    public void testGenerateComment_ValidGrade() {
        givenSubmissionWithFile(1L, 10L);

        autoReviewService.autoReviewSubmission(1L);
        Grade first = reviewedGrade();
        autoReviewService.autoReviewSubmission(1L);
        Grade second = reviewedGrade();

        assertNotNull(first.getComments());
        assertFalse(first.getComments().isBlank());
        // Grade and comment are cached per file, so reviewing the same file again gives the same result
        assertEquals(first.getGrade(), second.getGrade());
        assertEquals(first.getComments(), second.getComments());
    }

    @Test
    public void testGenerateComment_InvalidGrade() {

        assertDoesNotThrow(() -> autoReviewService.autoReviewSubmission(-1L));

        assertNotNull(reviewFuture);
        CompletionException thrown = assertThrows(CompletionException.class, reviewFuture::join);
        assertInstanceOf(InvalidDataException.class, thrown.getCause());
        assertEquals("Invalid data: Submission ID must be a positive number.", thrown.getCause().getMessage());
        verifyNoInteractions(submissionRepository);
    }


    @Test
    public void testGenerateComment_MessageNotFound() {

        autoReviewService.autoReviewSubmission(11L);
    }
}
