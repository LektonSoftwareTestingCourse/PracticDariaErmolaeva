package com.processing.apitests.transactions;

import com.processing.apitests.base.ApiSpec;
import com.processing.apitests.base.TestCardFactory;
import com.processing.apitests.base.TestDataFactory;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class TransactionsAuthTest {
    @Test
    @DisplayName("POST /api/transactions - APPROVED")
    void auth_approved_returns200WithContractBody() {
        // given
        String pan = TestCardFactory.createCard(1_000_000);
        String body = TestDataFactory.authRequestBody(pan, 150_000);

        given(ApiSpec.given())
                .body(body)
                // when
                .when().post("/api/transactions")
                // then
                .then()
                .statusCode(200)
                .body("mti", equalTo("0100"))
                .body("status", equalTo("APPROVED"))
                .body("responseCode", equalTo("00"))
                .body("rrn", matchesPattern("\\d{12}"))
                .body("authCode", matchesPattern("[A-Z0-9]{6}"))
                .body("processingTimeMs", greaterThanOrEqualTo(0))
                .body("declineReason", nullValue());
    }

    @Test
    @DisplayName("POST /api/transactions - CARD_BLOCKED - 200/05")
    void auth_blockedCard_returns05() {
        // given
        String pan = TestCardFactory.createCard(1_000_000);
        patchCardStatus(pan, "BLOCKED");

        given(ApiSpec.given())
                .body(TestDataFactory.authRequestBody(pan, 150_000))
                // when
                .when().post("/api/transactions")
                // then
                .then()
                .statusCode(200)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("05"))
                .body("declineReason", equalTo("CARD_BLOCKED"))
                .body("rrn", nullValue())
                .body("authCode", nullValue());
    }

    @Test
    @DisplayName("POST /api/transactions - CARD_INACTIVE - 200/05")
    void auth_inactiveCard_returns05() {
        // given
        String pan = TestCardFactory.createCard(1_000_000);
        patchCardStatus(pan, "INACTIVE");

        given(ApiSpec.given())
                .body(TestDataFactory.authRequestBody(pan, 150_000))
                // when
                .when().post("/api/transactions")
                // then
                .then()
                .statusCode(200)
                .body("responseCode", equalTo("05"))
                .body("declineReason", equalTo("CARD_INACTIVE"));
    }

    @Test
    @DisplayName("POST /api/transactions - INSUFFICIENT_FUNDS - 200/51")
    void auth_insufficientFunds_returns51() {
        // given
        String pan = TestCardFactory.createCard(10_000);

        given(ApiSpec.given())
                .body(TestDataFactory.authRequestBody(pan, 500_000))
                // when
                .when().post("/api/transactions")
                // then
                .then()
                .statusCode(200)
                .body("responseCode", equalTo("51"))
                .body("declineReason", equalTo("INSUFFICIENT_FUNDS"));
    }

    @Test
    @DisplayName("POST /api/transactions - CARD_NOT_FOUND - 200/14")
    void auth_unknownPan_returns14() {
        // given
        String unknownPan = "4000009999999999";

        given(ApiSpec.given())
                .body(TestDataFactory.authRequestBody(unknownPan, 150_000))
                // when
                .when().post("/api/transactions")
                // then
                .then()
                .statusCode(200)
                .body("responseCode", equalTo("14"))
                .body("declineReason", equalTo("CARD_NOT_FOUND"));
    }

    @ParameterizedTest(name = "поле {0} = {1} - 400")
    @CsvSource({
            "mti, 1234",
            "pan, 123",
            "currencyCode, RU",
            "mcc, 54111"
    })
    @DisplayName("POST /api/transactions - невалидные поля - 400")
    void auth_invalidFields_return400(String field, String value) {
        // given
        String pan = TestCardFactory.createCard(1_000_000);
        String validBody = TestDataFactory.authRequestBody(pan, 150_000);
        String brokenBody = validBody.replaceFirst(
                "\"" + field + "\":\\s*\"[^\"]*\"",
                "\"" + field + "\": \"" + value + "\"");

        given(ApiSpec.given())
                .body(brokenBody)
                // when
                .when().post("/api/transactions")
                // then
                .then()
                .statusCode(400)
                .body("error", equalTo("VALIDATION_ERROR"))
                .body("message", notNullValue());
    }

    @Test
    @DisplayName("POST /api/transactions - 429 при превышении rate-limit, заголовок Retry-After")
    void auth_rateLimitExceeded_returns429WithRetryAfter() {
        // given
        String client = "10.0.0.1";
        int maxRequests = 500;
        var request = given(ApiSpec.given())
                .header("X-Forwarded-For", client)
                .body("{}");

        // when
        Optional<Response> rateLimitedResponse;
        try (var requests = new ForkJoinPool(32)) {
            rateLimitedResponse = requests.submit(() -> IntStream.range(0, maxRequests)
                    .parallel()
                    .mapToObj(i -> given(request).when().post("/api/transactions"))
                    .filter(response -> response.statusCode() == 429)
                    .findAny())
                    .join();
        }

        // then
        rateLimitedResponse.orElseThrow(() -> new AssertionError("Ни один из запросов не получил HTTP 429"))
                .then()
                .statusCode(429)
                .header("Retry-After", "1")
                .body("error", equalTo("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("POST /api/internal/authorize - 422 и тело AuthorizationResponse")
    void internalAuthorize_insufficientFunds_returns422WithAuthorizationResponse() {
        // given
        String pan = TestCardFactory.createCard(10_000);

        given(ApiSpec.given().baseUri(System.getProperty("api.auth.url", "http://localhost:8083")))
                .body(TestDataFactory.authRequestBody(pan, 500_000))
                // when
                .when().post("/api/internal/authorize")
                // then
                .then()
                .statusCode(422)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("51"))
                .body("declineReason", equalTo("INSUFFICIENT_FUNDS"));
    }

    @Test
    @DisplayName("POST /api/internal/authorize - статус карты EXPIRED - 403/54")
    void internalAuthorize_expiredCardStatus_returns403() {
        // given
        String pan = TestCardFactory.createCard(1_000_000);
        patchCardStatus(pan, "EXPIRED");

        given(ApiSpec.given().baseUri(System.getProperty("api.auth.url", "http://localhost:8083")))
                .body(TestDataFactory.authRequestBody(pan, 100_000))
                // when
                .when().post("/api/internal/authorize")
                // then
                .then()
                .statusCode(403)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("54"))
                .body("declineReason", equalTo("CARD_EXPIRED"));
    }

    @Test
    @DisplayName("POST /api/internal/authorize - дата запроса больше срока активной карты - 403/54")
    void internalAuthorize_afterExpiryDate_returns403() {
        // given
        var card = given(ApiSpec.given())
                .body(TestDataFactory.cardBody("400000", 1_000_000))
                .when().post("/api/cards")
                .then().statusCode(201)
                .body("status", equalTo("ACTIVE"))
                .extract().jsonPath();
        String pan = card.getString("pan");
        var transactionTime = YearMonth.parse(card.getString("expiryDate")).plusMonths(1)
                .atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        String body = TestDataFactory.authRequestBody(pan, 100_000, TestDataFactory.uniqueStan(), transactionTime);

        given(ApiSpec.given().baseUri(System.getProperty("api.auth.url", "http://localhost:8083")))
                .body(body)
                // when
                .when().post("/api/internal/authorize")
                // then
                .then()
                .statusCode(403)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("54"))
                .body("declineReason", equalTo("CARD_EXPIRED"));
    }

    @ParameterizedTest(name = "POST /api/internal/authorize - дневной лимит {0}, месячный лимит {1} - 400/61")
    @CsvSource({
        "50_000, 500_000",
        "500_000, 50_000"
    })
    void internalAuthorize_exceedsDailyOrMonthlyLimit_returns400(long dailyLimit, long monthlyLimit) {
        // given
        String pan = given(ApiSpec.given())
                .body(TestDataFactory.cardBody("400000", 1_000_000, dailyLimit, monthlyLimit))
                .when().post("/api/cards")
                .then().statusCode(201).extract().path("pan");

        given(ApiSpec.given().baseUri(System.getProperty("api.auth.url", "http://localhost:8083")))
                .body(TestDataFactory.authRequestBody(pan, 100_000))
                // when
                .when().post("/api/internal/authorize")
                // then
                .then()
                .statusCode(400)
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("61"))
                .body("declineReason", equalTo("EXCEEDS_AMOUNT_LIMIT"));
    }

    @Test
    @DisplayName("POST /api/internal/rollback - 200, баланс карты восстановлен")
    void internalRollback_existingReservation_restoresCardBalance() {
        // given
        String pan = TestCardFactory.createCard(1_000_000);
        String authorizationUrl = System.getProperty("api.auth.url", "http://localhost:8083");
        String rrn = given(ApiSpec.given().baseUri(authorizationUrl))
                .body(TestDataFactory.authRequestBody(pan, 100_000))
                .when().post("/api/internal/authorize")
                .then().statusCode(200).body("status", equalTo("APPROVED")).extract().path("rrn");
        String body = """
                { "rrn": "%s", "pan": "%s", "amount": 100000 }
                """.formatted(rrn, pan);

        given(ApiSpec.given().baseUri(authorizationUrl))
                .body(body)
                // when
                .when().post("/api/internal/rollback")
                // then
                .then()
                .statusCode(200)
                .body("rrn", equalTo(rrn))
                .body("status", equalTo("APPROVED"))
                .body("responseCode", equalTo("00"));

        String balance = given(ApiSpec.given())
                .when().get("/api/cards/" + pan)
                .then().statusCode(200).extract().jsonPath().getString("availableBalance");
        assertThat(new BigDecimal(balance), comparesEqualTo(BigDecimal.valueOf(1_000_000)));
    }

    @Test
    @DisplayName("POST /api/internal/rollback - неизвестный RRN - 404/14")
    void internalRollback_unknownRrn_returns404() {
        // given
        String pan = TestCardFactory.createCard(1_000_000);
        String rrn = TestDataFactory.uniqueRrn();
        String body = """
                { "rrn": "%s", "pan": "%s", "amount": 100000 }
                """.formatted(rrn, pan);

        given(ApiSpec.given().baseUri(System.getProperty("api.auth.url", "http://localhost:8083")))
                .body(body)
                // when
                .when().post("/api/internal/rollback")
                // then
                .then()
                .statusCode(404)
                .body("rrn", equalTo(rrn))
                .body("status", equalTo("DECLINED"))
                .body("responseCode", equalTo("14"))
                .body("declineReason", equalTo("TRANSACTION_NOT_FOUND"));
    }

    private void patchCardStatus(String pan, String status) {
        given(ApiSpec.given())
                .body(TestDataFactory.patchStatusBody(status))
                .when().patch("/api/cards/" + pan)
                .then().statusCode(200);
    }
}
