package com.compass.bank.test.digitalbankapi.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transfer_idempotency")
@Getter
@Setter
@NoArgsConstructor
public class TransferIdempotency {

	@Id
	@Column(name = "idempotency_key", length = 255)
	private String idempotencyKey;

	@Column(name = "from_account_id", nullable = false)
	private UUID fromAccountId;

	@Column(name = "to_account_id", nullable = false)
	private UUID toAccountId;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(name = "transfer_id")
	private UUID transferId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
}
