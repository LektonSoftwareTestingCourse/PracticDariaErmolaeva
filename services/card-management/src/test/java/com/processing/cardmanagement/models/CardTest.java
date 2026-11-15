package com.processing.cardmanagement.models;

import com.processing.cardmanagement.exceptions.InsufficientFundsException;
import com.processing.cardmanagement.exceptions.RollbackAlreadySatisfiedException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardTest {

    private static final String PAN = "4000001234567899";
    private static final String RRN = "612345678901";

    @Test
    void shouldRejectSecondRollbackWhenReservationIsAlreadyRolledBack() {
        // Arrange
        Reservation reservation = new Reservation(PAN, BigDecimal.TEN, RRN);
        Reservation rolledBack = reservation.rolledBack(reservation.startRollback(BigDecimal.TEN));

        // Act + Assert
        assertThat(rolledBack.status()).isEqualTo(ReservationStatus.ROLLED_BACK);
        assertThatThrownBy(() -> rolledBack.startRollback(BigDecimal.TEN))
                .isInstanceOf(RollbackAlreadySatisfiedException.class);
    }

    @Test
    void shouldRejectReservationWhenAmountExceedsBalance() {
        // Act + Assert
        assertThatThrownBy(() -> activeCard().startReservation(new BigDecimal("990.99"), RRN))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void shouldDecreaseBalanceWhenReservationIsApplied() {
        // Arrange
        Card card = activeCard();
        Reservation reservation = card.startReservation(new BigDecimal("500.00"), RRN);

        // Act
        Card reserved = card.withReservation(reservation);

        // Assert
        assertThat(reserved.availableBalance()).isEqualByComparingTo("0");
    }

    private static Card activeCard() {
        return new Card(
                UUID.randomUUID(), PAN, "400000", "IVAN IVANOV", YearMonth.of(2028, 12), CardStatus.ACTIVE, "643",
                new BigDecimal("1000"), new BigDecimal("5000"), new BigDecimal("500.00"), "ISS001",
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
