package com.processing.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationRequestNormalizerTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource({
            "T1,        T1000000",
            "TERM0001,  TERM0001",
            "TERM00012, TERM0001"
    })
    void shouldNormalizeTerminalIdToEightChars(String terminalId, String expected) {
        // Act
        String normalized = AuthorizationRequestNormalizer.normalizeTerminalId(terminalId);

        // Assert
        assertThat(normalized).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource({
            // короче 15 - дополняется нулями справа
            "MERCH,                MERCH0000000000",
            // ровно 15 - без изменений
            "MERCH0000000002,      MERCH0000000002",
            // 16 символов с лишним нулём после MERCH - этот ноль удаляется
            "MERCH00000000002,     MERCH0000000002",
            // 16 символов без нуля после MERCH - обрезается
            "MERCH10000000002,     MERCH1000000000"
    })
    void shouldNormalizeMerchantIdToFifteenChars(String merchantId, String expected) {
        // Act
        String normalized = AuthorizationRequestNormalizer.normalizeMerchantId(merchantId);

        // Assert
        assertThat(normalized).isEqualTo(expected);
    }

    @ParameterizedTest(name = "terminalId=\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldKeepTerminalIdAsIsWhenItIsNullOrBlank(String terminalId) {
        // Act
        String normalized = AuthorizationRequestNormalizer.normalizeTerminalId(terminalId);

        // Assert
        assertThat(normalized).isEqualTo(terminalId);
    }

    @ParameterizedTest(name = "merchantId=\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldKeepMerchantIdAsIsWhenItIsNullOrBlank(String merchantId) {
        // Act
        String normalized = AuthorizationRequestNormalizer.normalizeMerchantId(merchantId);

        // Assert
        assertThat(normalized).isEqualTo(merchantId);
    }
}
