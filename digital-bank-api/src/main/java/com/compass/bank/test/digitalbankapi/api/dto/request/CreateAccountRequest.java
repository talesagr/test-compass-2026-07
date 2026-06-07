package com.compass.bank.test.digitalbankapi.api.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CreateAccountRequest(@NotBlank String name,
		@NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal initialBalance) {
}
