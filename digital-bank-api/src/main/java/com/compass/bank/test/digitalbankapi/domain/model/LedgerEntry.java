package com.compass.bank.test.digitalbankapi.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
@NoArgsConstructor
public class LedgerEntry {

	@Id
	private UUID id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@Column(name = "entry_type", nullable = false, length = 32)
	@Enumerated(EnumType.STRING)
	private EntryType entryType;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(name = "transfer_id", nullable = false)
	private UUID transferId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public LedgerEntry(UUID id, Account account, EntryType entryType, BigDecimal amount, UUID transferId,
			Instant createdAt) {
		this.id = id;
		this.account = account;
		this.entryType = entryType;
		this.amount = amount;
		this.transferId = transferId;
		this.createdAt = createdAt;
	}
}
