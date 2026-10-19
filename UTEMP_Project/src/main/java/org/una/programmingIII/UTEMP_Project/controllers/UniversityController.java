package org.una.programmingIII.UTEMP_Project.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.una.programmingIII.UTEMP_Project.dtos.FacultyDTO;
import org.una.programmingIII.UTEMP_Project.dtos.UniversityDTO;
import org.una.programmingIII.UTEMP_Project.exceptions.ResourceNotFoundException;
import org.una.programmingIII.UTEMP_Project.services.university.UniversityService;
import org.una.programmingIII.UTEMP_Project.utils.PageConverter;
import org.una.programmingIII.UTEMP_Project.utils.PageDTO;

import java.util.Optional;

@RestController
@RequestMapping("/utemp/universities")
public class UniversityController {

    private static final Logger logger = LoggerFactory.getLogger(UniversityController.class);
    private final UniversityService universityService;

    @Autowired
    public UniversityController(UniversityService universityService) {
        this.universityService = universityService;
    }

    @Operation(
            summary = "Get all universities",
            description = "Fetch a paginated list of all universities."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved list of universities.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UniversityDTO.class),
                            examples = @ExampleObject(value = "[{\"id\": 1, \"name\": \"National University\", \"location\": \"123 University Ave, City, Country\"}]")
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
    @PreAuthorize("hasAuthority('MANAGE_UNIVERSITIES')")
    public ResponseEntity<PageDTO<UniversityDTO>> getAllUniversities(Pageable pageable) {
        logger.info("Fetching all universities with pagination");
        Page<UniversityDTO> universitiesPage = universityService.getAllUniversities(pageable);

        PageDTO<UniversityDTO> universityDTOPage = PageConverter.convertPageToDTO(universitiesPage, universityDTO -> universityDTO);

        return ResponseEntity.ok(universityDTOPage);
    }


    @Operation(
            summary = "Get university by ID",
            description = "Fetch a university by its unique identifier."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved university.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UniversityDTO.class),
                            examples = @ExampleObject(value = "{\"id\": 1, \"name\": \"National University\", \"location\": \"123 University Ave, City, Country\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "University not found.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"University not found.\"}")
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
    @PreAuthorize("hasAuthority('MANAGE_UNIVERSITIES')")
    public ResponseEntity<UniversityDTO> getUniversityById(
            @Parameter(description = "ID of the university to retrieve", required = true) @PathVariable Long id) {
        logger.info("Fetching university with ID: {}", id);
        Optional<UniversityDTO> university = universityService.getUniversityById(id);
        return university.map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("University", id));
    }

    @Operation(
            summary = "Create a new university",
            description = "Create a new university with the provided details."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Successfully created university.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UniversityDTO.class),
                            examples = @ExampleObject(value = "{\"id\": 1, \"name\": \"National University\", \"location\": \"123 University Ave, City, Country\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid data provided.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Invalid data for university creation.\"}")
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
    @PreAuthorize("hasAuthority('MANAGE_UNIVERSITIES')")
    public ResponseEntity<UniversityDTO> createUniversity(
            @Parameter(description = "University data to create", required = true) @Valid @RequestBody UniversityDTO universityDTO) {
        logger.info("Creating new university: {}", universityDTO);
        UniversityDTO createdUniversity = universityService.createUniversity(universityDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUniversity);
    }

    @Operation(
            summary = "Update university",
            description = "Update an existing university identified by its ID."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully updated university.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UniversityDTO.class),
                            examples = @ExampleObject(value = "{\"id\": 1, \"name\": \"Updated University\", \"location\": \"456 New Ave, City, Country\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "University not found.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"University not found\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid data provided.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Invalid data for university update.\"}")
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
    @PreAuthorize("hasAuthority('MANAGE_UNIVERSITIES')")
    public ResponseEntity<UniversityDTO> updateUniversity(
            @Parameter(description = "ID of the university to update", required = true) @PathVariable Long id,
            @Valid @RequestBody UniversityDTO universityDTO) {
        logger.info("Updating university with ID: {}", id);
        Optional<UniversityDTO> updatedUniversity = universityService.updateUniversity(id, universityDTO);
        return updatedUniversity.map(ResponseEntity::ok)
                .orElseGet(() -> {
                    logger.warn("University with ID: {} not found for update", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @Operation(
            summary = "Delete university",
            description = "Delete a university identified by its ID."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Successfully deleted university."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "University not found.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"University not found\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid data provided.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Invalid data for university deletion.\"}")
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
    @PreAuthorize("hasAuthority('MANAGE_UNIVERSITIES')")
    public ResponseEntity<Void> deleteUniversity(
            @Parameter(description = "ID of the university to delete", required = true) @PathVariable Long id) {
        logger.info("Deleting university with ID: {}", id);
        universityService.deleteUniversity(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Add faculty to university",
            description = "Add a faculty to a university."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Successfully added faculty to university."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "University not found.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"University not found\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid data provided.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Invalid data for adding faculty.\"}")
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
    @PostMapping("/{universityId}/faculties")
    @PreAuthorize("hasAuthority('ADD_UNIVERSITY_FACULTIES')")
    public ResponseEntity<Void> addFacultyToUniversity(
            @Parameter(description = "ID of the university to which the faculty will be added", required = true) @PathVariable Long universityId,
            @Valid @RequestBody FacultyDTO facultyDTO) {
        logger.info("Adding faculty to university with ID: {}", universityId);
        universityService.addFacultyToUniversity(universityId, facultyDTO);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }


    @Operation(
            summary = "Remove faculty from university",
            description = "Remove a faculty from a university."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Successfully removed faculty from university."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Faculty or university not found.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"message\": \"Faculty or university not found\"}")
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
    @DeleteMapping("/{universityId}/faculties/{facultyId}")
    @PreAuthorize("hasAuthority('REMOVE_UNIVERSITY_FACULTIES')")
    public ResponseEntity<Void> removeFacultyFromUniversity(
            @Parameter(description = "ID of the university", required = true) @PathVariable Long universityId,
            @PathVariable Long facultyId) {
        logger.info("Removing faculty with ID: {} from university with ID: {}", facultyId, universityId);
        universityService.removeFacultyFromUniversity(universityId, facultyId);
        return ResponseEntity.noContent().build();
    }
}