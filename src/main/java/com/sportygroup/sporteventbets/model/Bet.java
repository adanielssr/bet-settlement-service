package com.sportygroup.sporteventbets.model;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
public class Bet {
    @Id
    private UUID id;
    private UUID userId;
    private UUID eventId;
    private String eventMarketId;
    private UUID selectedWinnerId;

    @Embedded
    private Money betAmount;

    @Enumerated(EnumType.STRING)
    private BetStatus status = BetStatus.PENDING;

    @Version
    private Long version;

    @CreatedDate
    private Instant createdDate;

    @LastModifiedDate
    private Instant updatedDate;

    private Instant settledDate;

    /**
     * Processes the event outcome for this bet, updating its status and settled date.
     * This method is idempotent: if the bet's status is no longer PENDING, it will not re-process
     * the outcome but will return the winning status based on its current state.
     *
     * @param eventOutcome The outcome of the event.
     * @return true if the bet is a winning bet, false otherwise.
     * @throws IllegalArgumentException if the event outcome's ID does not match this bet's event ID.
     */
    public boolean processEventOutcome(EventOutcome eventOutcome) {
        if (!this.eventId.equals(eventOutcome.eventId())) {
            throw new IllegalArgumentException("Event outcome ID does not match this bet's event ID.");
        }
        if (this.status != BetStatus.PENDING) {
            return this.status == BetStatus.WON;
        }

        this.settledDate = Instant.now();
        if (this.selectedWinnerId.equals(eventOutcome.eventWinnerId())) {
            this.status = BetStatus.WON;
            return true;
        } else {
            this.status = BetStatus.LOST;
            return false;
        }
    }

    /**
     * Settles the bet, updating its status to SETTLED and setting the settled date.
     * This method is idempotent: if the bet is already SETTLED, it will not re-process.
     * It expects the bet to be in a WON or LOST state before being settled.
     *
     * @throws IllegalStateException if the bet is still PENDING when attempting to settle.
     */
    public void settle() {
        if (this.status == BetStatus.PENDING) {
            throw new IllegalStateException("Bet cannot be settled if its outcome is still PENDING.");
        }
        if (this.status != BetStatus.SETTLED) {
            this.status = BetStatus.SETTLED;
            this.settledDate = Instant.now();
        }
    }
}
