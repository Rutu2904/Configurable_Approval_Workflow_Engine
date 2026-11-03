package com.techpulse.workflow.security;

import org.springframework.security.core.context.SecurityContextHolder;

// Helper to get the current logged-in user
public class CurrentUser {
    public static UserPrincipal get() {
        return (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}

