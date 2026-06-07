package com.compass.bank.test.digitalbankapi.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID id, UUID fromAccountId, UUID toAccountId, BigDecimal amount, Instant createdAt) {
}
