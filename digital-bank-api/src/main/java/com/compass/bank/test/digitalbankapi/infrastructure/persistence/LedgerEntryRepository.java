package com.compass.bank.test.digitalbankapi.infrastructure.persistence;

import com.compass.bank.test.digitalbankapi.domain.model.LedgerEntry;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

	Page<LedgerEntry> findByAccount_IdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
}
