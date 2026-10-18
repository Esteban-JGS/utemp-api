package org.una.programmingIII.UTEMP_Project.services.file;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.una.programmingIII.UTEMP_Project.dtos.FileMetadatumDTO;
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
    private static final String UNKNOWN = "desconocido";

    private final FileMetadatumRepository fileMetadatumRepository;
    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Path basePath;

    public FileServiceImplementation(FileMetadatumRepository fileMetadatumRepository,
                                     SubmissionRepository submissionRepository,
                                     UserRepository userRepository,
                                     ApplicationEventPublisher eventPublisher,
                                     @Value("${file.storage.base-path}") String basePath) {
        this.fileMetadatumRepository = fileMetadatumRepository;
        this.userRepository = userRepository;
        this.submissionRepository = submissionRepository;
        this.eventPublisher = eventPublisher;
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initializeStoragePath() throws IOException {
        Files.createDirectories(basePath);
        logger.info("Ruta de almacenamiento de archivos: {}", basePath);
    }

    @Override
    @Transactional
    public FileMetadatumDTO createNewFileMetadata(FileMetadatumDTO fileDTO, User requester) {
        Long studentId = fileDTO.getStudent() != null ? fileDTO.getStudent().getId() : null;
        Long submissionId = fileDTO.getSubmission() != null ? fileDTO.getSubmission().getId() : null;
        if (studentId == null || submissionId == null) {
            throw new InvalidDataException("student and submission are required");
        }
        if (!isAdmin(requester) && !studentId.equals(requester.getId())) {
            throw new AccessDeniedException("Users can only upload files as themselves");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new UserNotFoundException("Usuario con ID " + studentId + " no encontrado"));
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
                .fileSize(fileDTO.getFileSize() != null ? fileDTO.getFileSize() : 0L)
                // The name on disk is generated here; the user-supplied name is only kept as metadata
                .storagePath(UUID.randomUUID().toString())
                .build();

        logger.info("Metadatos creados para el archivo {}", newFile.getStoragePath());
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
            logger.error("El archivo con ID {} no existe en disco", id);
            throw new ResourceNotFoundException("File", id);
        }
        try {
            return new StoredFile(metadata.getFileName(), path, Files.size(path));
        } catch (IOException e) {
            throw new FileServiceException("No se pudo leer el archivo con ID " + id);
        }
    }

    @Override
    @Transactional
    public void deleteFile(Long id, User requester) {
        FileMetadatum metadata = findWritableFile(id, requester);
        try {
            Files.deleteIfExists(resolveStoragePath(metadata));
        } catch (IOException e) {
            throw new FileServiceException("No se pudo eliminar el archivo con ID " + id);
        }
        fileMetadatumRepository.delete(metadata);
        logger.info("Archivo con ID {} eliminado", id);
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
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
            raf.seek(offset);
            raf.write(chunk);
        } catch (IOException e) {
            logger.error("Error al guardar el fragmento {} del archivo {}: {}", chunkIndex, metadata.getId(), e.getMessage());
            throw new FileServiceException("No se pudo guardar el fragmento " + chunkIndex);
        }
        logger.debug("Fragmento {} de {} guardado para el archivo {}", chunkIndex + 1, totalChunks, metadata.getId());

        if (isLastChunk) {
            finalizeUpload(metadata);
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
        logger.info("Carga del archivo {} finalizada", metadata.getId());
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
        return FileMetadatumDTO.builder()
                .id(fileMetadatum.getId())
                .fileName(fileMetadatum.getFileName())
                .fileType(fileMetadatum.getFileType())
                .fileSize(fileMetadatum.getFileSize())
                .createdAt(fileMetadatum.getCreatedAt())
                .lastUpdate(fileMetadatum.getLastUpdate())
                .build();
    }
}
