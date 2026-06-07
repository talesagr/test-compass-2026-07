package com.compass.bank.test.digitalbankapi.infrastructure.persistence;

import com.compass.bank.test.digitalbankapi.domain.model.Transfer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
}
