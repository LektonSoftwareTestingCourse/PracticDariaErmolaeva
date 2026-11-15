package com.processing.cardmanagement.services;

import com.processing.common.dto.annotations.validators.BinValidator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class BinValidatorTest {

    private final BinValidator binValidator = new BinValidator();

    @ParameterizedTest(name = "bin={0} -> {1}")
    @CsvSource({
            "40000,   false",
            "4000000, false",
            "4000A0,  false",
            "400000,  true"
    })
    void shouldAcceptOnlySixDigitBin(String bin, boolean expected) {
        // Act
        boolean valid = binValidator.isValid(bin, null);

        // Assert
        assertThat(valid).isEqualTo(expected);
    }
}
