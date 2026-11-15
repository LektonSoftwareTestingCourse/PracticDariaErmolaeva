package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.config.CircuitBreakerFactory;
import com.processing.config.RetryFactory;
import com.processing.config.SwitchProperties;
import com.processing.exception.AuthorizationException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthorizationClientTest {

    private static final String AUTH_URL = "http://authorization:8083";
    private static final String AUTHORIZE_URL = AUTH_URL + "/api/internal/authorize";
    private static final String ROLLBACK_URL = AUTH_URL + "/api/internal/rollback";
    private static final int MAX_ATTEMPTS = 3;
    private static final String STAN = "000001";
    private static final String RRN = "612345678901";

    private MockRestServiceServer server;
    private CircuitBreaker circuitBreaker;
    private AuthorizationClient authorizationClient;

    @BeforeEach
    void setUp() {
        SwitchProperties properties = switchProperties();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        circuitBreaker = CircuitBreakerFactory.authorizationCircuitBreaker(properties);
        authorizationClient = new AuthorizationClient(
                properties,
                builder.build(),
                RetryFactory.authorizationRetry(properties),
                circuitBreaker);
    }

    @Test
    void shouldReturnApprovedResponseWhenAuthorizationApproves() {
        // Arrange
        server.expect(requestTo(AUTHORIZE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.stan").value(STAN))
                .andRespond(withSuccess(authorizationJson("APPROVED"), MediaType.APPLICATION_JSON));

        // Act
        AuthorizationResponse response = authorizationClient.authorize(request());

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_APPROVED);
        assertThat(response.rrn()).isEqualTo(RRN);
    }

    @Test
    void shouldReturnDeclinedResponseWithoutRetryWhenAuthorizationDeclines() {
        // Arrange
        server.expect(ExpectedCount.once(), requestTo(AUTHORIZE_URL))
                .andRespond(withSuccess(authorizationJson("DECLINED"), MediaType.APPLICATION_JSON));

        // Act
        AuthorizationResponse response = authorizationClient.authorize(request());

        // Assert
        assertThat(response.status()).isEqualTo(AuthorizationResponse.STATUS_DECLINED);
        server.verify();
    }

    @Test
    void shouldRetryAndThrowWhenAuthorizationReturnsUnknownStatus() {
        // Arrange
        server.expect(ExpectedCount.times(MAX_ATTEMPTS), requestTo(AUTHORIZE_URL))
                .andRespond(withSuccess(authorizationJson("PENDING"), MediaType.APPLICATION_JSON));

        // Act + Assert
        assertThatThrownBy(() -> authorizationClient.authorize(request()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("after 3 attempts")
                .hasMessageContaining("Invalid response from Authorization");
        server.verify();
    }

    @Test
    void shouldRetryAndThrowWhenAuthorizationRespondsWithServerError() {
        // Arrange
        server.expect(ExpectedCount.times(MAX_ATTEMPTS), requestTo(AUTHORIZE_URL))
                .andRespond(withServiceUnavailable());

        // Act + Assert
        assertThatThrownBy(() -> authorizationClient.authorize(request()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("STAN=" + STAN);
        server.verify();
    }

    @Test
    void shouldFailFastWithoutHttpCallWhenCircuitBreakerIsOpen() {
        // Arrange
        circuitBreaker.transitionToOpenState();

        // Act + Assert
        assertThatThrownBy(() -> authorizationClient.authorize(request()))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Circuit breaker open");
        server.verify();
    }

    @Test
    void shouldReturnRollbackResponseWhenAuthorizationAcceptsRollback() {
        // Arrange
        server.expect(requestTo(ROLLBACK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.rrn").value(RRN))
                .andExpect(jsonPath("$.pan").value("4000001234567890"))
                .andRespond(withSuccess(rollbackJson("APPROVED"), MediaType.APPLICATION_JSON));

        // Act
        RollbackResponse response = authorizationClient.rollback(request(), RRN);

        // Assert
        assertThat(response.status()).isEqualTo(RollbackResponse.STATUS_APPROVED);
        server.verify();
    }

    @Test
    void shouldReturnNullWhenRollbackFailsWithServerError() {
        // Arrange
        server.expect(requestTo(ROLLBACK_URL)).andRespond(withServerError());

        // Act
        RollbackResponse response = authorizationClient.rollback(request(), RRN);

        // Assert
        assertThat(response).isNull();
    }

    private static SwitchProperties switchProperties() {
        return new SwitchProperties(
                "1.0.0",
                Map.of("400000", "ISS001"),
                AUTH_URL,
                null,
                null,
                new SwitchProperties.HttpProperties(100, 100, 100),
                new SwitchProperties.RetryProperties(MAX_ATTEMPTS, List.of()),
                new SwitchProperties.CircuitBreakerSection(
                        new SwitchProperties.CircuitBreakerProperties(50, 10, 10, 60_000))
        );
    }

    private static AuthorizationRequest request() {
        return new AuthorizationRequest(
                "0100", STAN, "4000001234567890", "000000",
                new BigDecimal("1000"), "643", Instant.parse("2026-06-05T12:00:00Z"),
                "TERM0001", "POS", "MERCH0000000002", "5045", "ACQ002", "ISS001"
        );
    }

    private static String authorizationJson(String status) {
        return """
                {"mti":"0110","stan":"%s","rrn":"%s","authCode":"AB12CD",
                 "responseCode":"00","status":"%s","processingTimeMs":12}
                """.formatted(STAN, RRN, status);
    }

    private static String rollbackJson(String status) {
        return """
                {"rrn":"%s","responseCode":"00","status":"%s","processingTimeMs":5}
                """.formatted(RRN, status);
    }
}
