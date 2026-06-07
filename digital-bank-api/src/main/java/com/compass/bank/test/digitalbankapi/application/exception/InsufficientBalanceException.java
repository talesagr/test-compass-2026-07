package com.compass.bank.test.digitalbankapi.application.exception;

public class InsufficientBalanceException extends RuntimeException {

	public InsufficientBalanceException() {
		super("Insufficient balance");
	}
}
