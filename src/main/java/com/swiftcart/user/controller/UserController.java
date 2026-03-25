package com.swiftcart.user.controller;

import com.swiftcart.common.response.ApiResponse;
import com.swiftcart.user.dto.UserResponse;
import com.swiftcart.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Returns the profile of the currently authenticated user. */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(
                ApiResponse.ok(userService.getProfile(principal.getUsername()))
        );
    }
}
