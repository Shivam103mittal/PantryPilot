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
        String username = credentials.get("username");
        String password = credentials.get("password");

        Optional<User> userOpt = userService.loginUser(username, password);
        Map<String, String> response = new HashMap<>();

        if (userOpt.isPresent()) {
            String token = JwtUtil.generateToken(username);
            response.put("token", token);
            response.put("username", username);
        } else {
            response.put("error", "Invalid username or password");
        }

        return response;
    }
}
