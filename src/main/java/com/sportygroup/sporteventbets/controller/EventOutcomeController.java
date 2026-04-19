package com.sportygroup.sporteventbets.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportygroup.sporteventbets.model.EventOutcome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
public class EventOutcomeController {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public EventOutcomeController(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/outcome")
    public void publishEventOutcome(@RequestBody EventOutcome eventOutcome) throws JsonProcessingException {
        String eventOutcomeJson = objectMapper.writeValueAsString(eventOutcome);
        kafkaTemplate.send("event-outcomes", eventOutcome.getEventId(), eventOutcomeJson);
    }
}