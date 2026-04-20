package com.sportygroup.sporteventbets.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BetTest {

    private final UUID BET_ID = UUID.randomUUID();
    private final UUID USER_ID = UUID.randomUUID();
    private final UUID EVENT_ID = UUID.randomUUID();
    private final String EVENT_MARKET_ID = "match_winner";
    private final UUID WINNER_ID = UUID.randomUUID();
    private final UUID LOSER_ID = UUID.randomUUID();
    private final Money BET_AMOUNT = new Money(new BigDecimal("10.00"), "USD");

    private Bet createPendingBet(UUID selectedWinnerId) {
        return new Bet(BET_ID, USER_ID, EVENT_ID, EVENT_MARKET_ID, selectedWinnerId, BET_AMOUNT, BetStatus.PENDING, 0L, null, null, null);
    }

    @Test
    void processEventOutcome_should_mark_bet_as_won_if_selected_winner_matches() {
        // given
        Bet bet = createPendingBet(WINNER_ID);
        EventOutcome eventOutcome = new EventOutcome(EVENT_ID, "Football Match", WINNER_ID);

        // when
        boolean isWinning = bet.processEventOutcome(eventOutcome);

        // then
        assertThat(isWinning).isTrue();
        assertThat(bet.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(bet.getSettledDate()).isNotNull();
    }

    @Test
    void processEventOutcome_should_mark_bet_as_lost_if_selected_winner_does_not_match() {
        // given
        Bet bet = createPendingBet(LOSER_ID);
        EventOutcome eventOutcome = new EventOutcome(EVENT_ID, "Football Match", WINNER_ID);

        // when
        boolean isWinning = bet.processEventOutcome(eventOutcome);

        // then
        assertThat(isWinning).isFalse();
        assertThat(bet.getStatus()).isEqualTo(BetStatus.LOST);
        assertThat(bet.getSettledDate()).isNotNull();
    }

    @Test
    void processEventOutcome_should_be_idempotent_if_already_won() {
        // given
        Bet bet = createPendingBet(WINNER_ID);
        EventOutcome eventOutcome = new EventOutcome(EVENT_ID, "Football Match", WINNER_ID);
        bet.processEventOutcome(eventOutcome); // First call, sets to WON
        Instant firstSettledDate = bet.getSettledDate();

        // when
        boolean isWinning = bet.processEventOutcome(eventOutcome); // Second call

        // then
        assertThat(isWinning).isTrue();
        assertThat(bet.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(bet.getSettledDate()).isEqualTo(firstSettledDate); // Date should not change
    }

    @Test
    void processEventOutcome_should_be_idempotent_if_already_lost() {
        // given
        Bet bet = createPendingBet(LOSER_ID);
        EventOutcome eventOutcome = new EventOutcome(EVENT_ID, "Football Match", WINNER_ID);
        bet.processEventOutcome(eventOutcome); // First call, sets to LOST
        Instant firstSettledDate = bet.getSettledDate();

        // when
        boolean isWinning = bet.processEventOutcome(eventOutcome); // Second call

        // then
        assertThat(isWinning).isFalse();
        assertThat(bet.getStatus()).isEqualTo(BetStatus.LOST);
        assertThat(bet.getSettledDate()).isEqualTo(firstSettledDate); // Date should not change
    }

    @Test
    void processEventOutcome_should_throw_exception_if_event_id_does_not_match() {
        // given
        Bet bet = createPendingBet(WINNER_ID);
        EventOutcome eventOutcome = new EventOutcome(UUID.randomUUID(), "Football Match", WINNER_ID); // Different event ID

        // when / then
        assertThatThrownBy(() -> bet.processEventOutcome(eventOutcome))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event outcome ID does not match this bet's event ID.");
        assertThat(bet.getStatus()).isEqualTo(BetStatus.PENDING); // Status should remain unchanged
        assertThat(bet.getSettledDate()).isNull();
    }

    @Test
    void settle_should_mark_bet_as_settled_if_won() {
        // given
        Bet bet = createPendingBet(WINNER_ID);
        EventOutcome eventOutcome = new EventOutcome(EVENT_ID, "Football Match", WINNER_ID);
        bet.processEventOutcome(eventOutcome); // Bet is now WON

        // when
        bet.settle();

        // then
        assertThat(bet.getStatus()).isEqualTo(BetStatus.SETTLED);
        assertThat(bet.getSettledDate()).isNotNull();
    }

    @Test
    void settle_should_mark_bet_as_settled_if_lost() {
        // given
        Bet bet = createPendingBet(LOSER_ID);
        EventOutcome eventOutcome = new EventOutcome(EVENT_ID, "Football Match", WINNER_ID);
        bet.processEventOutcome(eventOutcome); // Bet is now LOST

        // when
        bet.settle();

        // then
        assertThat(bet.getStatus()).isEqualTo(BetStatus.SETTLED);
        assertThat(bet.getSettledDate()).isNotNull();
    }

    @Test
    void settle_should_be_idempotent_if_already_settled() {
        // given
        Bet bet = createPendingBet(WINNER_ID);
        EventOutcome eventOutcome = new EventOutcome(EVENT_ID, "Football Match", WINNER_ID);
        bet.processEventOutcome(eventOutcome); // Bet is WON
        bet.settle(); // First call, sets to SETTLED
        Instant firstSettledDate = bet.getSettledDate();

        // when
        bet.settle(); // Second call

        // then
        assertThat(bet.getStatus()).isEqualTo(BetStatus.SETTLED);
        assertThat(bet.getSettledDate()).isEqualTo(firstSettledDate); // Date should not change
    }

    @Test
    void settle_should_throw_exception_if_bet_is_pending() {
        // given
        Bet bet = createPendingBet(WINNER_ID); // Bet is PENDING

        // when / then
        assertThatThrownBy(bet::settle)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Bet cannot be settled if its outcome is still PENDING.");
        assertThat(bet.getStatus()).isEqualTo(BetStatus.PENDING); // Status should remain unchanged
        assertThat(bet.getSettledDate()).isNull();
    }
}
