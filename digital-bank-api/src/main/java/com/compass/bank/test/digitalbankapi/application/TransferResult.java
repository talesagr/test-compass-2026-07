package com.compass.bank.test.digitalbankapi.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResult(UUID id, UUID fromAccountId, UUID toAccountId, BigDecimal amount, Instant createdAt) {
}
