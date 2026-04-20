package com.sportygroup.sporteventbets.kafka;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportygroup.sporteventbets.model.Bet;
import com.sportygroup.sporteventbets.model.BetStatus;
import com.sportygroup.sporteventbets.model.EventOutcome;
import com.sportygroup.sporteventbets.model.Money;
import com.sportygroup.sporteventbets.repository.BetRepository;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.message.MessageBuilder;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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

    @Mock
    private MessageBuilder messageBuilder;

    @InjectMocks
    private EventOutcomeConsumer eventOutcomeConsumer;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        Logger logger = (Logger) LoggerFactory.getLogger(EventOutcomeConsumer.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(EventOutcomeConsumer.class);
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    void should_consume_event_outcome_and_send_to_rocketmq() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        EventOutcome eventOutcome = new EventOutcome(eventId, "Football Match", winnerId);
        Bet bet = new Bet(UUID.randomUUID(), UUID.randomUUID(), eventId, "market1", winnerId, new Money(new BigDecimal("10.00"), "USD"), BetStatus.PENDING, 0L, null, null, null);
        String eventOutcomeJson = "{\"eventId\":\"" + eventId + "\",\"eventName\":\"Football Match\",\"eventWinnerId\":\"" + winnerId + "\"}";
        String betJson = "{\"id\":\"" + bet.getId() + "\",\"userId\":\"" + bet.getUserId() + "\",\"eventId\":\"" + bet.getEventId() + "\",\"eventMarketId\":\"market1\",\"selectedWinnerId\":\"" + bet.getSelectedWinnerId() + "\",\"betAmount\":{\"amount\":10.00,\"currency\":\"USD\"},\"status\":\"PENDING\"}";

        when(objectMapper.readValue(eventOutcomeJson, EventOutcome.class)).thenReturn(eventOutcome);
        when(betRepository.findByEventIdAndStatus(eventId, BetStatus.PENDING)).thenReturn(Collections.singletonList(bet));
        when(objectMapper.writeValueAsString(bet)).thenReturn(betJson);

        // Mock RocketMQ message building
        when(serviceProvider.newMessageBuilder()).thenReturn(messageBuilder);
        when(messageBuilder.setTopic(anyString())).thenReturn(messageBuilder);
        when(messageBuilder.setBody(any(byte[].class))).thenReturn(messageBuilder);
        when(messageBuilder.setMessageGroup(anyString())).thenReturn(messageBuilder);
        when(messageBuilder.build()).thenReturn(mock(Message.class));

        when(rocketMQProducer.send(any(Message.class))).thenReturn(mock(SendReceipt.class));

        // when
        eventOutcomeConsumer.consume(eventOutcomeJson);

        // then
        verify(betRepository).save(bet);
        verify(rocketMQProducer).send(any(Message.class));
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).extracting(ILoggingEvent::getMessage)
                .doesNotContain("Optimistic locking failure")
                .doesNotContain("Invalid argument during bet processing")
                .doesNotContain("Failed to serialize bet")
                .doesNotContain("Failed to send bet");
    }

    @Test
    void should_handle_optimistic_locking_failure() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        EventOutcome eventOutcome = new EventOutcome(eventId, "Football Match", winnerId);
        Bet bet = new Bet(UUID.randomUUID(), UUID.randomUUID(), eventId, "market1", winnerId, new Money(new BigDecimal("10.00"), "USD"), BetStatus.PENDING, 0L, null, null, null);
        String eventOutcomeJson = "{\"eventId\":\"" + eventId + "\",\"eventName\":\"Football Match\",\"eventWinnerId\":\"" + winnerId + "\"}";

        when(objectMapper.readValue(eventOutcomeJson, EventOutcome.class)).thenReturn(eventOutcome);
        when(betRepository.findByEventIdAndStatus(eventId, BetStatus.PENDING)).thenReturn(Collections.singletonList(bet));
        doThrow(OptimisticLockingFailureException.class).when(betRepository).save(any(Bet.class));

        // when
        eventOutcomeConsumer.consume(eventOutcomeJson);

        // then
        verify(betRepository).save(bet); // Still verify save was attempted
        verifyNoInteractions(rocketMQProducer); // No RocketMQ message should be sent
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).extracting(ILoggingEvent::getLevel).contains(Level.INFO);
        assertThat(logs).extracting(ILoggingEvent::getMessage).anyMatch(msg -> msg.contains("Optimistic locking failure for bet"));
    }

    @Test
    void should_handle_illegal_argument_exception_from_bet_processing() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        EventOutcome eventOutcome = new EventOutcome(eventId, "Football Match", winnerId);
        // Create a bet with a different eventId to trigger IllegalArgumentException in processEventOutcome
        Bet bet = new Bet(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "market1", winnerId, new Money(new BigDecimal("10.00"), "USD"), BetStatus.PENDING, 0L, null, null, null);
        String eventOutcomeJson = "{\"eventId\":\"" + eventId + "\",\"eventName\":\"Football Match\",\"eventWinnerId\":\"" + winnerId + "\"}";

        when(objectMapper.readValue(eventOutcomeJson, EventOutcome.class)).thenReturn(eventOutcome);
        when(betRepository.findByEventIdAndStatus(any(UUID.class), eq(BetStatus.PENDING))).thenReturn(Collections.singletonList(bet));

        // when
        eventOutcomeConsumer.consume(eventOutcomeJson);

        // then
        verify(betRepository, never()).save(any(Bet.class)); // Save should not be called
        verifyNoInteractions(rocketMQProducer); // No RocketMQ message should be sent
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).extracting(ILoggingEvent::getLevel).contains(Level.ERROR);
        assertThat(logs).extracting(ILoggingEvent::getMessage).anyMatch(msg -> msg.contains("Invalid argument during bet processing for bet"));
    }

    @Test
    void should_handle_json_processing_exception_when_serializing_bet() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        EventOutcome eventOutcome = new EventOutcome(eventId, "Football Match", winnerId);
        Bet bet = new Bet(UUID.randomUUID(), UUID.randomUUID(), eventId, "market1", winnerId, new Money(new BigDecimal("10.00"), "USD"), BetStatus.PENDING, 0L, null, null, null);
        String eventOutcomeJson = "{\"eventId\":\"" + eventId + "\",\"eventName\":\"Football Match\",\"eventWinnerId\":\"" + winnerId + "\"}";

        when(objectMapper.readValue(eventOutcomeJson, EventOutcome.class)).thenReturn(eventOutcome);
        when(betRepository.findByEventIdAndStatus(eventId, BetStatus.PENDING)).thenReturn(Collections.singletonList(bet));
        when(objectMapper.writeValueAsString(any(Bet.class))).thenThrow(JsonProcessingException.class);

        // when
        eventOutcomeConsumer.consume(eventOutcomeJson);

        // then
        verify(betRepository).save(bet); // Save should still be called as it happens before serialization
        verifyNoInteractions(rocketMQProducer); // No RocketMQ message should be sent
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).extracting(ILoggingEvent::getLevel).contains(Level.ERROR);
        assertThat(logs).extracting(ILoggingEvent::getMessage).anyMatch(msg -> msg.contains("Failed to serialize bet"));
    }

    @Test
    void should_handle_client_exception_when_sending_to_rocketmq() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        EventOutcome eventOutcome = new EventOutcome(eventId, "Football Match", winnerId);
        Bet bet = new Bet(UUID.randomUUID(), UUID.randomUUID(), eventId, "market1", winnerId, new Money(new BigDecimal("10.00"), "USD"), BetStatus.PENDING, 0L, null, null, null);
        String eventOutcomeJson = "{\"eventId\":\"" + eventId + "\",\"eventName\":\"Football Match\",\"eventWinnerId\":\"" + winnerId + "\"}";
        String betJson = "{\"id\":\"" + bet.getId() + "\",\"userId\":\"" + bet.getUserId() + "\",\"eventId\":\"" + bet.getEventId() + "\",\"eventMarketId\":\"market1\",\"selectedWinnerId\":\"" + bet.getSelectedWinnerId() + "\",\"betAmount\":{\"amount\":10.00,\"currency\":\"USD\"},\"status\":\"PENDING\"}";

        when(objectMapper.readValue(eventOutcomeJson, EventOutcome.class)).thenReturn(eventOutcome);
        when(betRepository.findByEventIdAndStatus(eventId, BetStatus.PENDING)).thenReturn(Collections.singletonList(bet));
        when(objectMapper.writeValueAsString(bet)).thenReturn(betJson);

        // Mock RocketMQ message building
        when(serviceProvider.newMessageBuilder()).thenReturn(messageBuilder);
        when(messageBuilder.setTopic(anyString())).thenReturn(messageBuilder);
        when(messageBuilder.setBody(any(byte[].class))).thenReturn(messageBuilder);
        when(messageBuilder.setMessageGroup(anyString())).thenReturn(messageBuilder);
        when(messageBuilder.build()).thenReturn(mock(Message.class));

        doThrow(ClientException.class).when(rocketMQProducer).send(any(Message.class));

        // when
        eventOutcomeConsumer.consume(eventOutcomeJson);

        // then
        verify(betRepository).save(bet); // Save should still be called as it happens before sending
        verify(rocketMQProducer).send(any(Message.class)); // Verify send was attempted
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs).extracting(ILoggingEvent::getLevel).contains(Level.ERROR);
        assertThat(logs).extracting(ILoggingEvent::getMessage).anyMatch(msg -> msg.contains("Failed to send bet"));
    }
}
