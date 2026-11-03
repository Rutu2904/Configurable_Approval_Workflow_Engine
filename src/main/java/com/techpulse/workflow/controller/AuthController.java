package com.techpulse.workflow.controller;

import com.techpulse.workflow.dto.LoginRequest;
import com.techpulse.workflow.dto.LoginResponse;
import com.techpulse.workflow.security.JwtService;
import com.techpulse.workflow.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.ArrayList;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));

            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            String token = jwtService.generateToken(principal);

            return ResponseEntity.ok(new LoginResponse(
                    token, principal.getUsername(), new ArrayList<>(principal.getRoles())));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).build();
        }
    }
}

