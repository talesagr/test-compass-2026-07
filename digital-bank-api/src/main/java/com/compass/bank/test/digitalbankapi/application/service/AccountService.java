package com.compass.bank.test.digitalbankapi.application.service;

import com.compass.bank.test.digitalbankapi.application.exception.AccountNotFoundException;
import com.compass.bank.test.digitalbankapi.application.exception.BadRequestException;
import com.compass.bank.test.digitalbankapi.domain.model.Account;
import com.compass.bank.test.digitalbankapi.domain.model.LedgerEntry;
import com.compass.bank.test.digitalbankapi.infrastructure.persistence.AccountRepository;
import com.compass.bank.test.digitalbankapi.infrastructure.persistence.LedgerEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

	private final AccountRepository accountRepository;
	private final LedgerEntryRepository ledgerEntryRepository;

	@Transactional(readOnly = true)
	public Page<Account> list(Pageable pageable) {
		return accountRepository.findAll(pageable);
	}

	@Transactional(readOnly = true)
	public Account get(UUID id) {
		return accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
	}

	@Transactional
	public Account create(String holderName, BigDecimal initialBalance) {
		if (initialBalance == null || initialBalance.signum() < 0) {
			throw new BadRequestException("initialBalance must be zero or positive");
		}
		BigDecimal balance = initialBalance.setScale(2, RoundingMode.HALF_UP);
		Instant now = Instant.now();
		Account account = new Account(UUID.randomUUID(), holderName, balance, now);
		return accountRepository.save(account);
	}

	@Transactional(readOnly = true)
	public Page<LedgerEntry> movements(UUID accountId, Pageable pageable) {
		if (!accountRepository.existsById(accountId)) {
			throw new AccountNotFoundException(accountId);
		}
		return ledgerEntryRepository.findByAccount_IdOrderByCreatedAtDesc(accountId, pageable);
	}
}
