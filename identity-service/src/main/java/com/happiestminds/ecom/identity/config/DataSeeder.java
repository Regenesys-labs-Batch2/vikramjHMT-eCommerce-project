package com.happiestminds.ecom.identity.config;

import com.happiestminds.ecom.identity.domain.Role;
import com.happiestminds.ecom.identity.domain.User;
import com.happiestminds.ecom.identity.domain.UserProfile;
import com.happiestminds.ecom.identity.repository.RoleRepository;
import com.happiestminds.ecom.identity.repository.UserProfileRepository;
import com.happiestminds.ecom.identity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
public class DataSeeder implements CommandLineRunner {

    static final String ADMIN_EMAIL = "admin@ecom.test";

    private final RoleRepository roles;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final PasswordEncoder encoder;
    private final String adminPassword;

    public DataSeeder(RoleRepository roles, UserRepository users, UserProfileRepository profiles,
                      PasswordEncoder encoder, @Value("${ecom.seed.admin-password}") String adminPassword) {
        this.roles = roles;
        this.users = users;
        this.profiles = profiles;
        this.encoder = encoder;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Role customer = roles.findByName("CUSTOMER").orElseGet(() -> roles.save(new Role("CUSTOMER")));
        Role admin = roles.findByName("ADMIN").orElseGet(() -> roles.save(new Role("ADMIN")));
        if (!users.existsByEmail(ADMIN_EMAIL)) {
            User user = users.save(new User(ADMIN_EMAIL, encoder.encode(adminPassword), Set.of(admin, customer)));
            profiles.save(new UserProfile(user, "Platform Admin"));
        }
    }
}
