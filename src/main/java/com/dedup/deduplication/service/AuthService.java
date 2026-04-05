package com.dedup.deduplication.service;

import com.dedup.deduplication.model.User;
import com.dedup.deduplication.repository.InMemoryUserRepository;
import com.dedup.deduplication.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Authentication service for user registration and login.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final InMemoryUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Register a new user.
     *
     * @param request registration request
     * @return authentication response
     */
    public AuthResponse register(RegisterRequest request) {
        log.info("Registration attempt for username: {}", request.getUsername());
        
        // Validate input
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Create user
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .roles(new ArrayList<>(java.util.List.of("USER")))
                .totalStorageUsed(0L)
                .actualStorageUsed(0L)
                .deduplicatedStorageSaved(0L)
                .totalFilesUploaded(0)
                .totalDuplicatesDetected(0)
                .cloudSyncEnabled(false)
                .active(true)
                .build();

        user = userRepository.save(user);
        log.info("User registered successfully: {}", user.getUsername());

        // Generate token
        String token = jwtTokenProvider.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRoles()
        );

        return new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRoles()
        );
    }

    /**
     * Authenticate user and return token.
     *
     * @param request login request
     * @return authentication response
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        if (!user.isActive()) {
            throw new IllegalArgumentException("Account is disabled");
        }

        log.info("User logged in: {}", user.getUsername());

        String token = jwtTokenProvider.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRoles()
        );

        return new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRoles()
        );
    }

    /**
     * Get user by ID.
     *
     * @param userId the user ID
     * @return user
     */
    public Optional<User> getUserById(String userId) {
        return userRepository.findById(userId);
    }

    /**
     * Get user by username.
     *
     * @param username the username
     * @return user
     */
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Update user profile.
     *
     * @param userId   the user ID
     * @param fullName the new full name
     * @return updated user
     */
    public User updateProfile(String userId, String fullName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setFullName(fullName);
        return userRepository.save(user);
    }

    /**
     * Change user password.
     *
     * @param userId          the user ID
     * @param currentPassword the current password
     * @param newPassword     the new password
     */
    public void changePassword(String userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for user: {}", user.getUsername());
    }

    /**
     * Registration request DTO.
     */
    @lombok.Data
    public static class RegisterRequest {
        private String username;
        private String email;
        private String password;
        private String fullName;
    }

    /**
     * Login request DTO.
     */
    @lombok.Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    /**
     * Authentication response DTO.
     */
    public record AuthResponse(
            String token,
            String userId,
            String username,
            String email,
            String fullName,
            java.util.List<String> roles
    ) {}
}
