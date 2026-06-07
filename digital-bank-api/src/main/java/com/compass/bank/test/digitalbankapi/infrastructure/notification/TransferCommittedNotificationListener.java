package com.compass.bank.test.digitalbankapi.infrastructure.notification;

import com.compass.bank.test.digitalbankapi.application.interfaces.NotificationPort;
import com.compass.bank.test.digitalbankapi.application.TransferCompletedEvent;
import com.compass.bank.test.digitalbankapi.config.AsyncConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferCommittedNotificationListener {

	private final NotificationPort notificationPort;

	@Async(AsyncConfiguration.NOTIFY_TASK_EXECUTOR)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onTransferCompleted(TransferCompletedEvent event) {
		try {
			notificationPort.notifyTransferCompleted(event.transferId(), event.fromAccountId(), event.toAccountId(),
					event.amount());
		}
		catch (Exception ex) {
			log.error("notifyTransferCompleted failed transferId={} from={} to={} amount={}", event.transferId(),
					event.fromAccountId(), event.toAccountId(), event.amount(), ex);
		}
	}
}
