package com.compass.bank.test.digitalbankapi.api.controller;

import com.compass.bank.test.digitalbankapi.api.dto.response.AccountResponse;
import com.compass.bank.test.digitalbankapi.api.dto.request.CreateAccountRequest;
import com.compass.bank.test.digitalbankapi.api.dto.response.MovementResponse;
import com.compass.bank.test.digitalbankapi.application.service.AccountService;
import com.compass.bank.test.digitalbankapi.domain.model.Account;
import com.compass.bank.test.digitalbankapi.domain.model.LedgerEntry;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

	private final AccountService accountService;

	@GetMapping
	public Page<AccountResponse> list(
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return accountService.list(pageable).map(this::toResponse);
	}

	@GetMapping("/{id}")
	public AccountResponse get(@PathVariable UUID id) {
		return toResponse(accountService.get(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
		Account created = accountService.create(request.name(), request.initialBalance());
		return toResponse(created);
	}

	@GetMapping("/{id}/movements")
	public Page<MovementResponse> movements(@PathVariable UUID id, @PageableDefault(size = 20) Pageable pageable) {
		Page<LedgerEntry> page = accountService.movements(id, pageable);
		return page.map(
				e -> new MovementResponse(e.getId(), e.getEntryType().name(), e.getAmount(), e.getTransferId(),
						e.getCreatedAt()));
	}

	private AccountResponse toResponse(Account account) {
		return new AccountResponse(account.getId(), account.getHolderName(), account.getBalance(), account.getCreatedAt());
	}
}
