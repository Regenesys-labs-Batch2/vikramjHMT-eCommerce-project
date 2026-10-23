package com.happiestminds.ecom.identity.service;

import com.happiestminds.ecom.identity.api.dto.LoginRequest;
import com.happiestminds.ecom.identity.api.dto.LoginResponse;
import com.happiestminds.ecom.identity.domain.Role;
import com.happiestminds.ecom.identity.domain.User;
import com.happiestminds.ecom.identity.domain.UserStatus;
import com.happiestminds.ecom.identity.repository.UserRepository;
import com.happiestminds.ecom.identity.security.JwtService;
import com.happiestminds.ecom.identity.service.exception.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwtService) {
        this.users = users;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        // One generic error: never reveal whether the email or the password was wrong.
        if (user == null || user.getStatus() != UserStatus.ACTIVE
                || !encoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password");
        }
        List<String> roleNames = user.getRoles().stream().map(Role::getName).sorted().toList();
        String token = jwtService.issue(user.getId(), user.getEmail(), roleNames);
        return new LoginResponse(token, "Bearer", jwtService.expirySeconds(), user.getId(), roleNames);
    }
}
