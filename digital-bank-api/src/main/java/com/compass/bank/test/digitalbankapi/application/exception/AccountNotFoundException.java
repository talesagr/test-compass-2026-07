package com.compass.bank.test.digitalbankapi.application.exception;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {

	private final UUID accountId;

	public AccountNotFoundException(UUID accountId) {
		super("Account not found: " + accountId);
		this.accountId = accountId;
	}

	public UUID getAccountId() {
		return accountId;
	}
}
