package com.sportygroup.sporteventbets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportygroup.sporteventbets.model.Bet;
import com.sportygroup.sporteventbets.model.EventOutcome;
import com.sportygroup.sporteventbets.repository.BetRepository;
import com.sportygroup.sporteventbets.rocketmq.BetSettlementConsumer;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"})
class E2EFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BetRepository betRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private Producer rocketMQProducer;

    @MockBean
    private PushConsumer rocketMQConsumer;

    @MockBean
    private ClientServiceProvider serviceProvider;

    @Autowired
    private BetSettlementConsumer betSettlementConsumer;

    @Test
    void should_process_event_outcome_and_settle_bet_e2e() throws Exception {
        // given
        when(serviceProvider.newMessageBuilder()).thenReturn(new org.apache.rocketmq.client.java.message.MessageBuilderImpl());

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        doAnswer(invocation -> {
            betSettlementConsumer.consumeMessage(StandardCharsets.UTF_8.decode(messageCaptor.getValue().getBody()).toString());
            return mock(SendReceipt.class);
        }).when(rocketMQProducer).send(messageCaptor.capture());

        Bet bet = new Bet(UUID.randomUUID().toString(), "user1", "event1", "market1", "winner1", new BigDecimal("10.00"));
        betRepository.save(bet);

        EventOutcome eventOutcome = new EventOutcome("event1", "Football Match", "winner1");
        String eventOutcomeJson = objectMapper.writeValueAsString(eventOutcome);

        // when
        mockMvc.perform(post("/events/outcome")
                        .contentType("application/json")
                        .content(eventOutcomeJson))
                .andExpect(status().isOk());

        // then
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Bet settledBet = betRepository.findById(bet.getBetId()).orElseThrow();
            assertThat(settledBet.getStatus()).isEqualTo("SETTLED");
        });
    }
}