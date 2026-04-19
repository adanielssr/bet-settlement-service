package com.sportygroup.sporteventbets;

import com.sportygroup.sporteventbets.model.Bet;
import com.sportygroup.sporteventbets.repository.BetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class DataInitializer implements CommandLineRunner {

    private final BetRepository betRepository;

    @Autowired
    public DataInitializer(BetRepository betRepository) {
        this.betRepository = betRepository;
    }

    @Override
    public void run(String... args) {
        // Sample bets
        betRepository.save(new Bet(UUID.randomUUID().toString(), "user1", "event1", "market1", "winner1", new BigDecimal("10.00")));
        betRepository.save(new Bet(UUID.randomUUID().toString(), "user2", "event1", "market1", "winner2", new BigDecimal("20.00")));
        betRepository.save(new Bet(UUID.randomUUID().toString(), "user3", "event2", "market2", "winner3", new BigDecimal("30.00")));
    }
}