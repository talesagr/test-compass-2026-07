package com.compass.bank.test.digitalbankapi.application.exception;

public class SameAccountTransferException extends RuntimeException {

	public SameAccountTransferException() {
		super("Source and destination accounts must differ");
	}
}
