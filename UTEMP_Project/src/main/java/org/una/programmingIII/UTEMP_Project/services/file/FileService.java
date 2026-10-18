package org.una.programmingIII.UTEMP_Project.services.file;

import org.una.programmingIII.UTEMP_Project.dtos.FileMetadatumDTO;
import org.una.programmingIII.UTEMP_Project.models.User;

import java.nio.file.Path;

/**
 * Every operation receives the authenticated user and checks that they may access the file.
 * Reading is allowed to the owner, the teacher of the course and admins; writing only to the owner and admins.
 */
public interface FileService {

    FileMetadatumDTO createNewFileMetadata(FileMetadatumDTO fileDTO, User requester);

    FileMetadatumDTO getFileMetadatumById(Long id, User requester);

    StoredFile getFileForDownload(Long id, User requester);

    void deleteFile(Long fileId, User requester);

    void receiveFileChunk(FileMetadatumDTO fileDTO, User requester);

    record StoredFile(String fileName, Path path, long size) {
    }
}
