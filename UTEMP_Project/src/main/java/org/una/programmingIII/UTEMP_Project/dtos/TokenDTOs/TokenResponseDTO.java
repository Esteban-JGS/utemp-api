package org.una.programmingIII.UTEMP_Project.dtos.TokenDTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponseDTO {
    // Same name as in the login response
    private String token;
    private String refreshToken;
}
