package com.company.erp.config;

import com.company.erp.branch.Branch;
import com.company.erp.branch.BranchRepository;
import com.company.erp.branch.BranchType;
import com.company.erp.product.ProductService;
import com.company.erp.product.dto.ProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Realistic DEVELOPMENT-ONLY seed data, per spec section 42. Gated by
 * erp.seed.enabled (true only in the dev profile). Never touches
 * production - the property defaults to false and application-prod.properties
 * pins it explicitly.
 *
 * No users are seeded here on purpose - no credentials belong in source
 * control. Create the first account via POST /api/auth/bootstrap-admin
 * (see AuthController), which only works while the users table is empty.
 *
 * Idempotent: skips seeding entirely if any branch already exists, so
 * restarting the app in dev doesn't try to recreate branches/products and
 * violate the unique constraints.
 */
@Component
@RequiredArgsConstructor
public class DevSeedDataRunner implements CommandLineRunner {

    private final BranchRepository branchRepository;
    private final ProductService productService;

    @Value("${erp.seed.enabled:false}")
    private boolean seedEnabled;

    @Override
    public void run(String... args) {
        if (!seedEnabled || branchRepository.count() > 0) {
            return;
        }

        Branch mainWarehouse = newBranch("Main Warehouse", "WH-MAIN", BranchType.WAREHOUSE);
        Branch boleStore = newBranch("Bole Store", "ST-BOLE", BranchType.STORE);
        branchRepository.save(mainWarehouse);
        branchRepository.save(boleStore);

        productService.create(new ProductRequest(
                "Cable 2.5mm", "CBL-2.5", "2.5mm electrical cable", "meter", 50));
        productService.create(new ProductRequest(
                "Cable 4mm", "CBL-4.0", "4mm electrical cable", "meter", 50));
        productService.create(new ProductRequest(
                "Cable 6mm", "CBL-6.0", "6mm electrical cable", "meter", 30));
    }

    private Branch newBranch(String name, String code, BranchType type) {
        Branch branch = new Branch();
        branch.setName(name);
        branch.setCode(code);
        branch.setType(type);
        branch.setActive(true);
        return branch;
    }
}
