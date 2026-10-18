package org.una.programmingIII.UTEMP_Project.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.una.programmingIII.UTEMP_Project.controllers.request.AuthRequest;
import org.una.programmingIII.UTEMP_Project.controllers.responses.ApiResponse;
import org.una.programmingIII.UTEMP_Project.controllers.responses.TokenResponse;
import org.una.programmingIII.UTEMP_Project.dtos.TokenDTOs.TokenRequestDTO;
import org.una.programmingIII.UTEMP_Project.dtos.TokenDTOs.TokenResponseDTO;
import org.una.programmingIII.UTEMP_Project.dtos.UserDTO;
import org.una.programmingIII.UTEMP_Project.exceptions.InvalidTokenException;
import org.una.programmingIII.UTEMP_Project.models.User;
import org.una.programmingIII.UTEMP_Project.security.utils.JwtTokenProvider;
import org.una.programmingIII.UTEMP_Project.services.CustomUserDetails;
import org.una.programmingIII.UTEMP_Project.services.CustomUserDetailsService;

@RestController
@RequestMapping("/utemp/auth")
public class AuthController {

    private static final String TOKEN_TYPE = "Bearer";

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final AuthenticationManager authenticationManager;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider, CustomUserDetailsService userDetailsService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Operation(
            summary = "Authenticate User",
            description = "Validates user credentials and returns a short-lived access token and a refresh token."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authentication successful. Returns the tokens and user details."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials. Authentication failed.")
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> createAuthenticationToken(@Valid @RequestBody AuthRequest authRequest) {
        // Bad credentials raise an AuthenticationException, which GlobalExceptionHandler turns into a 401
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(authRequest.getIdentificationNumber(), authRequest.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserByUsername(authRequest.getIdentificationNumber());
        User user = userDetails.getUser();

        TokenResponse tokenResponse = TokenResponse.builder()
                .token(jwtTokenProvider.generateAccessToken(user))
                .refreshToken(jwtTokenProvider.generateRefreshToken(user))
                .tokenType(TOKEN_TYPE)
                .expiresIn(jwtTokenProvider.getAccessTokenValidity() / 1000)
                .user(toSummary(user))
                .build();
        return ResponseEntity.ok(new ApiResponse<>(tokenResponse));
    }

    @Operation(
            summary = "Refresh tokens",
            description = "Exchanges a valid refresh token for a new access token and a new refresh token."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "New tokens issued."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "The refresh token is invalid or expired.")
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponseDTO>> refreshToken(@RequestBody TokenRequestDTO request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new InvalidTokenException("Refresh token is required.");
        }
        return ResponseEntity.ok(new ApiResponse<>(jwtTokenProvider.refreshTokens(request.getRefreshToken())));
    }

    // Only the fields the client needs after login; mapping the whole entity graph (courses, enrollments...) is not needed here
    private UserDTO toSummary(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .identificationNumber(user.getIdentificationNumber())
                .role(user.getRole())
                .state(user.getState())
                .permissions(user.getPermissions())
                .createdAt(user.getCreatedAt())
                .lastUpdate(user.getLastUpdate())
                .build();
    }
}
