package com.company.erp.security;

import com.company.erp.user.Permission;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * "May the person making this request see this piece of information?" - used to
 * leave costs, profit, inventory value and similar figures OUT of API responses
 * for roles that don't hold the matching permission. Hiding data only in the
 * screen is not enough: anyone can read the raw response in the browser.
 * <p>
 * Fails closed: with no logged-in user the answer is always no.
 */
@Component
public class ContentAccess {

    public boolean can(Permission permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof UserPrincipal principal
                && principal.hasPermission(permission);
    }
}
