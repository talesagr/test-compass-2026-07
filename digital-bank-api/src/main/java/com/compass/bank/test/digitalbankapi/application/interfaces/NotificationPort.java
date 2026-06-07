package com.compass.bank.test.digitalbankapi.application.interfaces;

import java.math.BigDecimal;
import java.util.UUID;

public interface NotificationPort {

	void notifyTransferCompleted(UUID transferId, UUID fromAccountId, UUID toAccountId, BigDecimal amount);
}
