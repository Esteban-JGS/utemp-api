package org.una.programmingIII.UTEMP_Project.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.una.programmingIII.UTEMP_Project.controllers.request.AuthRequest;
import org.una.programmingIII.UTEMP_Project.controllers.responses.ApiResponse;
import org.una.programmingIII.UTEMP_Project.controllers.responses.TokenResponse;
import org.una.programmingIII.UTEMP_Project.dtos.TokenDTOs.TokenRequestDTO;
import org.una.programmingIII.UTEMP_Project.dtos.TokenDTOs.TokenResponseDTO;
import org.una.programmingIII.UTEMP_Project.exceptions.InvalidTokenException;
import org.una.programmingIII.UTEMP_Project.models.User;
import org.una.programmingIII.UTEMP_Project.models.UserRole;
import org.una.programmingIII.UTEMP_Project.security.utils.JwtTokenProvider;
import org.una.programmingIII.UTEMP_Project.services.CustomUserDetails;
import org.una.programmingIII.UTEMP_Project.services.CustomUserDetailsService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @InjectMocks
    private AuthController authController;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CustomUserDetailsService userDetailsService;

    private AuthRequest authRequest;
    private User user;

    @BeforeEach
    void setUp() {
        authRequest = new AuthRequest();
        authRequest.setIdentificationNumber("12345");
        authRequest.setPassword("password");

        user = new User();
        user.setId(7L);
        user.setName("Ana");
        user.setIdentificationNumber("12345");
        user.setPassword("$2a$10$hash");
        user.setRole(UserRole.STUDENT);
    }

    @Test
    void loginReturnsAccessAndRefreshTokens() {
        when(userDetailsService.loadUserByUsername("12345")).thenReturn(new CustomUserDetails(user));
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(user)).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenValidity()).thenReturn(900_000L);

        ResponseEntity<ApiResponse<TokenResponse>> response = authController.createAuthenticationToken(authRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TokenResponse body = response.getBody().getData();
        assertEquals("access-token", body.getToken());
        assertEquals("refresh-token", body.getRefreshToken());
        assertEquals("Bearer", body.getTokenType());
        assertEquals(900, body.getExpiresIn());
        assertEquals(7L, body.getUser().getId());
        assertNull(body.getUser().getPassword(), "the password hash must never be returned");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void loginWithBadCredentialsPropagatesTheExceptionAndIssuesNoToken() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // GlobalExceptionHandler maps it to 401
        assertThrows(BadCredentialsException.class, () -> authController.createAuthenticationToken(authRequest));

        verify(userDetailsService, never()).loadUserByUsername(any());
        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }

    @Test
    void refreshDelegatesToTheTokenProvider() {
        TokenResponseDTO tokens = new TokenResponseDTO("new-access", "new-refresh");
        when(jwtTokenProvider.refreshTokens("refresh-token")).thenReturn(tokens);

        ResponseEntity<ApiResponse<TokenResponseDTO>> response =
                authController.refreshToken(new TokenRequestDTO("refresh-token"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(tokens, response.getBody().getData());
    }

    @Test
    void refreshWithoutTokenIsRejected() {
        assertThrows(InvalidTokenException.class, () -> authController.refreshToken(new TokenRequestDTO(" ")));
        verifyNoInteractions(jwtTokenProvider);
    }
}
