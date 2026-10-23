package com.processing.service;

import com.processing.config.SwitchProperties;
import com.processing.exception.UnknownBinException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingServiceTest {

    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        routingService = new RoutingService(propertiesWithBinTable(Map.of(
                "400000", "ISS001",
                "510000", "ISS002"
        )));
    }

    @ParameterizedTest(name = "PAN {0} -> {1}")
    @CsvSource({
            "5100009876543210, ISS002",
            "400000,           ISS001"
    })
    void shouldReturnIssuerIdWhenBinIsInRoutingTable(String pan, String expectedIssuerId) {
        // Act
        String issuerId = routingService.getIssuerIdByPan(pan);

        // Assert
        assertThat(issuerId).isEqualTo(expectedIssuerId);
    }

    @Test
    void shouldThrowUnknownBinWhenBinIsNotInRoutingTable() {
        // Act + Assert
        assertThatThrownBy(() -> routingService.getIssuerIdByPan("9999991234567890"))
                .isInstanceOf(UnknownBinException.class)
                .hasMessageContaining("999999");
    }

    @ParameterizedTest(name = "PAN=\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"40000"})
    void shouldThrowUnknownBinWhenPanIsNullOrShorterThanBin(String pan) {
        // Act + Assert
        assertThatThrownBy(() -> routingService.getIssuerIdByPan(pan))
                .isInstanceOf(UnknownBinException.class)
                .hasMessageContaining("null-or-short");
    }

    private static SwitchProperties propertiesWithBinTable(Map<String, String> binRouting) {
        return new SwitchProperties("1.0.0", binRouting, null, null, null, null, null, null);
    }
}
