package com.happiestminds.ecom.payment.security;

import org.springframework.security.core.Authentication;

public final class AuthContext {

    private AuthContext() {
    }

    public static String subject(Authentication auth) {
        return auth.getName();
    }

    public static boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    public static boolean isAdminOrService(Authentication auth) {
        return hasRole(auth, "ADMIN") || hasRole(auth, "SERVICE");
    }
}
