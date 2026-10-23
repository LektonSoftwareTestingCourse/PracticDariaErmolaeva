package com.processing.cardmanagement.services;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LuhnValidatorTest {

    private final LuhnValidator luhnValidator = new LuhnValidator();

    @ParameterizedTest(name = "bin={0}")
    @ValueSource(strings = {"400000", "510000", "220000"})
    void shouldGenerateSixteenDigitLuhnValidPanStartingWithBin(String bin) {
        // Act
        String pan = luhnValidator.generatePan(bin);

        // Assert
        assertThat(pan).hasSize(16).startsWith(bin).containsOnlyDigits();
        assertThat(luhnValidator.isValid(pan)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "4000001234567890",
            "4000001234567898",
    })
    void shouldRejectPanWhenLuhnChecksumIsBroken(String pan) {
        // Act
        boolean valid = luhnValidator.isValid(pan);

        // Assert
        assertThat(valid).isFalse();
    }
}
