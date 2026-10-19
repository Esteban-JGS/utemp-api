package org.una.programmingIII.UTEMP_Project.controllers.responses;

import org.junit.jupiter.api.Test;
import org.una.programmingIII.UTEMP_Project.dtos.UserDTO;

import static org.junit.jupiter.api.Assertions.*;

class TokenResponseTest {

    @Test
    void testTokenResponseConstructor() {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(1L);
        userDTO.setIdentificationNumber("12345");
        userDTO.setName("John Doe");

        TokenResponse tokenResponse = TokenResponse.builder()
                .token("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                .tokenType("Bearer")
                .user(userDTO)
                .expiresIn(3600)
                .build();

        assertNotNull(tokenResponse.getToken(), "El token no debe ser nulo");
        assertNotNull(tokenResponse.getTokenType(), "El tipo de token no debe ser nulo");
        assertNotNull(tokenResponse.getUser(), "User should not be null");
        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", tokenResponse.getToken(), "El token no coincide");
        assertEquals("Bearer", tokenResponse.getTokenType(), "El tipo de token no coincide");
        assertEquals(3600, tokenResponse.getExpiresIn(), "El valor de 'expiresIn' no coincide");

        assertEquals("John Doe", tokenResponse.getUser().getName(), "User name does not match");
        assertEquals("12345", tokenResponse.getUser().getIdentificationNumber(), "User identification number does not match");
    }

    @Test
    void testTokenResponseNullFields() {
        TokenResponse tokenResponse = TokenResponse.builder()
                .token(null)
                .tokenType("Bearer")
                .user(null)
                .expiresIn(3600)
                .build();

        assertNull(tokenResponse.getToken(), "El token no debe ser nulo");
        assertNull(tokenResponse.getUser(), "User should be null");
    }

    @Test
    void testSettersAndGetters() {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(1L);
        userDTO.setIdentificationNumber("12345");
        userDTO.setName("John Doe");

        TokenResponse tokenResponse = TokenResponse.builder()
                .token("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                .tokenType("Bearer")
                .user(userDTO)
                .expiresIn(3600)
                .build();

        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", tokenResponse.getToken(), "El token no coincide");
        assertEquals("Bearer", tokenResponse.getTokenType(), "El tipo de token no coincide");
        assertEquals(3600, tokenResponse.getExpiresIn(), "El valor de 'expiresIn' no coincide");
        assertNotNull(tokenResponse.getUser(), "User should not be null");
        assertEquals("John Doe", tokenResponse.getUser().getName(), "User name does not match");
    }



    @Test
    void testToStringDoesNotLeakTokens() {
        TokenResponse tokenResponse = TokenResponse.builder()
                .token("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                .refreshToken("refresh-token-value")
                .tokenType("Bearer")
                .expiresIn(3600)
                .build();

        // Responses may end up in logs, so toString must not include the tokens
        String text = tokenResponse.toString();
        assertFalse(text.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"), "toString must not expose the access token");
        assertFalse(text.contains("refresh-token-value"), "toString must not expose the refresh token");
    }
}
