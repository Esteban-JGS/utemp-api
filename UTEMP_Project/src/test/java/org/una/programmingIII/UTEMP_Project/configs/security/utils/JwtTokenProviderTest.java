package org.una.programmingIII.UTEMP_Project.configs.security.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.una.programmingIII.UTEMP_Project.dtos.TokenDTOs.TokenResponseDTO;
import org.una.programmingIII.UTEMP_Project.exceptions.InvalidTokenException;
import org.una.programmingIII.UTEMP_Project.exceptions.TokenExpiredException;
import org.una.programmingIII.UTEMP_Project.models.User;
import org.una.programmingIII.UTEMP_Project.models.UserPermission;
import org.una.programmingIII.UTEMP_Project.repositories.UserRepository;
import org.una.programmingIII.UTEMP_Project.security.utils.JwtTokenProvider;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long";
    private static final long ACCESS_TOKEN_VALIDITY = 3600000; // 1 hour
    private static final long REFRESH_TOKEN_VALIDITY = 86400000; // 1 day

    @Mock
    private UserRepository userRepository;

    private JwtTokenProvider jwtTokenProvider;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(userRepository, SECRET, ACCESS_TOKEN_VALIDITY, REFRESH_TOKEN_VALIDITY);

        testUser = new User();
        testUser.setId(1L);
        testUser.setIdentificationNumber("000000000");
        testUser.setName("yo");
        testUser.setEmail("yo@example.com");
        List<UserPermission> permissions = new ArrayList<>();
        permissions.add(UserPermission.MANAGE_USERS);
        testUser.setPermissions(permissions);
    }

    @Test
    void testWeakSecretIsRejectedAtStartup() {
        assertThrows(WeakKeyException.class,
                () -> new JwtTokenProvider(userRepository, "too-short-secret", ACCESS_TOKEN_VALIDITY, REFRESH_TOKEN_VALIDITY));
    }

    @Test
    void testRefreshTokensUserNotFound() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(testUser);

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.empty());

        InvalidTokenException exception = assertThrows(InvalidTokenException.class,
                () -> jwtTokenProvider.refreshTokens(refreshToken));

        assertEquals("User not found for the given ID: " + testUser.getId(), exception.getMessage());
        verify(userRepository).findById(testUser.getId());
    }

    @Test
    void testRefreshTokens() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(testUser);

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        TokenResponseDTO tokenResponse = jwtTokenProvider.refreshTokens(refreshToken);

        assertNotNull(tokenResponse);
        assertTrue(jwtTokenProvider.validateToken(tokenResponse.getAccessToken(), testUser.getIdentificationNumber()));
        assertNotNull(tokenResponse.getRefreshToken());
    }

    @Test
    void testAccessTokenCannotBeUsedAsRefreshToken() {
        String accessToken = jwtTokenProvider.generateAccessToken(testUser);

        assertThrows(InvalidTokenException.class, () -> jwtTokenProvider.refreshTokens(accessToken));
    }

    @Test
    void testRefreshTokenCannotBeUsedAsAccessToken() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(testUser);

        assertFalse(jwtTokenProvider.validateToken(refreshToken, testUser.getIdentificationNumber()));
    }

    @Test
    void testValidateToken() {
        String token = jwtTokenProvider.generateAccessToken(testUser);

        assertTrue(jwtTokenProvider.validateToken(token, testUser.getIdentificationNumber()));
        assertFalse(jwtTokenProvider.validateToken(token, "someone-else"));
        assertEquals(testUser.getId(), jwtTokenProvider.getIdFromToken(token));
        assertEquals(testUser.getPermissions(), jwtTokenProvider.getPermissionsFromToken(token));
    }

    @Test
    void testTokenSignedWithAnotherKeyIsRejected() {
        String forgedToken = Jwts.builder()
                .subject(testUser.getIdentificationNumber())
                .claim("type", "access")
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_VALIDITY))
                .signWith(Keys.hmacShaKeyFor("another-secret-key-that-is-32-bytes-or-more".getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThrows(InvalidTokenException.class,
                () -> jwtTokenProvider.validateToken(forgedToken, testUser.getIdentificationNumber()));
    }

    @Test
    void testExpiredTokenIsRejected() {
        String expiredToken = Jwts.builder()
                .subject(testUser.getIdentificationNumber())
                .claim("type", "refresh")
                .claim("id", testUser.getId())
                .issuedAt(new Date(System.currentTimeMillis() - 600000))
                .expiration(new Date(System.currentTimeMillis() - 300000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertTrue(jwtTokenProvider.isTokenExpired(expiredToken));
        assertThrows(TokenExpiredException.class, () -> jwtTokenProvider.refreshTokens(expiredToken));
    }
}
