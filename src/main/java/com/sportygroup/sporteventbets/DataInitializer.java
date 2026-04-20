package com.sportygroup.sporteventbets;

import com.sportygroup.sporteventbets.model.Bet;
import com.sportygroup.sporteventbets.model.BetStatus;
import com.sportygroup.sporteventbets.model.Money;
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
        UUID event1 = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID winner1 = UUID.randomUUID();
        UUID winner2 = UUID.randomUUID();

        betRepository.save(new Bet(UUID.randomUUID(), user1, event1, "market1", winner1, new Money(new BigDecimal("10.00"), "USD"), BetStatus.PENDING, null, null, null, null));
        betRepository.save(new Bet(UUID.randomUUID(), user2, event1, "market1", winner2, new Money(new BigDecimal("20.00"), "USD"), BetStatus.PENDING, null, null, null, null));
        betRepository.save(new Bet(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "market2", UUID.randomUUID(), new Money(new BigDecimal("30.00"), "USD"), BetStatus.PENDING, null, null, null, null));
    }
}
