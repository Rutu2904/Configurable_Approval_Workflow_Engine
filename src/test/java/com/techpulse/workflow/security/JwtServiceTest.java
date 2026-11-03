package com.techpulse.workflow.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = Base64.getEncoder().encodeToString(
            "very_secret_key_that_is_at_least_32_bytes_long_1234567890".getBytes(StandardCharsets.UTF_8));
    private final long expirationMs = 60000;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secret, expirationMs);
    }

    @Test
    void generateAndValidateToken_andExtractClaims() {
        UserPrincipal principal = new UserPrincipal(42L, "alice", "pwd", Set.of("REQUESTER", "ADMIN"));
        String token = jwtService.generateToken(principal);

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtService.extractRoles(token)).containsExactlyInAnyOrder("REQUESTER", "ADMIN");
    }

    @Test
    void tamperedToken_isInvalid() {
        UserPrincipal principal = new UserPrincipal(42L, "alice", "pwd", Set.of("REQUESTER"));
        String token = jwtService.generateToken(principal);
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    void expiredToken_isInvalid() {
        JwtService shortLivedService = new JwtService(secret, -1000);
        UserPrincipal principal = new UserPrincipal(42L, "alice", "pwd", Set.of("REQUESTER"));
        String token = shortLivedService.generateToken(principal);

        assertThat(jwtService.isValid(token)).isFalse();
    }
}

