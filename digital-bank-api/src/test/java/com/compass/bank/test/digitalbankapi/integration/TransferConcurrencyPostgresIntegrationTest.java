package com.compass.bank.test.digitalbankapi.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.compass.bank.test.digitalbankapi.application.exception.InsufficientBalanceException;
import com.compass.bank.test.digitalbankapi.application.service.TransferService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class TransferConcurrencyPostgresIntegrationTest {

	private static final UUID ALICE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final UUID BOB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
	private static final UUID CHARLIE = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

	private static final BigDecimal INITIAL_SUM = new BigDecimal("1750.50");

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("digital_bank_it")
			.withUsername("bank")
			.withPassword("bank");

	@DynamicPropertySource
	static void registerDatasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private TransferService transferService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void resetFinancialData() {
		jdbcTemplate.update("DELETE FROM transfer_idempotency");
		jdbcTemplate.update("DELETE FROM notifications");
		jdbcTemplate.update("DELETE FROM ledger_entries");
		jdbcTemplate.update("DELETE FROM transfers");
		jdbcTemplate.update("UPDATE accounts SET balance = ?, holder_name = holder_name WHERE id = ?",
				new BigDecimal("1000.00"), ALICE);
		jdbcTemplate.update("UPDATE accounts SET balance = ?, holder_name = holder_name WHERE id = ?",
				new BigDecimal("500.00"), BOB);
		jdbcTemplate.update("UPDATE accounts SET balance = ?, holder_name = holder_name WHERE id = ?",
				new BigDecimal("250.50"), CHARLIE);
	}

	@Test
	void concurrentTransfersSamePair_oneDirection_conservesMoneyAndBalances() throws Exception {
		int threads = 50;
		BigDecimal unit = new BigDecimal("1.00");
		ExecutorService pool = Executors.newFixedThreadPool(16);
		CountDownLatch gate = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(pool.submit(() -> {
				gate.await();
				transferService.transfer(ALICE, BOB, unit);
				return null;
			}));
		}
		gate.countDown();
		for (Future<?> f : futures) {
			f.get(2, TimeUnit.MINUTES);
		}
		pool.shutdown();
		assertTrue(pool.awaitTermination(3, TimeUnit.MINUTES));

		assertEquals(new BigDecimal("950.00"), balance(ALICE));
		assertEquals(new BigDecimal("550.00"), balance(BOB));
		assertEquals(INITIAL_SUM, sumBalances());
		assertEquals(50, countRows("transfers"));
	}

	@Test
	void concurrentTransfersSameAccounts_pingPong_netZeroPerAccount() throws Exception {
		int eachWay = 40;
		ExecutorService pool = Executors.newFixedThreadPool(32);
		CountDownLatch gate = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < eachWay; i++) {
			futures.add(pool.submit(() -> {
				gate.await();
				transferService.transfer(ALICE, BOB, new BigDecimal("2.00"));
				return null;
			}));
			futures.add(pool.submit(() -> {
				gate.await();
				transferService.transfer(BOB, ALICE, new BigDecimal("2.00"));
				return null;
			}));
		}
		gate.countDown();
		for (Future<?> f : futures) {
			f.get(3, TimeUnit.MINUTES);
		}
		pool.shutdown();
		assertTrue(pool.awaitTermination(4, TimeUnit.MINUTES));

		assertEquals(new BigDecimal("1000.00"), balance(ALICE));
		assertEquals(new BigDecimal("500.00"), balance(BOB));
		assertEquals(INITIAL_SUM, sumBalances());
		assertEquals(80, countRows("transfers"));
	}

	@Test
	void concurrentSameIdempotencyKey_singleDebitOthersReplay() throws Exception {
		String key = "concurrent-idem-" + UUID.randomUUID();
		int threads = 25;
		BigDecimal amount = new BigDecimal("5.00");
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch gate = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(pool.submit(() -> {
				gate.await();
				transferService.transfer(Optional.of(key), ALICE, BOB, amount);
				return null;
			}));
		}
		gate.countDown();
		for (Future<?> f : futures) {
			f.get(3, TimeUnit.MINUTES);
		}
		pool.shutdown();
		assertTrue(pool.awaitTermination(4, TimeUnit.MINUTES));

		assertEquals(new BigDecimal("995.00"), balance(ALICE));
		assertEquals(new BigDecimal("505.00"), balance(BOB));
		assertEquals(1, countRows("transfers"));
		assertEquals(1, countRows("transfer_idempotency"));
	}

	@Test
	void concurrentOversubscribe_someFailInsufficientBalance_conservesTotal() throws Exception {
		int threads = 80;
		BigDecimal chunk = new BigDecimal("20.00");
		ExecutorService pool = Executors.newFixedThreadPool(32);
		CountDownLatch gate = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(pool.submit(() -> {
				gate.await();
				transferService.transfer(ALICE, BOB, chunk);
				return null;
			}));
		}
		gate.countDown();
		int successes = 0;
		int failures = 0;
		for (Future<?> f : futures) {
			try {
				f.get(3, TimeUnit.MINUTES);
				successes++;
			}
			catch (ExecutionException e) {
				if (isInsufficientBalance(e)) {
					failures++;
				}
				else {
					throw new RuntimeException(e);
				}
			}
		}
		pool.shutdown();
		assertTrue(pool.awaitTermination(4, TimeUnit.MINUTES));

		assertEquals(INITIAL_SUM, sumBalances());
		assertEquals(50, countRows("transfers"));
		assertEquals(50, successes);
		assertEquals(30, failures);
		assertEquals(BigDecimal.ZERO.setScale(2), balance(ALICE));
		assertEquals(new BigDecimal("1500.00"), balance(BOB));
	}

	@Test
	void stressHighConcurrency_manySmallTransfers_completesWithoutDeadlock() throws Exception {
		int threads = 120;
		BigDecimal unit = new BigDecimal("0.50");
		ExecutorService pool = Executors.newFixedThreadPool(40);
		CountDownLatch gate = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			final boolean aliceToBob = (i % 2 == 0);
			futures.add(pool.submit(() -> {
				gate.await();
				if (aliceToBob) {
					transferService.transfer(ALICE, BOB, unit);
				}
				else {
					transferService.transfer(BOB, ALICE, unit);
				}
				return null;
			}));
		}
		gate.countDown();
		for (Future<?> f : futures) {
			f.get(5, TimeUnit.MINUTES);
		}
		pool.shutdown();
		assertTrue(pool.awaitTermination(6, TimeUnit.MINUTES));

		assertEquals(new BigDecimal("1000.00"), balance(ALICE));
		assertEquals(new BigDecimal("500.00"), balance(BOB));
		assertEquals(INITIAL_SUM, sumBalances());
		assertEquals(120, countRows("transfers"));
	}

	private BigDecimal balance(UUID accountId) {
		BigDecimal b = jdbcTemplate.queryForObject("SELECT balance FROM accounts WHERE id = ?",
				BigDecimal.class, accountId);
		return b.setScale(2);
	}

	private BigDecimal sumBalances() {
		BigDecimal s = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(balance), 0) FROM accounts", BigDecimal.class);
		return s.setScale(2);
	}

	private int countRows(String table) {
		Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*)::int FROM " + table, Integer.class);
		return n == null ? 0 : n;
	}

	private static boolean isInsufficientBalance(ExecutionException e) {
		for (Throwable t = e.getCause(); t != null; t = t.getCause()) {
			if (t instanceof InsufficientBalanceException) {
				return true;
			}
		}
		return false;
	}
}
