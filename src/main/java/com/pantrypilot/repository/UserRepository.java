package com.pantrypilot.repository;

import com.pantrypilot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);  // for login
    boolean existsByUsername(String username);       // for checking taken username
    boolean existsByEmail(String email);             // for checking taken email
    Optional<User> findByEmail(String email);
}
