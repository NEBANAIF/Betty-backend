package com.company.erp.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SystemSettingsRepository extends JpaRepository<SystemSettings, UUID> {

    /** There is only ever one row (see SystemSettings' javadoc). */
    Optional<SystemSettings> findFirstByOrderByCreatedAtAsc();
}
