package com.company.erp.auth;

import com.company.erp.auth.dto.BootstrapAdminRequest;
import com.company.erp.auth.dto.CurrentUserResponse;
import com.company.erp.auth.dto.LoginRequest;
import com.company.erp.auth.dto.LoginResponse;
import com.company.erp.security.UserPrincipal;
import com.company.erp.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.currentUser(principal);
    }

    /**
     * One-time setup endpoint, open (no token needed) because there's no
     * one to authenticate as yet. Call it once from Postman with the
     * name/email/password you want for the first Super Admin; it refuses
     * with 409 the moment any user already exists, so it can't be used
     * to create a second account or by anyone once you've bootstrapped.
     */
    @PostMapping("/bootstrap-admin")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse bootstrapAdmin(@Valid @RequestBody BootstrapAdminRequest request) {
        return authService.bootstrapAdmin(request);
    }
}
