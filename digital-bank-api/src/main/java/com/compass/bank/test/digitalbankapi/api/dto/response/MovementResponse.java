package com.compass.bank.test.digitalbankapi.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MovementResponse(UUID id, String type, BigDecimal amount, UUID transferId, Instant occurredAt) {
}
