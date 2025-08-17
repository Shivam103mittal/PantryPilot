package com.pantrypilot.controller;

import com.pantrypilot.model.User;
import com.pantrypilot.service.UserService;
import com.pantrypilot.util.JwtUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public User register(@RequestBody User user) throws Exception {
        return userService.registerUser(user);
    }

    // Updated login to accept JSON body
@PostMapping("/login")
public Map<String, String> login(@RequestBody Map<String, String> credentials) {
    String input = credentials.get("username"); // can be username or email
    String password = credentials.get("password");

    Optional<User> userOpt = userService.loginUser(input, password);
    Map<String, String> response = new HashMap<>();

    if (userOpt.isPresent()) {
        User user = userOpt.get();
        // Use email in JWT because filter loads user by email
        String token = JwtUtil.generateToken(user.getEmail());
        response.put("token", token);
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
    } else {
        response.put("error", "Invalid username/email or password");
    }

    return response;
}

}
