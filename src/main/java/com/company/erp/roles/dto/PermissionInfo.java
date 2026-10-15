package com.company.erp.roles.dto;

/** One switch in the role editor: its key plus the words shown next to its checkbox. */
public record PermissionInfo(String key, String group, String label, String description) {
}
