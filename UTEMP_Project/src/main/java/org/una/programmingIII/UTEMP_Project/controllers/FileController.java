package org.una.programmingIII.UTEMP_Project.controllers;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.una.programmingIII.UTEMP_Project.dtos.FileMetadatumDTO;
import org.una.programmingIII.UTEMP_Project.services.CustomUserDetails;
import org.una.programmingIII.UTEMP_Project.services.file.FileService;
import org.una.programmingIII.UTEMP_Project.services.file.FileService.StoredFile;

import java.nio.charset.StandardCharsets;

/**
 * Upload (in chunks) and download of submission files.
 * Ownership is checked in {@link FileService}: only the owner, the course teacher or an admin can read a file,
 * and only the owner or an admin can modify it. Errors are mapped to HTTP responses by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/utemp/files")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    private final FileService fileService;

    @Operation(summary = "Create file metadata", description = "Registers a file before its chunks are uploaded.")
    @PostMapping
    public ResponseEntity<FileMetadatumDTO> createFileMetadata(@RequestBody FileMetadatumDTO fileDTO,
                                                               @AuthenticationPrincipal CustomUserDetails principal) {
        FileMetadatumDTO createdMetadata = fileService.createNewFileMetadata(fileDTO, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(createdMetadata);
    }

    @Operation(summary = "Get file metadata")
    @GetMapping("/{id}")
    public ResponseEntity<FileMetadatumDTO> getFileMetadata(@PathVariable Long id,
                                                            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(fileService.getFileMetadatumById(id, principal.getUser()));
    }

    @Operation(summary = "Delete a file and its metadata")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id,
                                           @AuthenticationPrincipal CustomUserDetails principal) {
        fileService.deleteFile(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Upload a file chunk", description = "Chunks must have the same size except the last one.")
    @PostMapping("/receive-chunk")
    public ResponseEntity<Void> receiveFileChunk(@RequestBody FileMetadatumDTO fileDTO,
                                                 @AuthenticationPrincipal CustomUserDetails principal) {
        fileService.receiveFileChunk(fileDTO, principal.getUser());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Download a file")
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id,
                                                 @AuthenticationPrincipal CustomUserDetails principal) {
        StoredFile file = fileService.getFileForDownload(id, principal.getUser());
        logger.info("Downloading file with ID {} ({} bytes)", id, file.size());

        // ContentDisposition encodes the original name safely (no header injection through the file name)
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();

        // Written on the request thread: streaming from a second thread raced with the security header writer
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(file.size())
                .body(new FileSystemResource(file.path()));
    }
}
