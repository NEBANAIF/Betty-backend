package com.company.erp.config;

import com.company.erp.user.Role;
import com.company.erp.user.UserRepository;
import com.company.erp.user.UserService;
import com.company.erp.user.dto.CreateUserRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The only way a fresh, non-dev deployment ever gets a first user:
 * DevSeedDataRunner is dev-only and creates a full demo dataset, but
 * production has erp.seed.enabled=false (application-prod.properties),
 * which would otherwise leave a brand new install with literally no way
 * to log in - USER_MANAGE-gated user creation requires an existing
 * SUPER_ADMIN, a chicken-and-egg problem for `docker compose up` on a
 * fresh database.
 * <p>
 * @ConditionalOnProperty makes this mutually exclusive with the dev seed
 * runner rather than layered on top of it: when erp.seed.enabled=true
 * (dev), this bean doesn't even get created, so DevSeedDataRunner's own
 * "skip if any user exists" idempotency guard is never put in a race
 * with a second runner that might create a user first and cause it to
 * silently skip its own (much larger) seed data.
 * <p>
 * Idempotent the same way: does nothing if any user already exists, so
 * restarting the container never tries to recreate the account.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "erp.seed.enabled", havingValue = "false", matchIfMissing = true)
public class ProductionBootstrapRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserService userService;

    @Value("${erp.bootstrap-admin.enabled:true}")
    private boolean bootstrapEnabled;

    @Value("${erp.bootstrap-admin.email:admin@erp.local}")
    private String bootstrapEmail;

    @Value("${erp.bootstrap-admin.password:ChangeMe123!}")
    private String bootstrapPassword;

    @Override
    public void run(String... args) {
        if (!bootstrapEnabled || userRepository.count() > 0) {
            return;
        }

        userService.createUser(new CreateUserRequest(
                "System Administrator", bootstrapEmail, null, bootstrapPassword, Role.SUPER_ADMIN, null, null));

        log.warn("=================================================================");
        log.warn("Bootstrap SUPER_ADMIN account created: {}", bootstrapEmail);
        log.warn("Log in and change this password immediately - it is a well-known");
        log.warn("default unless ERP_BOOTSTRAP_ADMIN_PASSWORD was set before startup.");
        log.warn("=================================================================");
    }
}
