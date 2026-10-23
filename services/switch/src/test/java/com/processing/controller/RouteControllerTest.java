package com.processing.controller;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.service.RouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteControllerTest {

    @Mock
    private RouteService routeService;

    private RouteController routeController;

    @BeforeEach
    void setUp() {
        routeController = new RouteController(routeService);
    }

    @ParameterizedTest(name = "PAN=\"{0}\"")
    @NullSource
    @ValueSource(strings = {"4000", "4000001234567890"})
    void shouldReturnOkWithRoutingResultForAnyPanFormat(String pan) {
        // Arrange
        AuthorizationRequest request = requestWithPan(pan);
        AuthorizationResponse routed = AuthorizationResponse.unknownBin("000001");
        when(routeService.route(request)).thenReturn(routed);

        // Act
        ResponseEntity<AuthorizationResponse> response = routeController.routeTransaction(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(routed);
    }

    private static AuthorizationRequest requestWithPan(String pan) {
        return new AuthorizationRequest(
                "0100", "000001", pan, "000000",
                new BigDecimal("1000"), "643", Instant.parse("2026-06-05T12:00:00Z"),
                "TERM0001", "POS", "MERCH0000000002", "5045", "ACQ002", null
        );
    }
}
