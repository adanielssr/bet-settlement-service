package com.sportygroup.sporteventbets.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportygroup.sporteventbets.model.EventOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventOutcomeController.class)
class EventOutcomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void should_publish_event_outcome() throws Exception {
        // given
        EventOutcome eventOutcome = new EventOutcome("event1", "Football Match", "winner1");
        String eventOutcomeJson = objectMapper.writeValueAsString(eventOutcome);

        // when
        mockMvc.perform(post("/events/outcome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventOutcomeJson))
                .andExpect(status().isOk());

        // then
        verify(kafkaTemplate).send("event-outcomes", eventOutcome.getEventId(), eventOutcomeJson);
    }
}