package com.compass.bank.test.digitalbankapi.application.exception;

public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}
}
