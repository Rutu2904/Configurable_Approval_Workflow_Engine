package com.techpulse.workflow.security;

import com.techpulse.workflow.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        // Base64 encoded 256-bit key
        jwtService = new JwtService("dGhpc0lzQVNlY3JldEtleUZvckpXVFRva2VuR2VuZXJhdGlvbjI1Ng==", 3600000);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword("pass");
        user.setRoles(Set.of("REQUESTER"));
        principal = new UserPrincipal(user);
    }

    @Test
    void generateAndValidate() {
        String token = jwtService.generateToken(principal);
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void extractUsername() {
        String token = jwtService.generateToken(principal);
        assertEquals("alice", jwtService.extractUsername(token));
    }

    @Test
    void extractUserId() {
        String token = jwtService.generateToken(principal);
        assertEquals(1L, jwtService.extractUserId(token));
    }

    @Test
    void extractRoles() {
        String token = jwtService.generateToken(principal);
        List<String> roles = jwtService.extractRoles(token);
        assertTrue(roles.contains("REQUESTER"));
    }

    @Test
    void tamperedToken_invalid() {
        String token = jwtService.generateToken(principal);
        String tampered = token + "xyz";
        assertFalse(jwtService.isTokenValid(tampered));
    }

    @Test
    void invalidToken_invalid() {
        assertFalse(jwtService.isTokenValid("not.a.token"));
    }
}

