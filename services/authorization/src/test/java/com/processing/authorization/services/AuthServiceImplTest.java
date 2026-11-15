package com.processing.authorization.services;

import com.processing.authorization.client.BinLookupClient;
import com.processing.authorization.client.CardManagementClient;
import com.processing.authorization.constants.DeclineOutcome;
import com.processing.authorization.events.AuthorizationEventNotifier;
import com.processing.authorization.exceptions.CardNotFoundException;
import com.processing.authorization.repositories.LimitUsageRepository;
import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.cardmanagement.CardModel;
import com.processing.common.dto.cardmanagement.CardModelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String PAN = "4000003458730237";
    private static final String ISSUER_ID = "ISS001";
    private static final BigDecimal AMOUNT = new BigDecimal("990.99");
    private static final BigDecimal BALANCE = new BigDecimal("1000.00");
    private static final BigDecimal DAILY_LIMIT = new BigDecimal("15000.00");
    private static final BigDecimal MONTHLY_LIMIT = new BigDecimal("300000.00");
    // Фиксированное время транзакции: срок действия карты считается от него, а не от «now()».
    private static final Instant TRANSMISSION_TIME = Instant.parse("2026-06-15T12:00:00Z");
    private static final YearMonth VALID_EXPIRY = YearMonth.of(2028, 12);
    private static final Instant REQUEST_TIME = Instant.now();

    @Mock
    private LimitUsageRepository limitUsageRepository;

    @Mock
    private AuthorizationEventNotifier eventNotifier;

    @Mock
    private BinLookupClient binLookupClient;

    @Mock
    private CardManagementClient cardManagementClient;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                limitUsageRepository,
                eventNotifier,
                binLookupClient,
                cardManagementClient);
    }

    @Test
    void shouldApproveWhenCardIsActiveAndFundsAndLimitsAreSufficient() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(card(CardModelStatus.ACTIVE, VALID_EXPIRY, BALANCE));
        when(binLookupClient.getIssuerId(PAN)).thenReturn(Optional.of(ISSUER_ID));
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(1);

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT, TRANSMISSION_TIME), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_APPROVED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_APPROVED);
        verify(cardManagementClient).reserve(AMOUNT, response.rrn(), PAN);
    }

    @ParameterizedTest(name = "status={0} -> {1}/{2}")
    @CsvSource({
            "INACTIVE, CARD_INACTIVE, 05",
            "EXPIRED,  CARD_EXPIRED,  54",
            "BLOCKED,  CARD_BLOCKED,  05"
    })
    void shouldDeclineWhenCardStatusIsNotActive(
            CardModelStatus status, String expectedReason, String expectedCode) {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(card(status, VALID_EXPIRY, BALANCE));

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT, TRANSMISSION_TIME), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.declineReason()).isEqualTo(expectedReason);
        assertThat(response.responseCode()).isEqualTo(expectedCode);
    }

    @Test
    void shouldNotTouchLimitsAndReserveWhenCardIsBlocked() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(card(CardModelStatus.BLOCKED, VALID_EXPIRY, BALANCE));

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT, TRANSMISSION_TIME), REQUEST_TIME);

        // Assert
        assertThat(response.declineReason()).isEqualTo(DeclineOutcome.REASON_CARD_BLOCKED);
        verify(limitUsageRepository, never()).upsertLimitUsage(any(), any(), any(), any(), any());
        verify(cardManagementClient, never()).reserve(any(), any(), any());
    }

    @Test
    void shouldDeclineWhenCardNotFound() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenThrow(new CardNotFoundException(PAN));

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT, TRANSMISSION_TIME), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.declineReason()).isEqualTo("CARD_NOT_FOUND");
        assertThat(response.responseCode()).isEqualTo("14");
    }

    @Test
    void shouldDeclineWhenExpiryDateHasPassed() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(card(CardModelStatus.ACTIVE, YearMonth.of(2026, 5), BALANCE));

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT, TRANSMISSION_TIME), REQUEST_TIME);

        // Assert
        assertThat(response.declineReason()).isEqualTo(DeclineOutcome.REASON_CARD_EXPIRED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_CARD_EXPIRED);
    }

    @Test
    void shouldDeclineAsExpiredOnlyAfterLastDayOfExpiryMonth() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(card(CardModelStatus.ACTIVE, YearMonth.of(2026, 9), BALANCE));
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(1);

        // Act
        AuthorizationResponse lastDay = authService.authorize(
                request(AMOUNT, Instant.parse("2026-09-30T12:00:00Z")), REQUEST_TIME);
        AuthorizationResponse nextDay = authService.authorize(
                request(AMOUNT, Instant.parse("2026-10-01T12:00:00Z")), REQUEST_TIME);

        // Assert
        assertThat(lastDay.declineReason()).isNotEqualTo(DeclineOutcome.REASON_CARD_EXPIRED);
        assertThat(nextDay.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(nextDay.declineReason()).isEqualTo(DeclineOutcome.REASON_CARD_EXPIRED);
        assertThat(nextDay.responseCode()).isEqualTo(AuthorizationResponse.CODE_CARD_EXPIRED);
    }

    @Test
    void shouldDeclineWithInsufficientFundsWhenAmountExceedsBalance() {
        // Arrange
        when(cardManagementClient.getCard(PAN))
                .thenReturn(card(CardModelStatus.ACTIVE, VALID_EXPIRY, new BigDecimal("500.00")));

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT, TRANSMISSION_TIME), REQUEST_TIME);

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.declineReason()).isEqualTo(DeclineOutcome.REASON_INSUFFICIENT_FUNDS);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_INSUFFICIENT_FUNDS);
        verify(limitUsageRepository, never()).upsertLimitUsage(any(), any(), any(), any(), any());
        verify(cardManagementClient, never()).reserve(any(), any(), any());
    }

    @Test
    void shouldDeclineWithExceedsLimitWhenLimitUsageIsNotUpdated() {
        // Arrange
        when(cardManagementClient.getCard(PAN)).thenReturn(card(CardModelStatus.ACTIVE, VALID_EXPIRY, BALANCE));
        when(limitUsageRepository.upsertLimitUsage(any(), any(), any(), any(), any())).thenReturn(0);

        // Act
        AuthorizationResponse response = authService.authorize(request(AMOUNT, TRANSMISSION_TIME), REQUEST_TIME);

        // Assert
        assertThat(response.declineReason()).isEqualTo(DeclineOutcome.REASON_EXCEEDS_AMOUNT_LIMIT);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_EXCEEDS_LIMIT);
        verify(cardManagementClient, never()).reserve(any(), any(), any());
    }

    private static AuthorizationRequest request(BigDecimal amount, Instant transmissionTime) {
        return new AuthorizationRequest(
                "0100", "000001", PAN, "000000",
                amount, "643", transmissionTime,
                "TERM0001", "POS", "MERCH0000000002", "5411", "ACQ002", ISSUER_ID
        );
    }

    private static CardModel card(CardModelStatus status, YearMonth expiry, BigDecimal balance) {
        return new CardModel(
                UUID.randomUUID(), PAN, "400000", "IVAN IVANOV",
                expiry, status, "643",
                DAILY_LIMIT, MONTHLY_LIMIT, balance,
                ISSUER_ID, TRANSMISSION_TIME
        );
    }
}
