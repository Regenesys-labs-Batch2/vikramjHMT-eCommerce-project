package com.happiestminds.ecom.identity.service;

import com.happiestminds.ecom.identity.api.dto.RegisterRequest;
import com.happiestminds.ecom.identity.api.dto.RegisterResponse;
import com.happiestminds.ecom.identity.domain.Role;
import com.happiestminds.ecom.identity.domain.User;
import com.happiestminds.ecom.identity.repository.RoleRepository;
import com.happiestminds.ecom.identity.repository.UserProfileRepository;
import com.happiestminds.ecom.identity.repository.UserRepository;
import com.happiestminds.ecom.identity.service.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository users;
    @Mock
    UserProfileRepository profiles;
    @Mock
    RoleRepository roles;

    UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(users, profiles, roles, new BCryptPasswordEncoder(4));
    }

    @Test
    @DisplayName("AC-IDN-01 R-IDN-01 rejects duplicate email")
    void shouldRejectDuplicateEmail_whenEmailExists() {
        when(users.existsByEmail("a@ecom.test")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("A@ecom.test", "password1", "A")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("R-IDN-02 R-IDN-04 stores a BCrypt hash and assigns only the CUSTOMER role")
    void shouldHashPasswordAndAssignCustomerRole() {
        when(users.existsByEmail("b@ecom.test")).thenReturn(false);
        when(roles.findByName("CUSTOMER")).thenReturn(Optional.of(new Role("CUSTOMER")));

        RegisterResponse response = service.register(new RegisterRequest("B@Ecom.test", "password1", "B"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("password1").startsWith("$2");
        assertThat(response.email()).isEqualTo("b@ecom.test");
        assertThat(response.roles()).containsExactly("CUSTOMER");
    }
}
