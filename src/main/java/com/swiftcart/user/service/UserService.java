package com.swiftcart.user.service;

import com.swiftcart.common.exception.AppException;
import com.swiftcart.config.JwtService;
import com.swiftcart.user.domain.Role;
import com.swiftcart.user.domain.User;
import com.swiftcart.user.dto.AuthResponse;
import com.swiftcart.user.dto.LoginRequest;
import com.swiftcart.user.dto.RegisterRequest;
import com.swiftcart.user.dto.UserResponse;
import com.swiftcart.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User service: handles registration, login, and profile queries.
 *
 * UserDetailsService is now a separate bean (UserDetailsServiceImpl)
 * to break the Spring Security circular dependency chain.
 */
@Slf4j
@Service
public class UserService {

    private final UserRepository        userRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtService            jwtService;
    private final AuthenticationManager authenticationManager;

    @Autowired
    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Lazy AuthenticationManager authenticationManager) {
        this.userRepository        = userRepository;
        this.passwordEncoder       = passwordEncoder;
        this.jwtService            = jwtService;
        this.authenticationManager = authenticationManager;
    }

    // ------------------------------------------------------------------ //
    // Registration
    // ------------------------------------------------------------------ //

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw AppException.conflict("Email already registered: " + request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(Role.CUSTOMER)
                .enabled(true)
                .build();

        userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        String token = jwtService.generateToken(user);
        return toAuthResponse(user, token);
    }

    // ------------------------------------------------------------------ //
    // Login
    // ------------------------------------------------------------------ //

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> AppException.notFound("User not found"));

        String token = jwtService.generateToken(user);
        log.info("User logged in: {}", user.getEmail());
        return toAuthResponse(user, token);
    }

    // ------------------------------------------------------------------ //
    // Profile
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public UserResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("User not found"));
        return UserResponse.from(user);
    }

    // ------------------------------------------------------------------ //
    // Internal helpers
    // ------------------------------------------------------------------ //

    private AuthResponse toAuthResponse(User user, String token) {
        return new AuthResponse(
                token,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name()
        );
    }
}
