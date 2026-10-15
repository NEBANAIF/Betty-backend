package com.company.erp.branch.dto;

import com.company.erp.branch.Branch;

import java.util.UUID;

/**
 * What a branch dropdown needs and nothing more - no address, phone, email or
 * manager. Lets someone pick the OTHER branch of a stock transfer without being
 * able to read that branch's details.
 */
public record BranchOptionResponse(UUID id, String name, String code, String type) {

    public static BranchOptionResponse from(Branch branch) {
        return new BranchOptionResponse(branch.getId(), branch.getName(), branch.getCode(), branch.getType().name());
    }
}
