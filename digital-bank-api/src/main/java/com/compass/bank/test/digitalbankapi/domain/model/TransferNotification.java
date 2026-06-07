package com.compass.bank.test.digitalbankapi.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class TransferNotification {

	@Id
	private UUID id;

	@Column(name = "transfer_id", nullable = false)
	private UUID transferId;

	@Column(nullable = false, length = 4000)
	private String payload;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public TransferNotification(UUID id, UUID transferId, String payload, Instant createdAt) {
		this.id = id;
		this.transferId = transferId;
		this.payload = payload;
		this.createdAt = createdAt;
	}
}
