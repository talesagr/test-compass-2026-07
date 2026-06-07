package com.compass.bank.test.digitalbankapi.application;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCompletedEvent(UUID transferId, UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
}
