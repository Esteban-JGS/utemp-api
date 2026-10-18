package org.una.programmingIII.UTEMP_Project.controllers.responses;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.una.programmingIII.UTEMP_Project.dtos.UserDTO;

@Getter
@Setter
@Builder
public class TokenResponse {
    @NotNull(message = "Token must not be null")
    private String token; //"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    @NotNull(message = "Refresh token must not be null")
    private String refreshToken;
    @NotNull(message = "Token type must not be null")
    private String tokenType; //"Bearer",
    @NotNull(message = "User must not be null")
    private UserDTO user;
    private long expiresIn; // seconds until the access token expires
}
