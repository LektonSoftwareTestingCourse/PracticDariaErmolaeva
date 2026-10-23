package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.common.dto.transactionlogger.TransactionRequest;
import com.processing.common.dto.transactionlogger.TransactionStatus;
import com.processing.exception.AuthorizationException;
import com.processing.exception.UnknownBinException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    private static final String PAN = "4000001234567890";
    private static final String STAN = "000001";
    private static final String RRN = "612345678901";
    private static final String AUTH_CODE = "AB12CD";
    private static final String ISSUER_ID = "ISS001";
    private static final BigDecimal AMOUNT = new BigDecimal("1000");
    private static final BigDecimal ACQUIRING_FEE = new BigDecimal("15.50");
    private static final Instant TRANSMISSION_TIME = Instant.parse("2026-06-05T12:00:00Z");

    @Mock
    private RoutingService routingService;

    @Mock
    private AuthorizationClient authorizationClient;

    @Mock
    private AcquiringFeeClient acquiringFeeClient;

    @Mock
    private LoggerClient loggerClient;

    private RouteService routeService;

    @BeforeEach
    void setUp() {
        routeService = new RouteService(routingService, authorizationClient, acquiringFeeClient, loggerClient);
    }

    @Test
    void shouldReturnApprovedResponseWhenIssuerApprovesAndTransactionIsLogged() {
        // Arrange
        AuthorizationResponse approved = approvedResponse();
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(routedRequest())).thenReturn(approved);
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(validRequest());

        // Assert
        assertThat(response).isEqualTo(approved);
    }

    @Test
    void shouldPublishTransactionWithIssuerAndAcquiringFeeWhenAuthorizationApproves() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(routedRequest())).thenReturn(approvedResponse());
        when(acquiringFeeClient.fetchAcquiringFee(TRANSMISSION_TIME, STAN, PAN, "TERM0001", AMOUNT))
                .thenReturn(ACQUIRING_FEE);
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        routeService.route(validRequest());

        // Assert
        TransactionRequest logged = capturePublishedTransaction();
        assertThat(logged.status()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(logged.issuerId()).isEqualTo(ISSUER_ID);
        assertThat(logged.rrn()).isEqualTo(RRN);
        assertThat(logged.authCode()).isEqualTo(AUTH_CODE);
        assertThat(logged.acquiringFee()).isEqualByComparingTo(ACQUIRING_FEE);
        assertThat(logged.processingTimeMs()).isEqualTo(15);
        assertThat(logged.id()).isNotNull();
    }

    @Test
    void shouldLogZeroProcessingTimeWhenAuthorizationResponseHasNoProcessingTime() {
        // Arrange
        AuthorizationResponse withoutTime = new AuthorizationResponse(
                "0110", STAN, RRN, AUTH_CODE, "00", AuthorizationResponse.STATUS_APPROVED, null, null);
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(routedRequest())).thenReturn(withoutTime);
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        routeService.route(validRequest());

        // Assert
        assertThat(capturePublishedTransaction().processingTimeMs()).isZero();
    }

    @Test
    void shouldDeclineWithCardNotFoundWhenBinIsUnknown() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenThrow(new UnknownBinException("400000"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_CARD_NOT_FOUND);
        assertThat(response.declineReason()).isEqualTo("CARD_NOT_FOUND");
        assertThat(response.stan()).isEqualTo(STAN);
    }

    @Test
    void shouldPublishDeclinedTransactionWithoutIssuerWhenBinIsUnknown() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenThrow(new UnknownBinException("400000"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        routeService.route(validRequest());

        // Assert
        TransactionRequest logged = capturePublishedTransaction();
        assertThat(logged.status()).isEqualTo(TransactionStatus.DECLINED);
        assertThat(logged.issuerId()).isNull();
        assertThat(logged.acquiringFee()).isNull();
    }

    @Test
    void shouldDeclineWithGeneralCodeWhenAuthorizationServiceIsUnavailable() {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(routedRequest()))
                .thenThrow(new AuthorizationException(STAN, 3, "Connection refused"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_DECLINED_GENERAL);
        assertThat(response.declineReason()).isEqualTo("Authorization service unavailable");
    }

    @ParameterizedTest(name = "rollback outcome: {0}")
    @MethodSource("rollbackOutcomes")
    void shouldRollbackAndReturnSystemErrorWhenApprovedTransactionCannotBePublished(
            String description, RollbackResponse rollbackOutcome) {
        // Arrange
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(routedRequest())).thenReturn(approvedResponse());
        when(loggerClient.log(any())).thenReturn(false);
        when(authorizationClient.rollback(routedRequest(), RRN)).thenReturn(rollbackOutcome);

        // Act
        AuthorizationResponse response = routeService.route(validRequest());

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_SERVICE_UNAVAILABLE);
        verify(authorizationClient).rollback(routedRequest(), RRN);
    }

    @Test
    void shouldReturnDeclineWithoutRollbackWhenDeclinedTransactionCannotBePublished() {
        // Arrange
        AuthorizationResponse declined = declinedResponse();
        when(routingService.getIssuerIdByPan(PAN)).thenReturn(ISSUER_ID);
        when(authorizationClient.authorize(routedRequest())).thenReturn(declined);
        when(loggerClient.log(any())).thenReturn(false);

        // Act
        AuthorizationResponse response = routeService.route(validRequest());

        // Assert
        assertThat(response).isEqualTo(declined);
        verify(authorizationClient, never()).rollback(any(), any());
    }

    @Test
    void shouldDeclineWhenPanIsTooShortToExtractBin() {
        // Arrange
        String shortPan = "4000";
        AuthorizationRequest request = request(shortPan, "TERM0001", "MERCH0000000002");
        when(routingService.getIssuerIdByPan(shortPan)).thenThrow(new UnknownBinException("null-or-short"));
        when(loggerClient.log(any())).thenReturn(true);

        // Act
        AuthorizationResponse response = routeService.route(request);

        // Assert
        assertThat(response.responseCode()).isEqualTo(AuthorizationResponse.CODE_CARD_NOT_FOUND);
    }

    private static Stream<Arguments> rollbackOutcomes() {
        return Stream.of(
                Arguments.of("no response", null),
                Arguments.of("approved", new RollbackResponse(RRN, "00", RollbackResponse.STATUS_APPROVED, null, 5)),
                Arguments.of("declined", new RollbackResponse(RRN, "14", RollbackResponse.STATUS_DECLINED,
                        "TRANSACTION_NOT_FOUND", 5))
        );
    }

    private TransactionRequest capturePublishedTransaction() {
        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(loggerClient).log(captor.capture());
        return captor.getValue();
    }

    private AuthorizationRequest validRequest() {
        return requestWithIds("TERM0001", "MERCH0000000002");
    }

    private AuthorizationRequest routedRequest() {
        return validRequest().withIssuerId(ISSUER_ID);
    }

    private AuthorizationRequest requestWithIds(String terminalId, String merchantId) {
        return request(PAN, terminalId, merchantId);
    }

    private AuthorizationRequest request(String pan, String terminalId, String merchantId) {
        return new AuthorizationRequest(
                "0100", STAN, pan, "000000",
                AMOUNT, "643", TRANSMISSION_TIME,
                terminalId, "POS", merchantId, "5045", "ACQ002", null
        );
    }

    private AuthorizationResponse approvedResponse() {
        return new AuthorizationResponse(
                "0110", STAN, RRN, AUTH_CODE, "00", AuthorizationResponse.STATUS_APPROVED, null, 15);
    }

    private AuthorizationResponse declinedResponse() {
        return new AuthorizationResponse(
                "0110", STAN, null, null, "51", AuthorizationResponse.STATUS_DECLINED, "INSUFFICIENT_FUNDS", 15);
    }
}
