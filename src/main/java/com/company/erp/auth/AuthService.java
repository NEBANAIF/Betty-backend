package com.company.erp.auth;

import com.company.erp.auth.dto.BootstrapAdminRequest;
import com.company.erp.auth.dto.CurrentUserResponse;
import com.company.erp.auth.dto.LoginRequest;
import com.company.erp.auth.dto.LoginResponse;
import com.company.erp.branch.BranchRepository;
import com.company.erp.security.JwtTokenProvider;
import com.company.erp.security.UserPrincipal;
import com.company.erp.user.Role;
import com.company.erp.user.UserRepository;
import com.company.erp.user.UserService;
import com.company.erp.user.dto.CreateUserRequest;
import com.company.erp.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    /**
     * Creates the very first Super Admin from credentials supplied in the
     * request body - never from source. Only works while the users table
     * is empty; once any user exists (including the one this call just
     * made) it refuses, so it's a true one-time bootstrap, not a standing
     * open registration endpoint.
     */
    public UserResponse bootstrapAdmin(BootstrapAdminRequest request) {
        if (userRepository.count() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An admin already exists - use POST /api/users (as an authenticated Super Admin) to add more users.");
        }
        return userService.createUser(new CreateUserRequest(
                request.name(), request.email(), null, request.password(), Role.SUPER_ADMIN, null, null));
    }

    public LoginResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtTokenProvider.generateAccessToken(principal);

        return new LoginResponse(
                token,
                "Bearer",
                principal.getUserId(),
                principal.getName(),
                principal.getUsername(),
                principal.getRole().name(),
                principal.getRoleName(),
                principal.getBranchId(),
                branchName(principal.getBranchId()),
                permissionNames(principal)
        );
    }

    public CurrentUserResponse currentUser(UserPrincipal principal) {
        return new CurrentUserResponse(
                principal.getUserId(),
                principal.getName(),
                principal.getUsername(),
                principal.getRole().name(),
                principal.getRoleName(),
                principal.getBranchId(),
                branchName(principal.getBranchId()),
                permissionNames(principal)
        );
    }

    /** null for SUPER_ADMIN (no assigned branch) - never throws for a branch that's since been deactivated/removed, since login/me must still work either way. */
    private String branchName(UUID branchId) {
        if (branchId == null) {
            return null;
        }
        return branchRepository.findById(branchId).map(b -> b.getName()).orElse(null);
    }

    private Set<String> permissionNames(UserPrincipal principal) {
        return principal.getPermissions().stream().map(Enum::name).collect(Collectors.toSet());
    }
}

