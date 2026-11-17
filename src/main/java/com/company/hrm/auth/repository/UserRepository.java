package com.company.hrm.auth.repository;

import com.company.hrm.auth.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByRoles_Code(String roleCode);

    Optional<User> findByEmail(String email);
}
