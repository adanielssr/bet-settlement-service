package com.sportygroup.sporteventbets.repository;

import com.sportygroup.sporteventbets.model.Bet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BetRepository extends JpaRepository<Bet, String> {
    List<Bet> findByEventIdAndStatus(String eventId, String status);
}