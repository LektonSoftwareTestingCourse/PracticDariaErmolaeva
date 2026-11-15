package com.processing.gateway.validation;

import com.processing.common.dto.authorization.AuthorizationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionRequestValidatorTest {

    private static final String PAN = "4000003458730237";
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");
    private static final Instant TRANSMISSION_TIME = Instant.parse("2026-06-15T12:00:00Z");

    private final TransactionRequestValidator validator = new TransactionRequestValidator();

    @Test
    void shouldAcceptRequestWhenAllFieldsAreValid() {
        // Arrange
        AuthorizationRequest request = validRequest().build();

        // Act + Assert
        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectWhenRequestIsNull() {
        // Act + Assert
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Request body is required");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void shouldRejectWhenFieldViolatesGatewayRules(String scenario, AuthorizationRequest request, String message) {
        // Act + Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage(message);
    }

    @ParameterizedTest(name = "amount={0}")
    @ValueSource(strings = {"0.00", "-0.01"})
    void shouldRejectWhenAmountIsNotPositive(BigDecimal amount) {
        // Arrange
        AuthorizationRequest request = validRequest().amount(amount).build();

        // Act + Assert
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage("Field 'amount' must be > 0");
    }

    @Test
    void shouldAcceptWhenAmountIsMinimalPositive() {
        // Arrange
        AuthorizationRequest request = validRequest().amount(new BigDecimal("0.01")).build();

        // Act + Assert
        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("mti отсутствует", validRequest().mti(null).build(), "Field 'mti' is required"),
                Arguments.of("pan пустой", validRequest().pan(" ").build(), "Field 'pan' is required"),
                Arguments.of("время не задано", validRequest().transmissionDateTime(null).build(),
                        "Field 'transmissionDateTime' is required"),
                Arguments.of("mti не 0100", validRequest().mti("0400").build(), "Field 'mti' must be '0100'"),
                Arguments.of("pan 15 цифр", validRequest().pan("400000345873023").build(),
                        "Field 'pan' must be exactly 16 digits"),
                Arguments.of("pan с буквой", validRequest().pan("400000345873023A").build(),
                        "Field 'pan' must be exactly 16 digits"),
                Arguments.of("amount отсутствует", validRequest().amount(null).build(), "Field 'amount' is required"),
                Arguments.of("валюта 2 символа", validRequest().currencyCode("64").build(),
                        "Field 'currencyCode' must be exactly 3 characters"),
                Arguments.of("mcc с буквой", validRequest().mcc("50A5").build(), "Field 'mcc' must be exactly 4 digits")
        );
    }

    private static AuthorizationRequest.AuthorizationRequestBuilder validRequest() {
        return AuthorizationRequest.builder()
                .mti("0100")
                .stan("000001")
                .pan(PAN)
                .processingCode("000000")
                .amount(AMOUNT)
                .currencyCode("643")
                .transmissionDateTime(TRANSMISSION_TIME)
                .terminalId("TERM0001")
                .terminalType("POS")
                .merchantId("MERCH0000000002")
                .mcc("5045")
                .acquirerId("ACQ002");
    }
}
