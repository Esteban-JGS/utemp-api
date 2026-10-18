package org.una.programmingIII.UTEMP_Project.dtos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AssignmentDTOTest {

    @Test
    void deadlineIsWrittenInUtc() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        AssignmentDTO assignment = AssignmentDTO.builder()
                .title("Final project")
                .deadline(Instant.parse("2026-12-01T23:59:00Z"))
                .build();

        String json = objectMapper.writeValueAsString(assignment);

        assertTrue(json.contains("\"deadline\":\"2026-12-01T23:59:00.000Z\""), json);
    }
}
