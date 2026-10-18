package org.una.programmingIII.UTEMP_Project.services.autoReview;

import org.una.programmingIII.UTEMP_Project.models.Grade;

public interface AutoReviewService {

    /**
     * Stores a preliminary, simulated AI grade for a submission whose file has been uploaded.
     * Triggered automatically through {@link SubmissionFileUploadedEvent}.
     */
    Grade autoReviewSubmission(Long submissionId);
}
