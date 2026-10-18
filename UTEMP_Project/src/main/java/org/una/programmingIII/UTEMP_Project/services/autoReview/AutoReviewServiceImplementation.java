package org.una.programmingIII.UTEMP_Project.services.autoReview;

import io.krakens.grok.api.Grok;
import io.krakens.grok.api.GrokCompiler;
import io.krakens.grok.api.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.una.programmingIII.UTEMP_Project.exceptions.InvalidDataException;
import org.una.programmingIII.UTEMP_Project.exceptions.ResourceNotFoundException;
import org.una.programmingIII.UTEMP_Project.models.FileMetadatum;
import org.una.programmingIII.UTEMP_Project.models.Grade;
import org.una.programmingIII.UTEMP_Project.models.GradeState;
import org.una.programmingIII.UTEMP_Project.models.Notification;
import org.una.programmingIII.UTEMP_Project.models.NotificationStatus;
import org.una.programmingIII.UTEMP_Project.models.Submission;
import org.una.programmingIII.UTEMP_Project.observers.Subject;
import org.una.programmingIII.UTEMP_Project.repositories.GradeRepository;
import org.una.programmingIII.UTEMP_Project.repositories.NotificationRepository;
import org.una.programmingIII.UTEMP_Project.repositories.SubmissionRepository;
import org.una.programmingIII.UTEMP_Project.services.EmailNotificationObserver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulated AI reviewer: once a submission's file is uploaded, it stores a preliminary grade and comment
 * (state PENDING_REVIEW) that the teacher later confirms through the manual review.
 */
@Service
public class AutoReviewServiceImplementation extends Subject<EmailNotificationObserver> implements AutoReviewService {

    private static final Logger logger = LoggerFactory.getLogger(AutoReviewServiceImplementation.class);

    private static final String COMMENT_PATTERN = "%{NUMBER:grade} : %{GREEDYDATA:message}";
    private static final double MAX_GRADE = 10.0;
    private static final double MIN_GRADE = 0.0;
    private static final Map<Integer, String> COMMENTS = Map.ofEntries(
            Map.entry(0, "You have much to improve in your work."),
            Map.entry(1, "You need to try harder."),
            Map.entry(2, "The work is below expectations."),
            Map.entry(3, "You need to improve several aspects."),
            Map.entry(4, "Acceptable work but with a lot of room for improvement."),
            Map.entry(5, "Average work."),
            Map.entry(6, "Good work, but there are areas to improve."),
            Map.entry(7, "Good work, almost excellent."),
            Map.entry(8, "Very good work."),
            Map.entry(9, "Excellent work."),
            Map.entry(10, "Excellent, very good work."));

    // The same file always gets the same grade and comment
    private final Map<Long, Double> fileGrades = new ConcurrentHashMap<>();
    private final Map<Long, String> fileComments = new ConcurrentHashMap<>();
    private final SubmissionRepository submissionRepository;
    private final GradeRepository gradeRepository;
    private final NotificationRepository notificationRepository;
    private final Grok grok;

    public AutoReviewServiceImplementation(SubmissionRepository submissionRepository,
                                           GradeRepository gradeRepository,
                                           NotificationRepository notificationRepository) {
        this.submissionRepository = submissionRepository;
        this.gradeRepository = gradeRepository;
        this.notificationRepository = notificationRepository;
        this.grok = initializeGrok();
    }

    private Grok initializeGrok() {
        GrokCompiler grokCompiler = GrokCompiler.newInstance();
        grokCompiler.register("NUMBER", "(\\d+(\\.\\d+)?)");
        grokCompiler.register("GREEDYDATA", "(.*)");
        return grokCompiler.compile(COMMENT_PATTERN);
    }

    /**
     * Runs after the upload transaction commits, on the async executor, so the upload response is not delayed
     * and the review always sees the stored file.
     */
    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmissionFileUploaded(SubmissionFileUploadedEvent event) {
        try {
            autoReviewSubmission(event.submissionId());
        } catch (Exception e) {
            // Nobody waits for this thread, so failures are only visible in the logs
            logger.error("Automatic review of submission {} failed: {}", event.submissionId(), e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public Grade autoReviewSubmission(Long submissionId) {
        Submission submission = validateSubmission(submissionId);
        if (submission.getFileMetadata().isEmpty()) {
            throw new InvalidDataException("Submission " + submissionId + " has no uploaded file to review.");
        }
        // One grade per submission: re-uploads update the pending AI grade, a grade finalized by the teacher is kept
        Grade gradeEntity = gradeRepository.findBySubmissionId(submissionId).orElseGet(Grade::new);
        if (gradeEntity.getState() == GradeState.FINALIZED) {
            logger.info("Submission {} already has a grade finalized by the teacher; skipping automatic review", submissionId);
            return gradeEntity;
        }

        // A submission can be re-uploaded; review the latest file
        Long fileId = submission.getFileMetadata().stream()
                .map(FileMetadatum::getId)
                .max(Long::compare)
                .orElseThrow();

        double grade = fileGrades.computeIfAbsent(fileId, id -> generateGrade());
        String comment = fileComments.computeIfAbsent(fileId, id -> generateComment(grade));

        Grade savedGrade = gradeRepository.save(fillGrade(gradeEntity, submission, grade, comment));
        logger.info("Submission {} reviewed automatically with grade {}", submissionId, grade);
        notifyStudent(savedGrade, submission);
        return savedGrade;
    }

    private double generateGrade() {
        double grade = Math.random() * (MAX_GRADE - MIN_GRADE) + MIN_GRADE;
        return Math.round(grade * 10) / 10.0;
    }

    private String generateComment(double grade) {
        if (grade < MIN_GRADE || grade > MAX_GRADE) {
            throw new InvalidDataException("Grade must be between 0 and 10.");
        }
        String message = COMMENTS.get((int) Math.floor(grade));
        return extractMessageWithGrok(grade + " : " + message);
    }

    private String extractMessageWithGrok(String logMessage) {
        Match match = grok.match(logMessage);

        if (match != null && !match.capture().isEmpty()) {
            return (String) match.capture().getOrDefault("message", "Message not captured.");
        } else {
            logger.warn("No match found for log message: {}", logMessage);
            return "Message not captured.";
        }
    }

    private Grade fillGrade(Grade gradeEntity, Submission submission, double grade, String comment) {
        gradeEntity.setSubmission(submission);
        gradeEntity.setGrade(grade);
        gradeEntity.setComments(comment);
        gradeEntity.setReviewedByAi(true);
        gradeEntity.setState(GradeState.PENDING_REVIEW);
        return gradeEntity;
    }

    private Submission validateSubmission(Long submissionId) {
        if (submissionId == null || submissionId <= 0) {
            throw new InvalidDataException("Submission ID must be a positive number.");
        }

        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId));
    }

    private void notifyStudent(Grade grade, Submission submission) {
        String message = "Your submission for '" + submission.getAssignment().getTitle()
                + "' received a preliminary grade of " + grade.getGrade() + ". Your teacher will review it.";

        Notification notification = new Notification();
        notification.setUser(submission.getStudent());
        notification.setMessage(message);
        notification.setStatus(NotificationStatus.UNREAD);
        notificationRepository.save(notification);

        try {
            notifyObservers("SUBMISSION_GRADED", message, submission.getStudent().getEmail());
        } catch (Exception e) {
            logger.error("Error sending the grade email to {}: {}", submission.getStudent().getEmail(), e.getMessage());
        }
    }
}
