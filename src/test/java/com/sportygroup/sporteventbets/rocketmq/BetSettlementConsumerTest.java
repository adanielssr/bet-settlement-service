package com.sportygroup.sporteventbets.rocketmq;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportygroup.sporteventbets.model.Bet;
import com.sportygroup.sporteventbets.model.BetStatus;
import com.sportygroup.sporteventbets.model.Money;
import com.sportygroup.sporteventbets.repository.BetRepository;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BetSettlementConsumerTest {

    @Mock
    private BetRepository betRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private BetSettlementConsumer betSettlementConsumer;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        Logger logger = (Logger) LoggerFactory.getLogger(BetSettlementConsumer.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(BetSettlementConsumer.class);
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    private MessageView createMockMessageView(String payload) {
        MessageView messageView = mock(MessageView.class);
        when(messageView.getBody()).thenReturn(ByteBuffer.wrap(payload.getBytes()));
        return messageView;
    }

    @Test
    void should_consume_bet_settlement_message_and_update_bet_status() throws Exception {
        // given
        UUID betId = UUID.randomUUID();
        Bet bet = new Bet(betId, UUID.randomUUID(), UUID.randomUUID(), "market1", UUID.randomUUID(), new Money(new BigDecimal("10.00"), "USD"), BetStatus.WON, 0L, null, null, null);
        String betJson = "{\"id\":\"" + betId + "\",\"userId\":\"" + bet.getUserId() + "\",\"eventId\":\"" + bet.getEventId() + "\",\"eventMarketId\":\"market1\",\"selectedWinnerId\":\"" + bet.getSelectedWinnerId() + "\",\"betAmount\":{\"amount\":10.00,\"currency\":\"USD\"},\"status\":\"WON\"}";

        when(objectMapper.readValue(betJson, Bet.class)).thenReturn(bet);
        when(betRepository.findById(betId)).thenReturn(Optional.of(bet)); // Return the actual bet object

        MessageView messageView = createMockMessageView(betJson);

        // when
        ConsumeResult result = betSettlementConsumer.consume(messageView);

        // then
        assertThat(result).isEqualTo(ConsumeResult.SUCCESS);
        verify(betRepository).findById(betId);
        verify(betRepository).save(bet);
        assertThat(bet.getStatus()).isEqualTo(BetStatus.SETTLED);
        assertThat(bet.getSettledDate()).isNotNull();
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).extracting(ILoggingEvent::getLevel).doesNotContain(Level.ERROR, Level.WARN);
        assertThat(logs).extracting(ILoggingEvent::getFormattedMessage).doesNotContain("Failed to parse bet settlement message");
    }

    @Test
    void should_handle_json_processing_exception_on_read_value() throws Exception {
        // given
        String invalidJson = "{invalid json}";
        when(objectMapper.readValue(anyString(), eq(Bet.class))).thenThrow(JsonProcessingException.class);
        MessageView messageView = createMockMessageView(invalidJson);

        // when
        ConsumeResult result = betSettlementConsumer.consume(messageView);

        // then
        assertThat(result).isEqualTo(ConsumeResult.FAILURE);
        verifyNoInteractions(betRepository);
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).extracting(ILoggingEvent::getLevel).contains(Level.ERROR);
        assertThat(logs).extracting(ILoggingEvent::getFormattedMessage).anyMatch(msg -> msg.contains("Failed to parse bet settlement message"));
    }

    @Test
    void should_handle_bet_not_found() throws Exception {
        // given
        UUID betId = UUID.randomUUID();
        Bet betInMessage = new Bet(betId, UUID.randomUUID(), UUID.randomUUID(), "market1", UUID.randomUUID(), new Money(new BigDecimal("10.00"), "USD"), BetStatus.WON, 0L, null, null, null);
        String betJson = "{\"id\":\"" + betId + "\"}"; // Only ID needed for lookup

        when(objectMapper.readValue(betJson, Bet.class)).thenReturn(betInMessage);
        when(betRepository.findById(betId)).thenReturn(Optional.empty());
        MessageView messageView = createMockMessageView(betJson);

        // when
        ConsumeResult result = betSettlementConsumer.consume(messageView);

        // then
        assertThat(result).isEqualTo(ConsumeResult.SUCCESS); // Message processed, just a warning
        verify(betRepository).findById(betId);
        verify(betRepository, never()).save(any(Bet.class));
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).extracting(ILoggingEvent::getLevel).contains(Level.WARN);
        assertThat(logs).extracting(ILoggingEvent::getFormattedMessage).anyMatch(msg -> msg.contains("Bet with ID " + betId + " not found"));
    }

    @Test
    void should_handle_optimistic_locking_failure() throws Exception {
        // given
        UUID betId = UUID.randomUUID();
        Bet bet = new Bet(betId, UUID.randomUUID(), UUID.randomUUID(), "market1", UUID.randomUUID(), new Money(new BigDecimal("10.00"), "USD"), BetStatus.WON, 0L, null, null, null);
        String betJson = "{\"id\":\"" + betId + "\"}";

        when(objectMapper.readValue(betJson, Bet.class)).thenReturn(bet);
        when(betRepository.findById(betId)).thenReturn(Optional.of(bet));
        doThrow(OptimisticLockingFailureException.class).when(betRepository).save(any(Bet.class));
        MessageView messageView = createMockMessageView(betJson);

        // when
        ConsumeResult result = betSettlementConsumer.consume(messageView);

        // then
        assertThat(result).isEqualTo(ConsumeResult.FAILURE); // Re-throw for retry
        verify(betRepository).findById(betId);
        verify(betRepository).save(bet);
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).extracting(ILoggingEvent::getLevel).contains(Level.WARN, Level.ERROR); // Expect WARN from inner, ERROR from outer
        assertThat(logs).extracting(ILoggingEvent::getFormattedMessage).anyMatch(msg -> msg.contains("Optimistic locking failure for bet " + betId));
        assertThat(logs).extracting(ILoggingEvent::getFormattedMessage).anyMatch(msg -> msg.contains("Failed to process RocketMQ message for bet settlement"));
    }

    @Test
    void should_handle_illegal_state_exception_from_bet_settle() throws Exception {
        // given
        UUID betId = UUID.randomUUID();
        // Bet is PENDING, so bet.settle() will throw IllegalStateException
        Bet bet = new Bet(betId, UUID.randomUUID(), UUID.randomUUID(), "market1", UUID.randomUUID(), new Money(new BigDecimal("10.00"), "USD"), BetStatus.PENDING, 0L, null, null, null);
        String betJson = "{\"id\":\"" + betId + "\"}";

        when(objectMapper.readValue(betJson, Bet.class)).thenReturn(bet);
        when(betRepository.findById(betId)).thenReturn(Optional.of(bet));
        MessageView messageView = createMockMessageView(betJson);

        // when
        ConsumeResult result = betSettlementConsumer.consume(messageView);

        // then
        assertThat(result).isEqualTo(ConsumeResult.SUCCESS); // Business rule violation, not a system failure for the message
        verify(betRepository).findById(betId);
        verify(betRepository, never()).save(any(Bet.class)); // Save should not be called
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).extracting(ILoggingEvent::getLevel).contains(Level.ERROR);
        assertThat(logs).extracting(ILoggingEvent::getFormattedMessage).anyMatch(msg -> msg.contains("Bet " + betId + " is in an invalid state (PENDING) for settlement"));
    }

    @Test
    void should_handle_general_exception_on_save() throws Exception {
        // given
        UUID betId = UUID.randomUUID();
        Bet bet = new Bet(betId, UUID.randomUUID(), UUID.randomUUID(), "market1", UUID.randomUUID(), new Money(new BigDecimal("10.00"), "USD"), BetStatus.WON, 0L, null, null, null);
        String betJson = "{\"id\":\"" + betId + "\"}";

        when(objectMapper.readValue(betJson, Bet.class)).thenReturn(bet);
        when(betRepository.findById(betId)).thenReturn(Optional.of(bet));
        doThrow(new RuntimeException("Database connection lost")).when(betRepository).save(any(Bet.class));
        MessageView messageView = createMockMessageView(betJson);

        // when
        ConsumeResult result = betSettlementConsumer.consume(messageView);

        // then
        assertThat(result).isEqualTo(ConsumeResult.FAILURE); // Re-throw for retry
        verify(betRepository).findById(betId);
        verify(betRepository).save(bet);
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).extracting(ILoggingEvent::getLevel).contains(Level.ERROR); // Expect ERROR from inner and outer
        assertThat(logs).extracting(ILoggingEvent::getFormattedMessage).anyMatch(msg -> msg.contains("Unexpected error settling bet " + betId));
        assertThat(logs).extracting(ILoggingEvent::getFormattedMessage).anyMatch(msg -> msg.contains("Failed to process RocketMQ message for bet settlement"));
    }
}
