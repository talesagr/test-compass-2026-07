package com.compass.bank.test.digitalbankapi.infrastructure.notification;

import com.compass.bank.test.digitalbankapi.application.interfaces.NotificationPort;
import com.compass.bank.test.digitalbankapi.domain.model.TransferNotification;
import com.compass.bank.test.digitalbankapi.infrastructure.persistence.TransferNotificationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DbNotificationAdapter implements NotificationPort {

	private final TransferNotificationRepository transferNotificationRepository;

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void notifyTransferCompleted(UUID transferId, UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
		String payload = "transferId=%s,from=%s,to=%s,amount=%s".formatted(transferId, fromAccountId, toAccountId,
				amount.toPlainString());
		transferNotificationRepository.save(
				new TransferNotification(UUID.randomUUID(), transferId, payload, Instant.now()));
		log.info("transfer_notification {}", payload);
	}
}
