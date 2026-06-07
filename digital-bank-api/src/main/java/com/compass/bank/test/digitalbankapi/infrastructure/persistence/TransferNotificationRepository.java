package com.compass.bank.test.digitalbankapi.infrastructure.persistence;

import com.compass.bank.test.digitalbankapi.domain.model.TransferNotification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferNotificationRepository extends JpaRepository<TransferNotification, UUID> {
}
