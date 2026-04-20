package com.sportygroup.sporteventbets.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportygroup.sporteventbets.model.Bet;
import com.sportygroup.sporteventbets.model.BetStatus;
import com.sportygroup.sporteventbets.model.EventOutcome;
import com.sportygroup.sporteventbets.repository.BetRepository;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public void consume(String message) throws JsonProcessingException {
        log.info("Received event outcome: {}", message);
        EventOutcome eventOutcome = objectMapper.readValue(message, EventOutcome.class);

        List<Bet> betsToSettle = betRepository.findByEventIdAndStatus(eventOutcome.eventId(), BetStatus.PENDING);

        for (Bet bet : betsToSettle) {
            try {
                boolean isWinningBet = bet.processEventOutcome(eventOutcome);
                betRepository.save(bet);

                if (isWinningBet) {
                    String betJson = objectMapper.writeValueAsString(bet);
                    Message rocketMessage = serviceProvider.newMessageBuilder()
                            .setTopic("bet-settlements")
                            .setBody(betJson.getBytes())
                            .setMessageGroup(bet.getUserId().toString())
                            .build();
                    final SendReceipt sendReceipt = rocketMQProducer.send(rocketMessage);
                    log.info("Sent winning bet {} to RocketMQ for settlement, messageId={}, user={}", bet.getId(), sendReceipt.getMessageId(), bet.getUserId());
                } else {
                    log.info("Bet {} for event {} is a losing bet, status set to LOST.", bet.getId(), bet.getEventId());
                }
            } catch (OptimisticLockingFailureException e) {
                log.info("Optimistic locking failure for bet {} during event outcome processing. This may indicate a concurrent update or message re-delivery.", bet.getId());
            } catch (IllegalArgumentException e) {
                log.error("Invalid argument during bet processing for bet {}: {}", bet.getId(), e.getMessage(), e);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize bet {} to JSON for RocketMQ: {}", bet.getId(), e.getMessage(), e);
            } catch (ClientException e) {
                log.error("Failed to send bet {} to RocketMQ: {}", bet.getId(), e.getMessage(), e);
            }
        }
    }
}
