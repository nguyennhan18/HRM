package com.company.hrm.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.company.hrm.auth.config.BootstrapAdminProperties;
import com.company.hrm.auth.entity.Role;
import com.company.hrm.auth.entity.User;
import com.company.hrm.auth.repository.RoleRepository;
import com.company.hrm.auth.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminProvisionerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void should_create_admin_when_both_environment_values_are_configured() {
        Role adminRole = new Role("ADMIN");
        given(userRepository.existsByRoles_Code("ADMIN")).willReturn(false);
        given(userRepository.findByEmail("admin@example.com")).willReturn(Optional.empty());
        given(roleRepository.findByCode("ADMIN")).willReturn(Optional.of(adminRole));

        provisioner("admin@example.com", "at-least-ten-characters").provisionIfConfigured();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User createdUser = userCaptor.getValue();
        assertThat(createdUser.getEmail()).isEqualTo("admin@example.com");
        assertThat(passwordEncoder.matches("at-least-ten-characters", createdUser.getPasswordHash())).isTrue();
        assertThat(createdUser.getRoles()).containsExactly(adminRole);
    }

    @Test
    void should_not_create_admin_when_either_environment_value_is_missing() {
        provisioner("admin@example.com", "").provisionIfConfigured();
        provisioner("", "at-least-ten-characters").provisionIfConfigured();

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).existsByRoles_Code(any());
    }

    @Test
    void should_not_change_data_when_an_admin_already_exists() {
        given(userRepository.existsByRoles_Code("ADMIN")).willReturn(false, true);
        given(userRepository.findByEmail("admin@example.com")).willReturn(Optional.empty());
        given(roleRepository.findByCode("ADMIN")).willReturn(Optional.of(new Role("ADMIN")));
        BootstrapAdminProvisioner provisioner = provisioner("admin@example.com", "at-least-ten-characters");

        provisioner.provisionIfConfigured();
        provisioner.provisionIfConfigured();

        verify(userRepository, times(1)).save(any(User.class));
        verify(roleRepository, times(1)).findByCode(eq("ADMIN"));
    }

    private BootstrapAdminProvisioner provisioner(String email, String password) {
        return new BootstrapAdminProvisioner(
                new BootstrapAdminProperties(email, password),
                userRepository,
                roleRepository,
                passwordEncoder);
    }
}
