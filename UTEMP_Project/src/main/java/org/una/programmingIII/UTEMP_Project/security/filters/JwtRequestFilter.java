package org.una.programmingIII.UTEMP_Project.security.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.una.programmingIII.UTEMP_Project.exceptions.InvalidTokenException;
import org.una.programmingIII.UTEMP_Project.exceptions.TokenExpiredException;
import org.una.programmingIII.UTEMP_Project.security.utils.jwtTokenProvider.JwtTokenProvider;
import org.una.programmingIII.UTEMP_Project.services.CustomUserDetails;
import org.una.programmingIII.UTEMP_Project.services.CustomUserDetailsService;

import java.io.IOException;

public class JwtRequestFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtRequestFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    public JwtRequestFilter(JwtTokenProvider jwtTokenProviderService, CustomUserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProviderService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public void doFilterInternal(@NonNull HttpServletRequest request,
                                 @NonNull HttpServletResponse response,
                                 @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);
        if (token == null) {
            logger.debug("Authorization token is missing");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String identificationNumber = jwtTokenProvider.getIdentificationNumberFromToken(token);
            if (identificationNumber != null && jwtTokenProvider.validateToken(token, identificationNumber)) {
                CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserByUsername(identificationNumber);
                // Tokens issued before the account was suspended or deactivated stop working right away
                if (!userDetails.isEnabled()) {
                    handleException(response, "Account is not active");
                    return;
                }
                authenticateUser(userDetails);
            } else {
                handleException(response, "Invalid JWT Token");
                return;
            }
        } catch (TokenExpiredException e) {
            handleException(response, "Token has expired", e);
            return;
        } catch (InvalidTokenException e) {
            handleException(response, "Invalid JWT token", e);
            return;
        } catch (Exception e) {
            handleException(response, "Authentication failed", e);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateUser(CustomUserDetails customUserDetails) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(customUserDetails, null, customUserDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        logger.debug("User {} authenticated successfully", customUserDetails.getUsername());
    }

    private String extractToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return null;
    }

    private void handleException(HttpServletResponse response, String message) throws IOException {
        handleException(response, message, null);
    }

    private void handleException(HttpServletResponse response, String message, Exception e) throws IOException {
        if (!response.isCommitted()) {
            logger.error("{}: {}", message, e != null ? e.getMessage() : "");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, message);
        }
    }
}