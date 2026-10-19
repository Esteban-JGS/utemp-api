package org.una.programmingIII.UTEMP_Project.dtos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileMetadatumDTOTest {

    @Test
    void toStringLabelsEachField() {
        UserDTO student = new UserDTO();
        student.setId(7L);
        FileMetadatumDTO file = FileMetadatumDTO.builder()
                .id(3L).fileName("essay.txt").fileSize(42L).fileType("text/plain").student(student).build();

        assertEquals("FileMetadatumDTO{id=3, fileName='essay.txt', fileSize=42, fileType='text/plain', createdAt=null, "
                + "lastUpdate=null, studentId=7}", file.toString());
    }
}
