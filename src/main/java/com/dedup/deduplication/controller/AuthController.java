package com.dedup.deduplication.controller;

import com.dedup.deduplication.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new user.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthService.AuthResponse> register(@RequestBody AuthService.RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /**
     * Login and get JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthService.AuthResponse> login(@RequestBody AuthService.LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Get current user profile.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfile> getCurrentUser(
            @RequestHeader("Authorization") String authHeader,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            com.dedup.deduplication.security.JwtAuthenticationFilter.UserPrincipal principal) {
        
        return authService.getUserById(principal.userId())
                .map(user -> ResponseEntity.ok(new UserProfile(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getFullName(),
                        user.getRoles(),
                        user.getTotalStorageUsed(),
                        user.getActualStorageUsed(),
                        user.getDeduplicatedStorageSaved(),
                        user.getTotalFilesUploaded(),
                        user.getTotalDuplicatesDetected(),
                        user.getCloudSyncEnabled(),
                        user.isActive()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update user profile.
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            com.dedup.deduplication.security.JwtAuthenticationFilter.UserPrincipal principal,
            @RequestBody UpdateProfileRequest request) {
        
        authService.updateProfile(principal.userId(), request.fullName());
        return ResponseEntity.ok().build();
    }

    /**
     * Change password.
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            com.dedup.deduplication.security.JwtAuthenticationFilter.UserPrincipal principal,
            @RequestBody ChangePasswordRequest request) {
        
        authService.changePassword(principal.userId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    /**
     * User profile DTO.
     */
    public record UserProfile(
            String id,
            String username,
            String email,
            String fullName,
            java.util.List<String> roles,
            Long totalStorageUsed,
            Long actualStorageUsed,
            Long deduplicatedStorageSaved,
            Integer totalFilesUploaded,
            Integer totalDuplicatesDetected,
            Boolean cloudSyncEnabled,
            Boolean active
    ) {}

    /**
     * Update profile request.
     */
    public record UpdateProfileRequest(String fullName) {}

    /**
     * Change password request.
     */
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}
}
