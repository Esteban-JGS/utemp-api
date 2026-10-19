package org.una.programmingIII.UTEMP_Project.configs.security.filters;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.una.programmingIII.UTEMP_Project.exceptions.InvalidTokenException;
import org.una.programmingIII.UTEMP_Project.exceptions.TokenExpiredException;
import org.una.programmingIII.UTEMP_Project.models.User;
import org.una.programmingIII.UTEMP_Project.models.UserState;
import org.una.programmingIII.UTEMP_Project.security.filters.JwtRequestFilter;
import org.una.programmingIII.UTEMP_Project.security.utils.jwtTokenProvider.JwtTokenProvider;
import org.una.programmingIII.UTEMP_Project.services.CustomUserDetails;
import org.una.programmingIII.UTEMP_Project.services.CustomUserDetailsService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtRequestFilterTest {

    private static final String TOKEN = "header.payload.signature";
    private static final String IDENTIFICATION = "300000001";

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private FilterChain filterChain;

    private JwtRequestFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new JwtRequestFilter(jwtTokenProvider, userDetailsService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestWithoutTokenContinuesUnauthenticated() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void validTokenAuthenticatesTheUser() throws Exception {
        givenValidTokenFor(userWithState(UserState.ACTIVE));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(IDENTIFICATION, SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void validTokenOfASuspendedUserIsRejected() throws Exception {
        givenValidTokenFor(userWithState(UserState.SUSPENDED));

        filter.doFilterInternal(request, response, filterChain);

        assertUnauthorized();
    }

    @Test
    void tokenThatFailsValidationIsRejected() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        when(jwtTokenProvider.getIdentificationNumberFromToken(TOKEN)).thenReturn(IDENTIFICATION);
        when(jwtTokenProvider.validateToken(TOKEN, IDENTIFICATION)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertUnauthorized();
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        when(jwtTokenProvider.getIdentificationNumberFromToken(TOKEN)).thenThrow(new TokenExpiredException("expired"));

        filter.doFilterInternal(request, response, filterChain);

        assertUnauthorized();
    }

    @Test
    void tokenWithInvalidSignatureIsRejected() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        when(jwtTokenProvider.getIdentificationNumberFromToken(TOKEN)).thenThrow(new InvalidTokenException("bad signature"));

        filter.doFilterInternal(request, response, filterChain);

        assertUnauthorized();
    }

    private User userWithState(UserState state) {
        User user = new User();
        user.setIdentificationNumber(IDENTIFICATION);
        user.setState(state);
        return user;
    }

    private void givenValidTokenFor(User user) {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
        when(jwtTokenProvider.getIdentificationNumberFromToken(TOKEN)).thenReturn(IDENTIFICATION);
        when(jwtTokenProvider.validateToken(TOKEN, IDENTIFICATION)).thenReturn(true);
        when(userDetailsService.loadUserByUsername(IDENTIFICATION)).thenReturn(new CustomUserDetails(user));
    }

    private void assertUnauthorized() throws Exception {
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"statusCode\":401"));
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
