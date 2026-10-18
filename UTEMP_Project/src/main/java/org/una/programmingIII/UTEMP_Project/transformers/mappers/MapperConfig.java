package org.una.programmingIII.UTEMP_Project.transformers.mappers;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.una.programmingIII.UTEMP_Project.dtos.EnrollmentDTO;
import org.una.programmingIII.UTEMP_Project.models.Enrollment;

@Configuration
public class MapperConfig {

    /**
     * Maps through getters and setters. Deep copy is left off because it turned every java.time value
     * (createdAt, lastUpdate, deadline) into null, and reading private fields failed on some lazy-loaded graphs.
     */
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        // EnrollmentDTO also exposes courseId/studentId for clients; when converting back, the nested objects win
        modelMapper.emptyTypeMap(EnrollmentDTO.class, Enrollment.class)
                .addMappings(mapper -> {
                    mapper.map(dto -> dto.getCourse().getId(), (enrollment, id) -> enrollment.getCourse().setId((Long) id));
                    mapper.map(dto -> dto.getStudent().getId(), (enrollment, id) -> enrollment.getStudent().setId((Long) id));
                })
                .implicitMappings();
        return modelMapper;
    }
}
