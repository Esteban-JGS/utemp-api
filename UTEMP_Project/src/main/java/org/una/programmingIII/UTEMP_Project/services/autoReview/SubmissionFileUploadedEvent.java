package org.una.programmingIII.UTEMP_Project.services.autoReview;

/**
 * Published when the last chunk of a submission's file has been stored.
 */
public record SubmissionFileUploadedEvent(Long submissionId) {
}
