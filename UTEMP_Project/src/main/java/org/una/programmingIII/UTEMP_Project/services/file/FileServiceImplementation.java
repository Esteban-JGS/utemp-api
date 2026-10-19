package org.una.programmingIII.UTEMP_Project.services.file;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.una.programmingIII.UTEMP_Project.dtos.FileMetadatumDTO;
import org.una.programmingIII.UTEMP_Project.dtos.UserDTO;
import org.una.programmingIII.UTEMP_Project.exceptions.FileServiceException;
import org.una.programmingIII.UTEMP_Project.exceptions.InvalidDataException;
import org.una.programmingIII.UTEMP_Project.exceptions.ResourceNotFoundException;
import org.una.programmingIII.UTEMP_Project.exceptions.UserNotFoundException;
import org.una.programmingIII.UTEMP_Project.models.FileMetadatum;
import org.una.programmingIII.UTEMP_Project.models.Submission;
import org.una.programmingIII.UTEMP_Project.models.User;
import org.una.programmingIII.UTEMP_Project.models.UserRole;
import org.una.programmingIII.UTEMP_Project.repositories.FileMetadatumRepository;
import org.una.programmingIII.UTEMP_Project.repositories.SubmissionRepository;
import org.una.programmingIII.UTEMP_Project.repositories.UserRepository;
import org.una.programmingIII.UTEMP_Project.services.autoReview.SubmissionFileUploadedEvent;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileServiceImplementation implements FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileServiceImplementation.class);
    private static final String UNKNOWN = "unknown";

    private final FileMetadatumRepository fileMetadatumRepository;
    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Path basePath;
    private final DataSize maxFileSize;

    public FileServiceImplementation(FileMetadatumRepository fileMetadatumRepository,
                                     SubmissionRepository submissionRepository,
                                     UserRepository userRepository,
                                     ApplicationEventPublisher eventPublisher,
                                     @Value("${file.storage.base-path}") String basePath,
                                     @Value("${file.storage.max-file-size}") DataSize maxFileSize) {
        this.fileMetadatumRepository = fileMetadatumRepository;
        this.userRepository = userRepository;
        this.submissionRepository = submissionRepository;
        this.eventPublisher = eventPublisher;
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
    }

    @PostConstruct
    void initializeStoragePath() throws IOException {
        Files.createDirectories(basePath);
        logger.info("File storage path: {}", basePath);
    }

    @Override
    @Transactional
    public FileMetadatumDTO createNewFileMetadata(FileMetadatumDTO fileDTO, User requester) {
        Long studentId = fileDTO.getStudent() != null ? fileDTO.getStudent().getId() : null;
        Long submissionId = fileDTO.getSubmission() != null ? fileDTO.getSubmission().getId() : null;
        if (studentId == null || submissionId == null) {
            throw new InvalidDataException("student and submission are required");
        }
        long fileSize = fileDTO.getFileSize() != null ? fileDTO.getFileSize() : 0L;
        if (fileSize <= 0 || fileSize > maxFileSize.toBytes()) {
            throw new InvalidDataException("file size must be between 1 byte and " + maxFileSize);
        }
        if (!isAdmin(requester) && !studentId.equals(requester.getId())) {
            throw new AccessDeniedException("Users can only upload files as themselves");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new UserNotFoundException("User with ID " + studentId + " not found"));
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId));
        if (!isAdmin(requester) && !isStudentOf(submission, requester)) {
            throw new AccessDeniedException("Users can only upload files to their own submissions");
        }

        FileMetadatum newFile = FileMetadatum.builder()
                .submission(submission)
                .student(student)
                .fileName(sanitizeFileName(fileDTO.getFileName()))
                .fileType(fileDTO.getFileType() != null ? fileDTO.getFileType() : UNKNOWN)
                .fileSize(fileSize)
                // The name on disk is generated here; the user-supplied name is only kept as metadata
                .storagePath(UUID.randomUUID().toString())
                .build();

        logger.info("Metadata created for file {}", newFile.getStoragePath());
        return metadataToDto(fileMetadatumRepository.save(newFile));
    }

    @Override
    @Transactional(readOnly = true)
    public FileMetadatumDTO getFileMetadatumById(Long id, User requester) {
        return metadataToDto(findReadableFile(id, requester));
    }

    @Override
    @Transactional(readOnly = true)
    public StoredFile getFileForDownload(Long id, User requester) {
        FileMetadatum metadata = findReadableFile(id, requester);
        Path path = resolveStoragePath(metadata);
        if (!Files.exists(path)) {
            logger.error("File with ID {} does not exist on disk", id);
            throw new ResourceNotFoundException("File", id);
        }
        try {
            return new StoredFile(metadata.getFileName(), path, Files.size(path));
        } catch (IOException e) {
            throw new FileServiceException("Could not read file with ID " + id);
        }
    }

    @Override
    @Transactional
    public void deleteFile(Long id, User requester) {
        FileMetadatum metadata = findWritableFile(id, requester);
        try {
            Files.deleteIfExists(resolveStoragePath(metadata));
        } catch (IOException e) {
            throw new FileServiceException("Could not delete file with ID " + id);
        }
        fileMetadatumRepository.delete(metadata);
        logger.info("File with ID {} deleted", id);
    }

    @Override
    @Transactional
    public void receiveFileChunk(FileMetadatumDTO fileDTO, User requester) {
        FileMetadatum metadata = findWritableFile(fileDTO.getId(), requester);
        byte[] chunk = fileDTO.getFileChunk();
        int chunkIndex = fileDTO.getChunkIndex();
        int totalChunks = fileDTO.getTotalChunks();
        if (chunk == null || chunk.length == 0 || totalChunks <= 0 || chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new InvalidDataException("invalid file chunk");
        }

        // All chunks have the same size except the last one, which ends exactly at the declared file size
        boolean isLastChunk = chunkIndex == totalChunks - 1;
        long offset = isLastChunk ? metadata.getFileSize() - chunk.length : (long) chunkIndex * chunk.length;
        if (offset < 0 || offset + chunk.length > metadata.getFileSize()) {
            throw new InvalidDataException("chunk does not fit in the declared file size");
        }

        Path path = resolveStoragePath(metadata);
        // Chunks must arrive in order: each one starts where the stored part ends, so none is skipped or written twice
        if (offset != storedBytes(path)) {
            throw new InvalidDataException("chunk " + chunkIndex + " is out of order");
        }
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
            raf.seek(offset);
            raf.write(chunk);
        } catch (IOException e) {
            logger.error("Error saving chunk {} of file {}: {}", chunkIndex, metadata.getId(), e.getMessage());
            throw new FileServiceException("Could not save chunk " + chunkIndex);
        }
        logger.debug("Chunk {} of {} saved for file {}", chunkIndex + 1, totalChunks, metadata.getId());

        if (isLastChunk) {
            finalizeUpload(metadata);
        }
    }

    private long storedBytes(Path path) {
        try {
            return Files.exists(path) ? Files.size(path) : 0L;
        } catch (IOException e) {
            throw new FileServiceException("Could not read the stored part of " + path.getFileName());
        }
    }

    private void finalizeUpload(FileMetadatum metadata) {
        Submission submission = metadata.getSubmission();
        fileMetadatumRepository.save(metadata);
        if (submission != null) {
            submission.setFileName(metadata.getFileName());
            submissionRepository.save(submission);
            // The automatic review listens to this event and runs after this transaction commits
            eventPublisher.publishEvent(new SubmissionFileUploadedEvent(submission.getId()));
        }
        logger.info("Upload of file {} completed", metadata.getId());
    }

    private FileMetadatum findReadableFile(Long id, User requester) {
        FileMetadatum metadata = findFile(id);
        if (!isAdmin(requester) && !fileMetadatumRepository.isOwnerOrCourseTeacher(id, requester.getId())) {
            throw new AccessDeniedException("User " + requester.getId() + " cannot read file " + id);
        }
        return metadata;
    }

    private FileMetadatum findWritableFile(Long id, User requester) {
        FileMetadatum metadata = findFile(id);
        if (!isAdmin(requester) && !fileMetadatumRepository.isOwner(id, requester.getId())) {
            throw new AccessDeniedException("User " + requester.getId() + " cannot modify file " + id);
        }
        return metadata;
    }

    private FileMetadatum findFile(Long id) {
        if (id == null) {
            throw new InvalidDataException("file id is required");
        }
        return fileMetadatumRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File", id));
    }

    private Path resolveStoragePath(FileMetadatum metadata) {
        Path path = basePath.resolve(metadata.getStoragePath()).normalize();
        // Defense in depth: never touch anything outside the storage directory
        if (!path.startsWith(basePath)) {
            throw new AccessDeniedException("Invalid storage path for file " + metadata.getId());
        }
        return path;
    }

    // Keeps only the last path segment, e.g. "../../notes.txt" -> "notes.txt"
    static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return UNKNOWN;
        }
        String baseName = fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1).strip();
        return baseName.isEmpty() || baseName.equals("..") || baseName.equals(".") ? UNKNOWN : baseName;
    }

    private boolean isAdmin(User user) {
        return user.getRole() == UserRole.ADMIN;
    }

    private boolean isStudentOf(Submission submission, User user) {
        return submission.getStudent() != null && user.getId().equals(submission.getStudent().getId());
    }

    private FileMetadatumDTO metadataToDto(FileMetadatum fileMetadatum) {
        // The storage path is an internal detail and is not exposed to clients
        // student_id is nullable in the schema, so older rows may not have one
        User student = fileMetadatum.getStudent();
        UserDTO studentDTO = student != null ? UserDTO.builder().id(student.getId()).name(student.getName()).build() : null;
        return FileMetadatumDTO.builder()
                .id(fileMetadatum.getId())
                .student(studentDTO)
                .fileName(fileMetadatum.getFileName())
                .fileType(fileMetadatum.getFileType())
                .fileSize(fileMetadatum.getFileSize())
                .createdAt(fileMetadatum.getCreatedAt())
                .lastUpdate(fileMetadatum.getLastUpdate())
                .build();
    }
}
