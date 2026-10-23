package com.happiestminds.ecom.identity.service;

import com.happiestminds.ecom.identity.api.dto.ProfileDto;
import com.happiestminds.ecom.identity.api.dto.RegisterRequest;
import com.happiestminds.ecom.identity.api.dto.RegisterResponse;
import com.happiestminds.ecom.identity.api.dto.UpdateProfileRequest;
import com.happiestminds.ecom.identity.api.dto.UserResponse;
import com.happiestminds.ecom.identity.domain.Role;
import com.happiestminds.ecom.identity.domain.User;
import com.happiestminds.ecom.identity.domain.UserProfile;
import com.happiestminds.ecom.identity.repository.RoleRepository;
import com.happiestminds.ecom.identity.repository.UserProfileRepository;
import com.happiestminds.ecom.identity.repository.UserRepository;
import com.happiestminds.ecom.identity.service.exception.ConflictException;
import com.happiestminds.ecom.identity.service.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, UserProfileRepository profiles, RoleRepository roles,
                       PasswordEncoder encoder) {
        this.users = users;
        this.profiles = profiles;
        this.roles = roles;
        this.encoder = encoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        Role customer = roles.findByName("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role is not seeded"));
        User user = new User(email, encoder.encode(request.password()), Set.of(customer));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
        profiles.save(new UserProfile(user, request.fullName().trim()));
        log.info("Registered user {}", user.getId());
        return new RegisterResponse(user.getId(), user.getEmail(), roleNames(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(String userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        return toResponse(user, profiles.findByUserId(userId).orElse(null));
    }

    @Transactional
    public UserResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        UserProfile profile = profiles.findByUserId(userId).orElseGet(() -> new UserProfile(user, request.fullName()));
        profile.update(request.fullName().trim(), request.phone(), request.addressLine1(), request.city(),
                request.postcode());
        return toResponse(user, profiles.save(profile));
    }

    private static ConflictException emailTaken() {
        return new ConflictException("EMAIL_ALREADY_EXISTS", "An account with this email already exists");
    }

    private static List<String> roleNames(User user) {
        return user.getRoles().stream().map(Role::getName).sorted().toList();
    }

    private static UserResponse toResponse(User user, UserProfile profile) {
        ProfileDto dto = profile == null ? null
                : new ProfileDto(profile.getFullName(), profile.getPhone(), profile.getAddressLine1(),
                profile.getCity(), profile.getPostcode());
        return new UserResponse(user.getId(), user.getEmail(), roleNames(user), dto);
    }
}
