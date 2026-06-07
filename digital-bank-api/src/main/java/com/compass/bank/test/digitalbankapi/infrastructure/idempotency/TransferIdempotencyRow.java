package com.compass.bank.test.digitalbankapi.infrastructure.idempotency;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferIdempotencyRow(UUID fromAccountId, UUID toAccountId, BigDecimal amount, UUID transferId) {
}
