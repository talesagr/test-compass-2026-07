package com.compass.bank.test.digitalbankapi.application.exception;

public class IdempotencyKeyConflictException extends RuntimeException {

	public IdempotencyKeyConflictException() {
		super("Idempotency-Key was already used with a different request payload");
	}
}
