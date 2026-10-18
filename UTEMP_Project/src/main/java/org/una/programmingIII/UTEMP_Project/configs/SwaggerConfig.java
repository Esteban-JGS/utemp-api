package org.una.programmingIII.UTEMP_Project.configs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String UTEMP_TITLE = "University Task and Evaluation Management Platform (UTEMP) API";
    private static final String UTEMP_VERSION = "1.0";

    private static final String UTEMP_DESCRIPTION_TITLE = "The University Task and Evaluation Management Platform (UTEMP) API streamlines the management and review of university assignments and evaluations.";
    private static final String UTEMP_DESCRIPTION_FEATURES =
            "Key features include:\n" +
                    " - **University Management**: Facilitates registration and organization of institutional data.\n" +
                    " - **Faculty and Department Oversight**: Empowers administrators to manage academic divisions.\n" +
                    " - **Course Management**: Enables efficient creation and modification of courses.\n" +
                    " - **User Management**: Supports diverse user roles with granular permissions.\n" +
                    " - **Assignment and Submission Management**: Simplifies tracking and feedback on assignments.\n" +
                    " - **Advanced Automated Review**: Simulated AI grading that runs asynchronously.\n" +
                    " - **Secure Credential Verification**: Employs JWT for secure data protection.\n" +
                    " - **Real-time Notifications**: Offers alerts on deadlines and submissions.\n" +
                    " - **Pagination**: Paginated listings for every resource.\n" +
                    "Overall, the UTEMP API revolutionizes academic workflows by providing a user-friendly and secure system to enhance collaboration and educational outcomes.";

    private static final String CONTACT_NAME = "UTEMP team";
    private static final String CONTACT_EMAIL = "utempjen@gmail.com";
    private static final String CONTACT_URL = "https://github.com/Esteban-JGS/UTEMP_API_Projecto_Programming_III";

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";
    private static final String SECURITY_SCHEME_TYPE = "HTTP";
    private static final String SECURITY_SCHEME_SCHEME = "bearer";
    private static final String SECURITY_SCHEME_BEARER_FORMAT = "JWT";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(createApiInfo())
                .addSecurityItem(createSecurityRequirement())
                .components(createComponents());
    }

    private Info createApiInfo() {
        return new Info()
                .title(UTEMP_TITLE)
                .version(UTEMP_VERSION)
                .description(UTEMP_DESCRIPTION_TITLE + "\n\n" + UTEMP_DESCRIPTION_FEATURES)
                .contact(createContact());
    }

    private Contact createContact() {
        return new Contact()
                .name(CONTACT_NAME)
                .email(CONTACT_EMAIL)
                .url(CONTACT_URL);
    }

    private SecurityRequirement createSecurityRequirement() {
        return new SecurityRequirement().addList(SECURITY_SCHEME_NAME);
    }

    private Components createComponents() {
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.valueOf(SECURITY_SCHEME_TYPE))
                .scheme(SECURITY_SCHEME_SCHEME)
                .bearerFormat(SECURITY_SCHEME_BEARER_FORMAT);

        return new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, securityScheme);
    }
}