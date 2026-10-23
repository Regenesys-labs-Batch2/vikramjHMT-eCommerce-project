package com.happiestminds.ecom.identity.api;

import com.happiestminds.ecom.identity.api.dto.RegisterRequest;
import com.happiestminds.ecom.identity.api.dto.RegisterResponse;
import com.happiestminds.ecom.identity.api.dto.UpdateProfileRequest;
import com.happiestminds.ecom.identity.api.dto.UserResponse;
import com.happiestminds.ecom.identity.security.AuthContext;
import com.happiestminds.ecom.identity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @GetMapping("/me")
    public UserResponse me(Authentication auth) {
        return userService.getById(AuthContext.subject(auth));
    }

    @PutMapping("/me")
    public UserResponse updateMe(Authentication auth, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(AuthContext.subject(auth), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse byId(@PathVariable String id) {
        return userService.getById(id);
    }
}
