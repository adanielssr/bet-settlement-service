package com.sportygroup.sporteventbets.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportygroup.sporteventbets.model.Bet;
import com.sportygroup.sporteventbets.model.EventOutcome;
import com.sportygroup.sporteventbets.repository.BetRepository;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventOutcomeConsumerTest {

    @Mock
    private BetRepository betRepository;

    @Mock
    private Producer rocketMQProducer;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ClientServiceProvider serviceProvider;

    @InjectMocks
    private EventOutcomeConsumer eventOutcomeConsumer;

    @Test
    void should_consume_event_outcome_and_send_to_rocketmq() throws Exception {
        // given
        when(serviceProvider.newMessageBuilder()).thenReturn(new org.apache.rocketmq.client.java.message.MessageBuilderImpl());

        EventOutcome eventOutcome = new EventOutcome("event1", "Football Match", "winner1");
        Bet bet = new Bet(UUID.randomUUID().toString(), "user1", "event1", "market1", "winner1", new BigDecimal("10.00"));
        String eventOutcomeJson = "{\"eventId\":\"event1\",\"eventName\":\"Football Match\",\"eventWinnerId\":\"winner1\"}";
        String betJson = "{\"betId\":\"" + bet.getBetId() + "\",\"userId\":\"user1\",\"eventId\":\"event1\",\"eventMarketId\":\"market1\",\"eventWinnerId\":\"winner1\",\"betAmount\":10.00,\"status\":\"PENDING\"}";

        when(objectMapper.readValue(eventOutcomeJson, EventOutcome.class)).thenReturn(eventOutcome);
        when(betRepository.findByEventIdAndStatus("event1", "PENDING")).thenReturn(Collections.singletonList(bet));
        when(objectMapper.writeValueAsString(bet)).thenReturn(betJson);
        when(rocketMQProducer.send(any(Message.class))).thenReturn(Mockito.mock(SendReceipt.class));

        // when
        eventOutcomeConsumer.consume(eventOutcomeJson);

        // then
        verify(rocketMQProducer).send(any(Message.class));
    }
}