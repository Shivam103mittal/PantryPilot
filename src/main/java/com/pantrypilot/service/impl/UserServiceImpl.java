package com.pantrypilot.service.impl;

import com.pantrypilot.model.User;
import com.pantrypilot.repository.UserRepository;
import com.pantrypilot.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public User registerUser(User user) throws Exception {
        if (isUsernameTaken(user.getUsername())) {
            throw new Exception("Username already taken");
        }
        if (isEmailTaken(user.getEmail())) {
            throw new Exception("Email already taken");
        }

        // Hash the password before saving
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    @Override
    public Optional<User> loginUser(String identifier, String password) {
        // Try finding by username first
        Optional<User> userOpt = userRepository.findByUsername(identifier);

        // If not found by username, try email
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(identifier);
        }

        // Check password
        if (userOpt.isPresent() && passwordEncoder.matches(password, userOpt.get().getPassword())) {
            return userOpt;
        }
        return Optional.empty();
    }

    @Override
    public boolean isUsernameTaken(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public boolean isEmailTaken(String email) {
        return userRepository.existsByEmail(email);
    }
}
