package com.compass.bank.test.digitalbankapi.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.compass.bank.test.digitalbankapi.application.exception.AccountNotFoundException;
import com.compass.bank.test.digitalbankapi.application.exception.BadRequestException;
import com.compass.bank.test.digitalbankapi.application.exception.IdempotencyKeyConflictException;
import com.compass.bank.test.digitalbankapi.application.exception.InsufficientBalanceException;
import com.compass.bank.test.digitalbankapi.application.exception.SameAccountTransferException;
import com.compass.bank.test.digitalbankapi.application.service.TransferService;
import com.compass.bank.test.digitalbankapi.domain.model.Account;
import com.compass.bank.test.digitalbankapi.domain.model.LedgerEntry;
import com.compass.bank.test.digitalbankapi.domain.model.Transfer;
import com.compass.bank.test.digitalbankapi.infrastructure.idempotency.TransferIdempotencyRow;
import com.compass.bank.test.digitalbankapi.infrastructure.idempotency.TransferIdempotencyStore;
import com.compass.bank.test.digitalbankapi.infrastructure.persistence.AccountRepository;
import com.compass.bank.test.digitalbankapi.infrastructure.persistence.LedgerEntryRepository;
import com.compass.bank.test.digitalbankapi.infrastructure.persistence.TransferRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

	private static final UUID A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final UUID B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private TransferRepository transferRepository;

	@Mock
	private LedgerEntryRepository ledgerEntryRepository;

	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	@Mock
	private TransferIdempotencyStore transferIdempotencyStore;

	@Mock
	private ThreadPoolTaskScheduler idempotencyWaitScheduler;

	@InjectMocks
	private TransferService transferService;

	@BeforeEach
	void stubIdempotencyWaitScheduler() throws Exception {
		ScheduledFuture<?> future = mock(ScheduledFuture.class);
		lenient().when(future.get()).thenReturn(null);
		lenient().doReturn(future).when(idempotencyWaitScheduler).schedule(any(Runnable.class), any(Instant.class));
	}

	@Test
	void transfer_sameAccount_throws() {
		assertThrows(SameAccountTransferException.class, () -> transferService.transfer(A, A, BigDecimal.ONE));
		verifyNoInteractions(accountRepository);
		verifyNoInteractions(transferIdempotencyStore);
	}

	@Test
	void transfer_nullAmount_throws() {
		assertThrows(BadRequestException.class, () -> transferService.transfer(A, B, null));
		verifyNoInteractions(accountRepository);
		verifyNoInteractions(transferIdempotencyStore);
	}

	@Test
	void transfer_zeroAmount_throws() {
		assertThrows(BadRequestException.class, () -> transferService.transfer(A, B, BigDecimal.ZERO));
		verifyNoInteractions(accountRepository);
		verifyNoInteractions(transferIdempotencyStore);
	}

	@Test
	void transfer_accountMissing_throws() {
		when(accountRepository.findByIdForUpdate(A)).thenReturn(Optional.empty());
		assertThrows(AccountNotFoundException.class, () -> transferService.transfer(A, B, BigDecimal.ONE));
		verify(transferRepository, never()).saveAndFlush(any());
		verifyNoInteractions(transferIdempotencyStore);
	}

	@Test
	void transfer_insufficientBalance_throws() {
		Account accA = new Account(A, "A", new BigDecimal("10.00"), Instant.now());
		Account accB = new Account(B, "B", BigDecimal.ZERO, Instant.now());
		when(accountRepository.findByIdForUpdate(A)).thenReturn(Optional.of(accA));
		when(accountRepository.findByIdForUpdate(B)).thenReturn(Optional.of(accB));
		assertThrows(InsufficientBalanceException.class, () -> transferService.transfer(A, B, new BigDecimal("50.00")));
		verify(transferRepository, never()).saveAndFlush(any());
		verifyNoInteractions(transferIdempotencyStore);
	}

	@Test
	void transfer_success_updatesBalancesPublishesEvent() {
		Account accA = new Account(A, "A", new BigDecimal("100.00"), Instant.now());
		Account accB = new Account(B, "B", new BigDecimal("20.00"), Instant.now());
		when(accountRepository.findByIdForUpdate(A)).thenReturn(Optional.of(accA));
		when(accountRepository.findByIdForUpdate(B)).thenReturn(Optional.of(accB));

		TransferResult result = transferService.transfer(A, B, new BigDecimal("15.00"));

		assertEquals(new BigDecimal("85.00"), accA.getBalance());
		assertEquals(new BigDecimal("35.00"), accB.getBalance());
		assertEquals(A, result.fromAccountId());
		assertEquals(B, result.toAccountId());
		verify(transferRepository).saveAndFlush(any(Transfer.class));
		verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
		verify(accountRepository).save(accA);
		verify(accountRepository).save(accB);
		ArgumentCaptor<TransferCompletedEvent> captor = ArgumentCaptor.forClass(TransferCompletedEvent.class);
		verify(applicationEventPublisher).publishEvent(captor.capture());
		TransferCompletedEvent event = captor.getValue();
		assertEquals(result.id(), event.transferId());
		assertEquals(new BigDecimal("15.00"), event.amount());
		verifyNoInteractions(transferIdempotencyStore);
	}

	@Test
	void transfer_withIdempotencyKey_success_insertsMarksAndPublishes() {
		BigDecimal amount = new BigDecimal("15.00");
		when(transferIdempotencyStore.insertClaimIfAbsent("k1", A, B, amount)).thenReturn(1);
		when(transferIdempotencyStore.markCompleteIfStillPending(eq("k1"), any(UUID.class))).thenReturn(1);
		Account accA = new Account(A, "A", new BigDecimal("100.00"), Instant.now());
		Account accB = new Account(B, "B", new BigDecimal("20.00"), Instant.now());
		when(accountRepository.findByIdForUpdate(A)).thenReturn(Optional.of(accA));
		when(accountRepository.findByIdForUpdate(B)).thenReturn(Optional.of(accB));

		TransferResult result = transferService.transfer(Optional.of("k1"), A, B, amount);

		assertEquals(new BigDecimal("85.00"), accA.getBalance());
		verify(transferIdempotencyStore).insertClaimIfAbsent("k1", A, B, amount);
		verify(transferIdempotencyStore).markCompleteIfStillPending(eq("k1"), eq(result.id()));
		verify(applicationEventPublisher).publishEvent(any(TransferCompletedEvent.class));
	}

	@Test
	void transfer_withIdempotencyKey_replay_doesNotMutateAccountsOrRepublish() {
		BigDecimal amount = new BigDecimal("15.00");
		UUID existingTransferId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
		when(transferIdempotencyStore.insertClaimIfAbsent("k1", A, B, amount)).thenReturn(0);
		when(transferIdempotencyStore.find("k1"))
				.thenReturn(Optional.of(new TransferIdempotencyRow(A, B, amount, existingTransferId)));
		Transfer stored = new Transfer(existingTransferId, A, B, amount, Instant.parse("2020-01-01T00:00:00Z"));
		when(transferRepository.findById(existingTransferId)).thenReturn(Optional.of(stored));

		TransferResult result = transferService.transfer(Optional.of("k1"), A, B, amount);

		assertEquals(existingTransferId, result.id());
		assertEquals(amount, result.amount());
		verifyNoInteractions(accountRepository);
		verify(transferRepository, never()).saveAndFlush(any());
		verify(applicationEventPublisher, never()).publishEvent(any());
	}

	@Test
	void transfer_withIdempotencyKey_conflictOnPayloadMismatch() {
		BigDecimal amount = new BigDecimal("5.00");
		when(transferIdempotencyStore.insertClaimIfAbsent("k1", A, B, amount)).thenReturn(0);
		when(transferIdempotencyStore.find("k1"))
				.thenReturn(Optional.of(new TransferIdempotencyRow(A, B, new BigDecimal("10.00"), UUID.randomUUID())));

		assertThrows(IdempotencyKeyConflictException.class,
				() -> transferService.transfer(Optional.of("k1"), A, B, amount));
		verifyNoInteractions(accountRepository);
	}
}
