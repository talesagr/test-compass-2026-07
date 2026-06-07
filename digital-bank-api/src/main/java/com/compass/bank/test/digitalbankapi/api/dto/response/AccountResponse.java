package com.compass.bank.test.digitalbankapi.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String name, BigDecimal balance, Instant createdAt) {
}
