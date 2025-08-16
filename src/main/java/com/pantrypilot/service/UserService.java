package com.pantrypilot.service;

import com.pantrypilot.model.User;
import java.util.Optional;

public interface UserService {
    User registerUser(User user) throws Exception;
    Optional<User> loginUser(String username, String password);
    boolean isUsernameTaken(String username);
    boolean isEmailTaken(String email);
}
