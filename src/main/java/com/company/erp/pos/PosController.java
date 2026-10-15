package com.company.erp.pos;

import com.company.erp.pos.dto.CheckoutRequest;
import com.company.erp.sales.dto.SaleResponse;
import com.company.erp.security.ContentAccess;
import com.company.erp.user.Permission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
public class PosController {

    private final PosCheckoutService posCheckoutService;
    private final ContentAccess contentAccess;

    @PostMapping("/checkout")
    @PreAuthorize("hasAuthority('POS_ACCESS')")
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        SaleResponse sale = posCheckoutService.checkout(request);
        return contentAccess.can(Permission.VIEW_PROFIT) ? sale : sale.withoutProfit();
    }
}
