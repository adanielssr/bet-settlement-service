package com.sportygroup.sporteventbets.model;

import java.util.UUID;

public record EventOutcome(UUID eventId, String eventName, UUID eventWinnerId) {
}
