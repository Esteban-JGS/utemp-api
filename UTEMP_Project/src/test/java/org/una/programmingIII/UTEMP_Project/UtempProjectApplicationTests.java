package org.una.programmingIII.UTEMP_Project;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UtempProjectApplicationTests {

    @Test
    void contextLoads() {
        // Starts the whole application against PostgreSQL: Flyway migrations run and Hibernate validates the schema
    }
}
