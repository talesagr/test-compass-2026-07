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
@Table(name = "transfers")
@Getter
@Setter
@NoArgsConstructor
public class Transfer {

	@Id
	private UUID id;

	@Column(name = "from_account_id", nullable = false)
	private UUID fromAccountId;

	@Column(name = "to_account_id", nullable = false)
	private UUID toAccountId;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Transfer(UUID id, UUID fromAccountId, UUID toAccountId, BigDecimal amount, Instant createdAt) {
		this.id = id;
		this.fromAccountId = fromAccountId;
		this.toAccountId = toAccountId;
		this.amount = amount;
		this.createdAt = createdAt;
	}
}
