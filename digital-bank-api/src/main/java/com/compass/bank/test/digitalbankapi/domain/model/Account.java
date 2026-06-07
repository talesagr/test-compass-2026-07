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
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account {

	@Id
	private UUID id;

	@Column(name = "holder_name", nullable = false)
	private String holderName;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal balance;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Account(UUID id, String holderName, BigDecimal balance, Instant createdAt) {
		this.id = id;
		this.holderName = holderName;
		this.balance = balance;
		this.createdAt = createdAt;
	}
}
