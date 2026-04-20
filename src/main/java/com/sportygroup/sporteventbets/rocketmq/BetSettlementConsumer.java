package com.sportygroup.sporteventbets.rocketmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportygroup.sporteventbets.model.Bet;
import com.sportygroup.sporteventbets.model.BetStatus;
import com.sportygroup.sporteventbets.repository.BetRepository;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class BetSettlementConsumer implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(BetSettlementConsumer.class);

    private final BetRepository betRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public BetSettlementConsumer(BetRepository betRepository, ObjectMapper objectMapper) {
        this.betRepository = betRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public ConsumeResult consume(org.apache.rocketmq.client.apis.message.MessageView messageView) {
        String messageBody = StandardCharsets.UTF_8.decode(messageView.getBody()).toString();
        try {
            consumeMessage(messageBody);
            return ConsumeResult.SUCCESS;
        } catch (Exception e) {
            log.error("Failed to process RocketMQ message for bet settlement: {}", e.getMessage(), e);
            return ConsumeResult.FAILURE; // Indicate failure for RocketMQ to retry
        }
    }

    @Transactional
    public void consumeMessage(String messageBody) {
        log.info("Received bet settlement message: {}", messageBody);
        Bet messageBet; // This will hold the bet deserialized from the message, primarily for its ID

        try {
            messageBet = objectMapper.readValue(messageBody, Bet.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse bet settlement message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse bet settlement message", e); // Re-throw for RocketMQ retry
        }

        Optional<Bet> optionalBet = betRepository.findById(messageBet.getId());

        if (optionalBet.isPresent()) {
            Bet bet = optionalBet.get();
            try {
                bet.settle(); // Call the DDD method on the managed entity
                betRepository.save(bet); // Save the updated managed entity
                log.info("Bet {} successfully settled.", bet.getId());
            } catch (OptimisticLockingFailureException e) {
                log.warn("Optimistic locking failure for bet {} during settlement. This may indicate a concurrent update or message re-delivery.", bet.getId());
                throw e; // Re-throw to ensure transaction rollback and potential message retry
            } catch (IllegalStateException e) { // From bet.settle() if status is PENDING
                log.error("Bet {} is in an invalid state ({}) for settlement: {}", bet.getId(), bet.getStatus(), e.getMessage(), e);
                // Log and do not re-throw, as this message might be unprocessable due to business rules.
            } catch (Exception e) { // General catch for unexpected issues during save
                log.error("Unexpected error settling bet {}: {}", bet.getId(), e.getMessage(), e);
                throw new RuntimeException("Unexpected error settling bet " + bet.getId(), e); // Re-throw for retry
            }
        } else {
            log.warn("Bet with ID {} not found in the database for settlement. Message: {}", messageBet.getId(), messageBody);
            // Log a warning and do not re-throw, as this message might be for a non-existent bet.
        }
    }
}
