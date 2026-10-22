package org.una.programmingIII.UTEMP_Project.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.una.programmingIII.UTEMP_Project.controllers.request.ManualReviewRequest;
import org.una.programmingIII.UTEMP_Project.dtos.FileMetadatumDTO;
import org.una.programmingIII.UTEMP_Project.dtos.GradeDTO;
import org.una.programmingIII.UTEMP_Project.dtos.SubmissionDTO;
import org.una.programmingIII.UTEMP_Project.exceptions.ResourceNotFoundException;
import org.una.programmingIII.UTEMP_Project.models.Grade;
import org.una.programmingIII.UTEMP_Project.services.submission.SubmissionService;
import org.una.programmingIII.UTEMP_Project.utils.PageConverter;
import org.una.programmingIII.UTEMP_Project.utils.PageDTO;

import java.util.Optional;

@RestController
@RequestMapping("/utemp/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    @Autowired
    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @Operation(
            summary = "Get all submissions",
            description = "Retrieve a paginated list of all submissions."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "List of submissions retrieved successfully.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Page.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Internal server error.\"}")
                    )
            )
    })
    @GetMapping
    @PreAuthorize("hasAuthority('MANAGE_SUBMISSIONS')")
    public ResponseEntity<PageDTO<SubmissionDTO>> getAllSubmissions(Pageable pageable) {
        Page<SubmissionDTO> submissionsPage = submissionService.getAllSubmissions(pageable);
        PageDTO<SubmissionDTO> submissionsDTOPage = PageConverter.convertPageToDTO(submissionsPage, submissionDTO -> submissionDTO);
        return ResponseEntity.ok(submissionsDTOPage);
    }

    @Operation(
            summary = "Get submission by ID",
            description = "Retrieve a submission by its ID."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Submission retrieved successfully.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SubmissionDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Submission not found.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Submission not found.\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Internal server error.\"}")
                    )
            )
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_SUBMISSIONS')")
    public ResponseEntity<SubmissionDTO> getSubmissionById(
            @Parameter(description = "ID of the submission to retrieve", required = true) @PathVariable Long id) {
        Optional<SubmissionDTO> submissionDTO = submissionService.getSubmissionById(id);
        return submissionDTO.map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", id));
    }


    @Operation(
            summary = "Create a new submission",
            description = "Create a new submission."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Submission created successfully.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SubmissionDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid data provided.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Invalid data provided.\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Internal server error.\"}")
                    )
            )
    })
    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_SUBMISSIONS')")
    public ResponseEntity<SubmissionDTO> createSubmission(
            @Valid @RequestBody SubmissionDTO submissionDTO) {
        SubmissionDTO createdSubmission = submissionService.createSubmission(submissionDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSubmission);
    }

    @Operation(
            summary = "Update submission",
            description = "Update an existing submission by its ID."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Submission updated successfully.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SubmissionDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Submission not found.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Submission not found.\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid data provided.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Invalid data provided.\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Internal server error.\"}")
                    )
            )
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_SUBMISSIONS')")
    public ResponseEntity<SubmissionDTO> updateSubmission(
            @Parameter(description = "ID of the submission to update", required = true) @PathVariable Long id,
            @Valid @RequestBody SubmissionDTO submissionDTO) {
        Optional<SubmissionDTO> updatedSubmission = submissionService.updateSubmission(id, submissionDTO);
        return updatedSubmission.map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", id));
    }

    @Operation(
            summary = "Delete submission",
            description = "Delete a submission by its ID."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Submission deleted successfully."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Submission not found.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Submission not found.\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Internal server error.\"}")
                    )
            )
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_SUBMISSIONS')")
    public ResponseEntity<Void> deleteSubmission(
            @Parameter(description = "ID of the submission to delete", required = true) @PathVariable Long id) {
        submissionService.deleteSubmission(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Get submissions by assignment ID",
            description = "Retrieve a paginated list of submissions for a specific assignment."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "List of submissions for assignment retrieved successfully."
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Internal server error.\"}")
                    )
            )
    })
    @GetMapping("/assignment/{assignmentId}")
    @PreAuthorize("hasAuthority('GET_ASSIGNMENT_SUBMISSIONS') and @access.canManageAssignment(#assignmentId, principal)")
    public ResponseEntity<PageDTO<SubmissionDTO>> getSubmissionsByAssignmentId(
            @Parameter(description = "ID of the assignment", required = true) @PathVariable Long assignmentId,
            Pageable pageable) {
        Page<SubmissionDTO> submissionsPage = submissionService.getSubmissionsByAssignmentId(assignmentId, pageable);
        PageDTO<SubmissionDTO> submissionsDTOPage = PageConverter.convertPageToDTO(submissionsPage, submissionDTO -> submissionDTO);
        for (SubmissionDTO dtp : submissionsDTOPage.getContent()) {
            dtp.setAssignmentId(assignmentId);
            dtp.setAssignmentTitle(dtp.getAssignment().getTitle());
            dtp.setStudentName(dtp.getStudent().getName());
            dtp.setStudentId(dtp.getStudent().getId());
            // A submission may have no file yet; when it was re-uploaded, the latest file is the one that counts
            dtp.getFileMetadata().stream()
                    .map(FileMetadatumDTO::getId)
                    .max(Long::compare)
                    .ifPresent(dtp::setMetadataID);
        }

        return ResponseEntity.ok(submissionsDTOPage);
    }

    @Operation(
            summary = "Add grade to submission",
            description = "Add a grade to an existing submission."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Grade added successfully.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GradeDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid data provided.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Invalid data provided.\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Submission not found.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Submission not found.\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Internal server error.\"}")
                    )
            )
    })
    @PostMapping("/{submissionId}/grades")
    @PreAuthorize("hasAuthority('ADD_SUBMISSION_GRADES') and @access.canManageSubmission(#submissionId, principal)")
    public ResponseEntity<GradeDTO> addGradeToSubmission(
            @Parameter(description = "ID of the submission", required = true) @PathVariable Long submissionId,
            @Valid @RequestBody GradeDTO gradeDTO) {
        GradeDTO createdGrade = submissionService.addGradeToSubmission(submissionId, gradeDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdGrade);
    }


    @Operation(
            summary = "Remove Grade from Submission",
            description = "This endpoint allows users to remove a specific grade entry from a designated submission. The IDs for both the submission and the grade to be removed are provided as path parameters. Upon successful execution, the specified grade will no longer be associated with the submission."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Grade successfully removed from the submission. No content is returned as confirmation of the operation.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Grade successfully removed from the submission.\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Submission or grade not found. This indicates that either the specified submission ID or grade ID does not exist.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Submission or grade not found.\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "An error occurred while removing the grade from the submission. This indicates a server-side issue that prevented the operation from completing successfully.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"An error occurred while removing the grade from the submission.\"}")
                    )
            )
    })
    @DeleteMapping("/{submissionId}/grades/{gradeId}")
    @PreAuthorize("hasAuthority('REMOVE_SUBMISSION_GRADES') and @access.canManageSubmission(#submissionId, principal)")
    public ResponseEntity<Void> removeGradeFromSubmission(@PathVariable Long submissionId,
                                                          @PathVariable Long gradeId) {
        submissionService.removeGradeFromSubmission(submissionId, gradeId);
        return ResponseEntity.noContent().build(); // 204 No Content response on successful removal
    }

    @PutMapping("/{submissionId}/{gradeValue}")
    @PreAuthorize("hasAuthority('EVALUATE_SUBMISSIONS') and @access.canManageSubmission(#submissionId, principal)")
    public ResponseEntity<Void> manualReviewSubmission(@PathVariable Long submissionId, @PathVariable Double gradeValue,
                                                       @Valid @RequestBody ManualReviewRequest request) {
        Optional<Grade> grade = submissionService.manualReviewSubmission(submissionId, gradeValue, request.getComments());
        if (grade.isPresent()) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.badRequest().build();
        }
    }
}