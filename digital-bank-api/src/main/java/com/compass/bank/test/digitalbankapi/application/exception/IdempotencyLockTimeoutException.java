package com.compass.bank.test.digitalbankapi.application.exception;

public class IdempotencyLockTimeoutException extends RuntimeException {

	public IdempotencyLockTimeoutException() {
		super("Timed out waiting for idempotent transfer to finish");
	}
}
