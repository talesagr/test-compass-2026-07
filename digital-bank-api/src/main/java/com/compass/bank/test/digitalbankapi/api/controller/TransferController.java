package com.compass.bank.test.digitalbankapi.api.controller;

import com.compass.bank.test.digitalbankapi.api.dto.request.TransferRequest;
import com.compass.bank.test.digitalbankapi.api.dto.response.TransferResponse;
import com.compass.bank.test.digitalbankapi.application.TransferResult;
import com.compass.bank.test.digitalbankapi.application.service.TransferService;
import com.compass.bank.test.digitalbankapi.config.AsyncConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {

	private final TransferService transferService;
	private final Executor transferApiExecutor;

	public TransferController(TransferService transferService,
			@Qualifier(AsyncConfiguration.TRANSFER_API_EXECUTOR) Executor transferApiExecutor) {
		this.transferService = transferService;
		this.transferApiExecutor = transferApiExecutor;
	}

	@Operation(summary = "Create transfer", parameters = {
			@Parameter(
					name = "Idempotency-Key",
					in = ParameterIn.HEADER,
					description = "Optional. Same key + same body replays the same transfer; same key + different body returns 409.",
					example = "abcd9874-e29b-41d4-a716-446655441234"
			)
	})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CompletableFuture<TransferResponse> transfer(
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody TransferRequest request) {
		Optional<String> keyOpt = Optional.ofNullable(idempotencyKey).map(String::trim).filter(s -> !s.isEmpty());
		return CompletableFuture.supplyAsync(() -> {
			TransferResult result = transferService.transfer(keyOpt, request.fromAccountId(), request.toAccountId(),
					request.amount());
			return new TransferResponse(result.id(), result.fromAccountId(), result.toAccountId(), result.amount(),
					result.createdAt());
		}, transferApiExecutor);
	}
}
