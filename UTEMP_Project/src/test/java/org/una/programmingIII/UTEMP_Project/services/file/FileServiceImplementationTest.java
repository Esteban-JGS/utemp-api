package org.una.programmingIII.UTEMP_Project.services.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.una.programmingIII.UTEMP_Project.dtos.FileMetadatumDTO;
import org.una.programmingIII.UTEMP_Project.dtos.SubmissionDTO;
import org.una.programmingIII.UTEMP_Project.dtos.UserDTO;
import org.una.programmingIII.UTEMP_Project.exceptions.InvalidDataException;
import org.una.programmingIII.UTEMP_Project.models.FileMetadatum;
import org.una.programmingIII.UTEMP_Project.models.Submission;
import org.una.programmingIII.UTEMP_Project.models.User;
import org.una.programmingIII.UTEMP_Project.models.UserRole;
import org.una.programmingIII.UTEMP_Project.repositories.FileMetadatumRepository;
import org.una.programmingIII.UTEMP_Project.repositories.SubmissionRepository;
import org.una.programmingIII.UTEMP_Project.repositories.UserRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceImplementationTest {

    private static final long FILE_ID = 10L;

    @Mock
    private FileMetadatumRepository fileMetadatumRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private UserRepository userRepository;

    @TempDir
    private Path storageDir;

    private FileServiceImplementation fileService;
    private User owner;
    private User otherStudent;
    private User admin;

    @BeforeEach
    void setUp() throws IOException {
        fileService = new FileServiceImplementation(fileMetadatumRepository, submissionRepository, userRepository, storageDir.toString());
        fileService.initializeStoragePath();
        owner = user(1L, UserRole.STUDENT);
        otherStudent = user(2L, UserRole.STUDENT);
        admin = user(3L, UserRole.ADMIN);
    }

    @Test
    void createStoresTheFileUnderAGeneratedNameAndKeepsOnlyTheBaseName() {
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(submissionRepository.findById(5L)).thenReturn(Optional.of(new Submission()));
        when(fileMetadatumRepository.save(any(FileMetadatum.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fileService.createNewFileMetadata(uploadRequest(owner.getId(), "../../etc/passwd"), owner);

        ArgumentCaptor<FileMetadatum> saved = ArgumentCaptor.forClass(FileMetadatum.class);
        verify(fileMetadatumRepository).save(saved.capture());
        assertEquals("passwd", saved.getValue().getFileName());
        assertTrue(saved.getValue().getStoragePath().matches("[0-9a-f-]{36}"),
                "storage name must be a UUID, was " + saved.getValue().getStoragePath());
    }

    @Test
    void studentCannotCreateAFileOnBehalfOfAnotherStudent() {
        assertThrows(AccessDeniedException.class,
                () -> fileService.createNewFileMetadata(uploadRequest(owner.getId(), "notes.txt"), otherStudent));
        verify(fileMetadatumRepository, never()).save(any());
    }

    @Test
    void anotherStudentCannotDownloadTheFile() {
        when(fileMetadatumRepository.findById(FILE_ID)).thenReturn(Optional.of(storedFile("hello")));
        when(fileMetadatumRepository.isOwnerOrCourseTeacher(FILE_ID, otherStudent.getId())).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> fileService.getFileForDownload(FILE_ID, otherStudent));
    }

    @Test
    void ownerOrCourseTeacherCanDownloadTheFile() throws IOException {
        FileMetadatum metadata = storedFile("hello");
        Files.writeString(storageDir.resolve(metadata.getStoragePath()), "hello");
        when(fileMetadatumRepository.findById(FILE_ID)).thenReturn(Optional.of(metadata));
        when(fileMetadatumRepository.isOwnerOrCourseTeacher(FILE_ID, owner.getId())).thenReturn(true);

        FileService.StoredFile file = fileService.getFileForDownload(FILE_ID, owner);

        assertEquals("report.txt", file.fileName());
        assertEquals(5, file.size());
    }

    @Test
    void adminCanReadAnyFile() {
        when(fileMetadatumRepository.findById(FILE_ID)).thenReturn(Optional.of(storedFile("hello")));

        assertEquals("report.txt", fileService.getFileMetadatumById(FILE_ID, admin).getFileName());
        verify(fileMetadatumRepository, never()).isOwnerOrCourseTeacher(any(), any());
    }

    @Test
    void anotherStudentCannotWriteChunksIntoTheFile() {
        when(fileMetadatumRepository.findById(FILE_ID)).thenReturn(Optional.of(storedFile("hello")));
        when(fileMetadatumRepository.isOwner(FILE_ID, otherStudent.getId())).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> fileService.receiveFileChunk(chunk(0, 1, "HACK!"), otherStudent));
    }

    @Test
    void chunksAreWrittenAtTheirOffsets() throws IOException {
        FileMetadatum metadata = storedFile("abcdefghij");
        when(fileMetadatumRepository.findById(FILE_ID)).thenReturn(Optional.of(metadata));
        when(fileMetadatumRepository.isOwner(FILE_ID, owner.getId())).thenReturn(true);

        // Sent out of order on purpose: the offset depends on the index, not on arrival order
        fileService.receiveFileChunk(chunk(2, 3, "ij"), owner);
        fileService.receiveFileChunk(chunk(0, 3, "abcd"), owner);
        fileService.receiveFileChunk(chunk(1, 3, "efgh"), owner);

        assertEquals("abcdefghij", Files.readString(storageDir.resolve(metadata.getStoragePath())));
    }

    @Test
    void chunkLargerThanTheDeclaredFileIsRejected() {
        when(fileMetadatumRepository.findById(FILE_ID)).thenReturn(Optional.of(storedFile("abc")));
        when(fileMetadatumRepository.isOwner(FILE_ID, owner.getId())).thenReturn(true);

        assertThrows(InvalidDataException.class,
                () -> fileService.receiveFileChunk(chunk(0, 1, "much longer than three bytes"), owner));
    }

    @Test
    void storagePathOutsideTheStorageDirectoryIsNeverUsed() {
        FileMetadatum metadata = storedFile("hello");
        metadata.setStoragePath("../outside.txt");
        when(fileMetadatumRepository.findById(FILE_ID)).thenReturn(Optional.of(metadata));

        assertThrows(AccessDeniedException.class, () -> fileService.getFileForDownload(FILE_ID, admin));
    }

    @Test
    void sanitizeFileNameKeepsOnlyTheLastSegment() {
        assertEquals("notes.txt", FileServiceImplementation.sanitizeFileName("notes.txt"));
        assertEquals("passwd", FileServiceImplementation.sanitizeFileName("../../etc/passwd"));
        assertEquals("evil.exe", FileServiceImplementation.sanitizeFileName("..\\..\\evil.exe"));
        assertEquals("desconocido", FileServiceImplementation.sanitizeFileName(".."));
        assertEquals("desconocido", FileServiceImplementation.sanitizeFileName(null));
    }

    private static User user(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private FileMetadatum storedFile(String content) {
        return FileMetadatum.builder()
                .id(FILE_ID)
                .student(owner)
                .fileName("report.txt")
                .fileSize((long) content.length())
                .storagePath("2b1c7a8e-0d9f-4c55-9a43-5f4f1f2d9c11")
                .build();
    }

    private static FileMetadatumDTO uploadRequest(Long studentId, String fileName) {
        UserDTO student = new UserDTO();
        student.setId(studentId);
        SubmissionDTO submission = new SubmissionDTO();
        submission.setId(5L);
        return FileMetadatumDTO.builder()
                .student(student)
                .submission(submission)
                .fileName(fileName)
                .fileSize(100L)
                .build();
    }

    private static FileMetadatumDTO chunk(int index, int total, String content) {
        return FileMetadatumDTO.builder()
                .id(FILE_ID)
                .chunkIndex(index)
                .totalChunks(total)
                .fileChunk(content.getBytes(StandardCharsets.UTF_8))
                .build();
    }
}
