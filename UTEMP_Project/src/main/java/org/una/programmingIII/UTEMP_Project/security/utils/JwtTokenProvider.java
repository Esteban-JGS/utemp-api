package org.una.programmingIII.UTEMP_Project.security.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.una.programmingIII.UTEMP_Project.dtos.TokenDTOs.TokenResponseDTO;
import org.una.programmingIII.UTEMP_Project.exceptions.InvalidTokenException;
import org.una.programmingIII.UTEMP_Project.exceptions.TokenExpiredException;
import org.una.programmingIII.UTEMP_Project.models.User;
import org.una.programmingIII.UTEMP_Project.models.UserPermission;
import org.una.programmingIII.UTEMP_Project.models.UserState;
import org.una.programmingIII.UTEMP_Project.repositories.UserRepository;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN = "access";
    private static final String REFRESH_TOKEN = "refresh";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRepository userRepository;
    private final SecretKey signingKey;
    private final long accessTokenValidity;
    private final long refreshTokenValidity;

    public JwtTokenProvider(UserRepository userRepository,
                            @Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiration.access}") long accessTokenValidity,
                            @Value("${jwt.expiration.refresh}") long refreshTokenValidity) {
        this.userRepository = userRepository;
        // Throws WeakKeyException if the secret is shorter than 256 bits, so a weak key fails at startup
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = accessTokenValidity;
        this.refreshTokenValidity = refreshTokenValidity;
    }

    public String generateToken(User user, boolean isRefreshToken) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        long validityDuration = isRefreshToken ? refreshTokenValidity : accessTokenValidity;
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getIdentificationNumber())
                .claim(TOKEN_TYPE_CLAIM, isRefreshToken ? REFRESH_TOKEN : ACCESS_TOKEN)
                .claim("id", user.getId())
                .claim("name", user.getName())
                .claim("email", user.getEmail())
                .claim("permissions", serializePermissions(user.getPermissions()))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validityDuration))
                .signWith(signingKey)
                .compact();
    }

    public long getAccessTokenValidity() {
        return accessTokenValidity;
    }

    private String serializePermissions(List<UserPermission> permissions) {
        return handleJsonProcessing(() -> objectMapper.writeValueAsString(permissions), "Error serializing permissions.");
    }

    private <T> T handleJsonProcessing(JsonProcessingFunction<T> function, String errorMessage) {
        try {
            return function.apply();
        } catch (JsonProcessingException e) {
            logger.error(errorMessage, e);
            throw new InvalidTokenException(errorMessage);
        }
    }

    public String getIdentificationNumberFromToken(String token) {
        return parseClaims(token, Claims::getSubject);
    }

    public Long getIdFromToken(String token) {
        return parseClaims(token, claims -> claims.get("id", Long.class));
    }

    public List<UserPermission> getPermissionsFromToken(String token) {
        return parseClaims(token, claims -> handleJsonProcessing(
                () -> objectMapper.readValue(claims.get("permissions", String.class), new TypeReference<List<UserPermission>>() {
                }),
                "Invalid permissions in token."
        ));
    }

    /**
     * Validates an access token. Refresh tokens are rejected so they cannot be used to call the API.
     */
    public boolean validateToken(String token, String identificationNumber) {
        if (token == null || identificationNumber == null) {
            throw new IllegalArgumentException("Token and identification number cannot be null");
        }
        Claims claims = parseClaims(token, Function.identity());
        boolean isValid = identificationNumber.equals(claims.getSubject())
                && ACCESS_TOKEN.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
        logger.debug("Validating token for identification number {}: {}", identificationNumber, isValid);
        return isValid;
    }

    public boolean isTokenExpired(String token) {
        // parseClaims rejects expired tokens, so any token that parses is still valid
        try {
            parseClaims(token, Claims::getExpiration);
            return false;
        } catch (TokenExpiredException e) {
            return true;
        }
    }

    private <T> T parseClaims(String token, Function<Claims, T> claimsResolver) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .clockSkewSeconds(60)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claimsResolver.apply(claims);
        } catch (ExpiredJwtException e) {
            logger.warn("Token has expired: {}", e.getMessage());
            throw new TokenExpiredException("The token has expired.");
        } catch (JwtException | IllegalArgumentException e) {
            logger.warn("Error while parsing JWT token: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token.");
        }
    }

    public TokenResponseDTO refreshTokens(String refreshToken) {
        Claims claims = parseClaims(refreshToken, Function.identity());
        if (!REFRESH_TOKEN.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new InvalidTokenException("Invalid refresh token.");
        }

        Long id = claims.get("id", Long.class);
        if (id == null) {
            throw new InvalidTokenException("Invalid refresh token.");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new InvalidTokenException("User not found for the given ID: " + id));
        if (user.getState() != UserState.ACTIVE) {
            throw new InvalidTokenException("The account of user " + id + " is not active.");
        }

        logger.info("Tokens refreshed for user id {}", id);
        return new TokenResponseDTO(generateAccessToken(user), generateRefreshToken(user));
    }

    public String generateAccessToken(User user) {
        return generateToken(user, false);
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, true);
    }

    @FunctionalInterface
    private interface JsonProcessingFunction<T> {
        T apply() throws JsonProcessingException;
    }
}
