package com.canmakan.backend.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public HTTP endpoints for UC18 registration and pre-JWT login. 
 * 
 * @author Amelia
 * @author YangMaowei
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class RegistrationController {

    private final RegistrationService registrationService;
    private final LoginService loginService;

    // Register a new user
    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(registrationService.register(request));
    }

    // Login a user
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(loginService.login(request));
    }
}
