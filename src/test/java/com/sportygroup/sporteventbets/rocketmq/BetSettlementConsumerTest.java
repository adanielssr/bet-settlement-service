package com.sportygroup.sporteventbets.rocketmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportygroup.sporteventbets.model.Bet;
import com.sportygroup.sporteventbets.repository.BetRepository;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BetSettlementConsumerTest {

    @Mock
    private BetRepository betRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private BetSettlementConsumer betSettlementConsumer;

    @Test
    void should_consume_bet_settlement_message_and_update_bet_status() throws Exception {
        // given
        Bet bet = new Bet(UUID.randomUUID().toString(), "user1", "event1", "market1", "winner1", new BigDecimal("10.00"));
        String betJson = "{\"betId\":\"" + bet.getBetId() + "\",\"userId\":\"user1\",\"eventId\":\"event1\",\"eventMarketId\":\"market1\",\"eventWinnerId\":\"winner1\",\"betAmount\":10.00,\"status\":\"PENDING\"}";

        when(objectMapper.readValue(betJson, Bet.class)).thenReturn(bet);

        // Create a proper mock of MessageView
        MessageView messageView = mock(MessageView.class);
        when(messageView.getBody()).thenReturn(ByteBuffer.wrap(betJson.getBytes()));

        // when
        betSettlementConsumer.consume(messageView);

        // then
        verify(betRepository).save(bet);
    }
}