package com.compass.bank.test.digitalbankapi.application.service;

import com.compass.bank.test.digitalbankapi.application.TransferCompletedEvent;
import com.compass.bank.test.digitalbankapi.application.TransferResult;
import com.compass.bank.test.digitalbankapi.application.exception.AccountNotFoundException;
import com.compass.bank.test.digitalbankapi.application.exception.BadRequestException;
import com.compass.bank.test.digitalbankapi.application.exception.IdempotencyKeyConflictException;
import com.compass.bank.test.digitalbankapi.application.exception.IdempotencyLockTimeoutException;
import com.compass.bank.test.digitalbankapi.application.exception.InsufficientBalanceException;
import com.compass.bank.test.digitalbankapi.application.exception.SameAccountTransferException;
import com.compass.bank.test.digitalbankapi.domain.model.Account;
import com.compass.bank.test.digitalbankapi.domain.model.EntryType;
import com.compass.bank.test.digitalbankapi.domain.model.LedgerEntry;
import com.compass.bank.test.digitalbankapi.domain.model.Transfer;
import com.compass.bank.test.digitalbankapi.infrastructure.idempotency.TransferIdempotencyRow;
import com.compass.bank.test.digitalbankapi.infrastructure.idempotency.TransferIdempotencyStore;
import com.compass.bank.test.digitalbankapi.infrastructure.persistence.AccountRepository;
import com.compass.bank.test.digitalbankapi.infrastructure.persistence.LedgerEntryRepository;
import com.compass.bank.test.digitalbankapi.infrastructure.persistence.TransferRepository;
import com.compass.bank.test.digitalbankapi.config.AsyncConfiguration;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferService {

	private static final int IDEMPOTENCY_MAX_ATTEMPTS = 60;

	private final AccountRepository accountRepository;
	private final TransferRepository transferRepository;
	private final LedgerEntryRepository ledgerEntryRepository;
	private final ApplicationEventPublisher applicationEventPublisher;
	private final TransferIdempotencyStore transferIdempotencyStore;
	@Qualifier(AsyncConfiguration.IDEMPOTENCY_WAIT_SCHEDULER)
	private final ThreadPoolTaskScheduler idempotencyWaitScheduler;

	@Transactional
	public TransferResult transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
		return transfer(Optional.empty(), fromAccountId, toAccountId, amount);
	}

	@Transactional
	public TransferResult transfer(Optional<String> idempotencyKey, UUID fromAccountId, UUID toAccountId,
			BigDecimal amount) {
		BigDecimal normalizedAmount = requirePositiveNormalizedMoney(amount);
		String key = idempotencyKey.map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
		if (key == null) {
			return executeTransfer(fromAccountId, toAccountId, normalizedAmount);
		}
		if (key.length() > 255) {
			throw new BadRequestException("Idempotency-Key exceeds maximum length of 255");
		}
		return transferWithIdempotency(key, fromAccountId, toAccountId, normalizedAmount);
	}

	private TransferResult transferWithIdempotency(String key, UUID fromAccountId, UUID toAccountId,
			BigDecimal amount) {
		for (int attempt = 0; attempt < IDEMPOTENCY_MAX_ATTEMPTS; attempt++) {
			int inserted = transferIdempotencyStore.insertClaimIfAbsent(key, fromAccountId, toAccountId, amount);
			if (inserted == 1) {
				TransferResult result = executeTransfer(fromAccountId, toAccountId, amount);
				int updated = transferIdempotencyStore.markCompleteIfStillPending(key, result.id());
				if (updated == 1) {
					return result;
				}
				TransferIdempotencyRow row = transferIdempotencyStore.find(key)
						.orElseThrow(() -> new IllegalStateException("idempotency row missing after insert"));
				assertMatchingRequest(row, fromAccountId, toAccountId, amount);
				if (row.transferId() == null) {
					sleepBriefly();
					continue;
				}
				return loadResult(row.transferId());
			}
			TransferIdempotencyRow row = transferIdempotencyStore.find(key)
					.orElseThrow(() -> new IllegalStateException("idempotency row missing after conflict"));
			assertMatchingRequest(row, fromAccountId, toAccountId, amount);
			if (row.transferId() != null) {
				return loadResult(row.transferId());
			}
			sleepBriefly();
		}
		throw new IdempotencyLockTimeoutException();
	}

	private void assertMatchingRequest(TransferIdempotencyRow row, UUID fromAccountId, UUID toAccountId,
			BigDecimal amount) {
		if (!row.fromAccountId().equals(fromAccountId) || !row.toAccountId().equals(toAccountId)
				|| row.amount().compareTo(amount) != 0) {
			throw new IdempotencyKeyConflictException();
		}
	}

	private TransferResult loadResult(UUID transferId) {
		Transfer transfer = transferRepository.findById(transferId)
				.orElseThrow(() -> new IllegalStateException("transfer not found for idempotent replay"));
		return new TransferResult(transfer.getId(), transfer.getFromAccountId(), transfer.getToAccountId(),
				transfer.getAmount(), transfer.getCreatedAt());
	}

	private void sleepBriefly() {
		try {
			idempotencyWaitScheduler.schedule(() -> { }, Instant.now().plusMillis(50)).get();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while waiting for idempotent transfer");
		}
		catch (ExecutionException e) {
			Throwable c = e.getCause() != null ? e.getCause() : e;
			throw new IllegalStateException(c);
		}
	}

	private static BigDecimal requirePositiveNormalizedMoney(BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) {
			throw new BadRequestException("amount must be positive");
		}
		return amount.setScale(2, RoundingMode.HALF_UP);
	}

	private TransferResult executeTransfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
		BigDecimal normalizedAmount = requirePositiveNormalizedMoney(amount);
		if (fromAccountId.equals(toAccountId)) {
			throw new SameAccountTransferException();
		}
		UUID firstLock = fromAccountId.compareTo(toAccountId) < 0 ? fromAccountId : toAccountId;
		UUID secondLock = fromAccountId.compareTo(toAccountId) < 0 ? toAccountId : fromAccountId;

		Account first = accountRepository.findByIdForUpdate(firstLock)
				.orElseThrow(() -> new AccountNotFoundException(firstLock));
		Account second = accountRepository.findByIdForUpdate(secondLock)
				.orElseThrow(() -> new AccountNotFoundException(secondLock));

		Account from = first.getId().equals(fromAccountId) ? first : second;
		Account to = from == first ? second : first;

		if (from.getBalance().compareTo(normalizedAmount) < 0) {
			throw new InsufficientBalanceException();
		}

		Instant now = Instant.now();
		UUID transferId = UUID.randomUUID();

		from.setBalance(from.getBalance().subtract(normalizedAmount));
		to.setBalance(to.getBalance().add(normalizedAmount));

		transferRepository.saveAndFlush(
				new Transfer(transferId, fromAccountId, toAccountId, normalizedAmount, now));
		ledgerEntryRepository.save(
				new LedgerEntry(UUID.randomUUID(), from, EntryType.DEBIT, normalizedAmount, transferId, now));
		ledgerEntryRepository.save(
				new LedgerEntry(UUID.randomUUID(), to, EntryType.CREDIT, normalizedAmount, transferId, now));
		accountRepository.save(from);
		accountRepository.save(to);

		applicationEventPublisher.publishEvent(
				new TransferCompletedEvent(transferId, fromAccountId, toAccountId, normalizedAmount));
		return new TransferResult(transferId, fromAccountId, toAccountId, normalizedAmount, now);
	}
}
