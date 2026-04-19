package com.sportygroup.sporteventbets.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportygroup.sporteventbets.model.Bet;
import com.sportygroup.sporteventbets.model.EventOutcome;
import com.sportygroup.sporteventbets.repository.BetRepository;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventOutcomeConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventOutcomeConsumer.class);

    private final BetRepository betRepository;
    private final Producer rocketMQProducer;
    private final ObjectMapper objectMapper;
    private final ClientServiceProvider serviceProvider;

    @Autowired
    public EventOutcomeConsumer(BetRepository betRepository, Producer rocketMQProducer, ObjectMapper objectMapper, ClientServiceProvider serviceProvider) {
        this.betRepository = betRepository;
        this.rocketMQProducer = rocketMQProducer;
        this.objectMapper = objectMapper;
        this.serviceProvider = serviceProvider;
    }

    @KafkaListener(topics = "event-outcomes", groupId = "event-group")
    public void consume(String message) throws JsonProcessingException {
        log.info("Received event outcome: {}", message);
        EventOutcome eventOutcome = objectMapper.readValue(message, EventOutcome.class);

        List<Bet> betsToSettle = betRepository.findByEventIdAndStatus(eventOutcome.getEventId(), "PENDING");

        for (Bet bet : betsToSettle) {
            if (bet.getEventWinnerId().equals(eventOutcome.getEventWinnerId())) {
                try {
                    String betJson = objectMapper.writeValueAsString(bet);
                    Message rocketMessage = serviceProvider.newMessageBuilder()
                            .setTopic("bet-settlements")
                            .setBody(betJson.getBytes())
                            // Ensure all bets for the same user are processed in order
                            .setMessageGroup(bet.getUserId())
                            .build();
                    final SendReceipt sendReceipt = rocketMQProducer.send(rocketMessage);
                    log.info("Sent bet {} to RocketMQ for settlement, messageId={}, user={}", bet.getBetId(), sendReceipt.getMessageId(), bet.getUserId());
                } catch (Exception e) {
                    log.error("Failed to send bet {} to RocketMQ", bet.getBetId(), e);
                }
            }
        }
    }
}