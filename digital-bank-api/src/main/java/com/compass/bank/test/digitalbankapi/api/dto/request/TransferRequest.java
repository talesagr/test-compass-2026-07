package com.compass.bank.test.digitalbankapi.api.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(@NotNull UUID fromAccountId, @NotNull UUID toAccountId,
		@NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount) {
}
