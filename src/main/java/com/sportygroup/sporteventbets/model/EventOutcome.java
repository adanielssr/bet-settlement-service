package com.sportygroup.sporteventbets.model;

public class EventOutcome {
    private String eventId;
    private String eventName;
    private String eventWinnerId;

    public EventOutcome() {
    }

    public EventOutcome(String eventId, String eventName, String eventWinnerId) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventWinnerId = eventWinnerId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getEventWinnerId() {
        return eventWinnerId;
    }

    public void setEventWinnerId(String eventWinnerId) {
        this.eventWinnerId = eventWinnerId;
    }
}