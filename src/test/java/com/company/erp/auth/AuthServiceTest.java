package com.company.erp.auth;

import com.company.erp.auth.dto.LoginRequest;
import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.common.audit.BaseEntity;
import com.company.erp.security.JwtTokenProvider;
import com.company.erp.security.UserPrincipal;
import com.company.erp.user.Role;
import com.company.erp.user.User;
import com.company.erp.user.UserRepository;
import com.company.erp.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private BranchRepository branchRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private Authentication authentication;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(authenticationManager, jwtTokenProvider, branchRepository, userRepository, userService);
    }

    @Test
    void successfulLoginReturnsATokenAndEnrichesTheBranchName() {
        Branch bole = new Branch();
        bole.setName("Bole Store");
        UUID branchId = UUID.randomUUID();
        setId(bole, branchId);

        User user = new User();
        user.setName("Bole Manager");
        user.setEmail("manager@bole.test");
        user.setRole(Role.STORE_MANAGER);
        user.setBranch(bole);
        UserPrincipal principal = new UserPrincipal(user);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(jwtTokenProvider.generateAccessToken(principal)).thenReturn("signed.jwt.token");
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(bole));

        var response = service.login(new LoginRequest("manager@bole.test", "correct-password"));

        assertThat(response.accessToken()).isEqualTo("signed.jwt.token");
        assertThat(response.role()).isEqualTo("STORE_MANAGER");
        assertThat(response.branchId()).isEqualTo(branchId);
        assertThat(response.branchName()).isEqualTo("Bole Store");
        assertThat(response.permissions()).contains("POS_ACCESS", "SALES_CREATE");
    }

    @Test
    void loginForASuperAdminHasNoBranchAndNoBranchLookupIsEvenAttempted() {
        User admin = new User();
        admin.setName("Root Admin");
        admin.setEmail("admin@test.com");
        admin.setRole(Role.SUPER_ADMIN);
        admin.setBranch(null);
        UserPrincipal principal = new UserPrincipal(admin);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        lenient().when(jwtTokenProvider.generateAccessToken(principal)).thenReturn("token");

        var response = service.login(new LoginRequest("admin@test.com", "password"));

        assertThat(response.branchId()).isNull();
        assertThat(response.branchName()).isNull();
        org.mockito.Mockito.verifyNoInteractions(branchRepository);
    }

    @Test
    void wrongPasswordPropagatesTheAuthenticationFailureRatherThanSwallowingIt() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> service.login(new LoginRequest("someone@test.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void currentUserAlsoEnrichesBranchNameTheSameWay() {
        Branch warehouse = new Branch();
        warehouse.setName("Main Warehouse");
        UUID branchId = UUID.randomUUID();
        setId(warehouse, branchId);

        User user = new User();
        user.setName("Warehouse Staff");
        user.setEmail("staff@warehouse.test");
        user.setRole(Role.WAREHOUSE_STAFF);
        user.setBranch(warehouse);
        UserPrincipal principal = new UserPrincipal(user);

        when(branchRepository.findById(branchId)).thenReturn(Optional.of(warehouse));

        var response = service.currentUser(principal);

        assertThat(response.branchName()).isEqualTo("Main Warehouse");
        assertThat(response.role()).isEqualTo("WAREHOUSE_STAFF");
    }

    private void setId(BaseEntity entity, UUID id) {
        try {
            var field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
