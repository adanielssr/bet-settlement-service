package com.sportygroup.sporteventbets.repository;

import com.sportygroup.sporteventbets.model.Bet;
import com.sportygroup.sporteventbets.model.BetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BetRepository extends JpaRepository<Bet, UUID> {
    List<Bet> findByEventIdAndStatus(UUID eventId, BetStatus status);
}
