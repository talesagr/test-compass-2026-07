package com.compass.bank.test.digitalbankapi.infrastructure.idempotency;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransferIdempotencyStore {

	private static final String INSERT_CLAIM = """
			INSERT INTO transfer_idempotency (idempotency_key, from_account_id, to_account_id, amount, transfer_id, created_at)
			VALUES (?, ?, ?, ?, NULL, ?)
			ON CONFLICT (idempotency_key) DO NOTHING
			""";

	private static final String UPDATE_COMPLETE = """
			UPDATE transfer_idempotency SET transfer_id = ? WHERE idempotency_key = ? AND transfer_id IS NULL
			""";

	private static final String SELECT = """
			SELECT from_account_id, to_account_id, amount, transfer_id FROM transfer_idempotency WHERE idempotency_key = ?
			""";

	private final JdbcTemplate jdbcTemplate;

	public TransferIdempotencyStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public int insertClaimIfAbsent(String idempotencyKey, UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
		return jdbcTemplate.update(INSERT_CLAIM, idempotencyKey, fromAccountId, toAccountId, amount,
				Timestamp.from(Instant.now()));
	}

	public int markCompleteIfStillPending(String idempotencyKey, UUID transferId) {
		return jdbcTemplate.update(UPDATE_COMPLETE, transferId, idempotencyKey);
	}

	public Optional<TransferIdempotencyRow> find(String idempotencyKey) {
		List<TransferIdempotencyRow> rows = jdbcTemplate.query(SELECT, (rs, rowNum) -> new TransferIdempotencyRow(
				rs.getObject("from_account_id", UUID.class), rs.getObject("to_account_id", UUID.class),
				rs.getBigDecimal("amount"), rs.getObject("transfer_id", UUID.class)), idempotencyKey);
		return rows.stream().findFirst();
	}
}
