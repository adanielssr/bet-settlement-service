package com.sportygroup.sporteventbets.rocketmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportygroup.sporteventbets.model.Bet;
import com.sportygroup.sporteventbets.repository.BetRepository;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

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
        consumeMessage(messageBody);
        return ConsumeResult.SUCCESS;
    }

    public void consumeMessage(String messageBody) {
        log.info("Received bet settlement message: {}", messageBody);
        try {
            Bet bet = objectMapper.readValue(messageBody, Bet.class);
            log.info("Settling bet: {}", bet.getBetId());
            bet.setStatus("SETTLED");
            betRepository.save(bet);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse bet settlement message", e);
        }
    }
}