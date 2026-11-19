package com.processing.apitests.transactions;

import com.processing.apitests.base.ApiSpec;
import com.processing.apitests.base.Config;
import com.processing.apitests.base.TestCardFactory;
import com.processing.apitests.base.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

class TransactionsSearchTest {

    @Test
    @DisplayName("search - по новой карте без транзакций - пустая выборка")
    void search_cardWithoutTransactions_returnsEmpty() {
        // given
        String pan = TestCardFactory.createCard();

        given(ApiSpec.given())
                .queryParam("pan", pan)
                // when
                .when().get("/api/transactions/search")
                // then
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/transaction-search-response.json"))
                .body("total", equalTo(0))
                .body("transactions", hasSize(0));
    }

    @Test
    @DisplayName("search - GPath-фильтр: среди результатов есть созданная транзакция")
    void search_filterByPan_findsCreatedTransaction() {
        // given
        String pan = TestCardFactory.createCard();

        String stan = TestDataFactory.uniqueStan();
        given(ApiSpec.given())
                .body(TestDataFactory.authRequestBody(pan, 100_000, stan))
                .when().post("/api/transactions")
                .then().statusCode(200);

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> given(ApiSpec.given())
                        .queryParam("pan", pan)
                        // when
                        .when().get("/api/transactions/search")
                        // then
                        .then()
                        .statusCode(200)
                        .body("transactions.findAll { it.pan == '%s' }.stan".formatted(pan), contains(stan)));
    }

    @Test
    @DisplayName("search limit=0 - 400")
    void search_invalidLimit_returns400() {
        // given
        given(ApiSpec.given())
                .queryParam("limit", 0)
                // when
                .when().get("/api/transactions/search")
                // then
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("search status=UNKNOWN - 400")
    void search_invalidStatus_returns400() {
        // given
        given(ApiSpec.given())
                .queryParam("status", "UNKNOWN")
                // when
                .when().get("/api/transactions/search")
                // then
                .then()
                .statusCode(400);
    }

    @ParameterizedTest(name = "search - {0}={1} - 400")
    @CsvSource({
        "limit, 501",
        "offset, -1",
        "dateFrom, invalid",
        "dateTo, invalid"
    })
    void search_invalidQueryParameter_returns400(String parameter, String value) {
        // given
        given(ApiSpec.given())
                .queryParam(parameter, value)
                // when
                .when().get("/api/transactions/search")
                // then
                .then()
                .statusCode(400)
                .body("message", containsString(parameter));
    }

    @Test
    @DisplayName("search - dateFrom > dateTo - 400")
    void search_reversedDateRange_returns400() {
        // given
        given(ApiSpec.given())
                .queryParam("dateFrom", "2026-11-02")
                .queryParam("dateTo", "2026-11-01")
                // when
                .when().get("/api/transactions/search")
                // then
                .then()
                .statusCode(400)
                .body("message", notNullValue());
    }

    @Test
    @DisplayName("search - limit и offset меняют страницу, total сохраняется")
    void search_pagination_returnsSecondOfTwoTransactions() {
        // given
        String pan = TestCardFactory.createCard();
        String firstId = storeTransaction(pan, TestDataFactory.uniqueStan());
        String secondId = storeTransaction(pan, TestDataFactory.uniqueStan());
        String firstPageId = given(ApiSpec.given())
                .queryParam("pan", pan)
                .queryParam("limit", 1)
                .when().get("/api/transactions/search")
                .then()
                .statusCode(200)
                .body("total", equalTo(2))
                .body("transactions", hasSize(1))
                .body("transactions[0].id", oneOf(firstId, secondId))
                .extract().path("transactions[0].id");

        given(ApiSpec.given())
                .queryParam("pan", pan)
                .queryParam("limit", 1)
                .queryParam("offset", 1)
                // when
                .when().get("/api/transactions/search")
                // then
                .then()
                .statusCode(200)
                .body("total", equalTo(2))
                .body("transactions", hasSize(1))
                .body("transactions[0].id", allOf(oneOf(firstId, secondId), not(equalTo(firstPageId))));
    }

    @Test
    @DisplayName("search - фильтры по PAN, статусу, дате и мерчанту применяются вместе")
    void search_combinedFilters_findsStoredTransaction() {
        // given
        String pan = TestCardFactory.createCard();
        LocalDate dateFrom = LocalDate.now(ZoneOffset.UTC);
        String id = storeTransaction(pan, TestDataFactory.uniqueStan());
        String otherId = storeTransaction(TestCardFactory.createCard(), TestDataFactory.uniqueStan());

        given(ApiSpec.given())
                .queryParam("pan", pan)
                .queryParam("status", "APPROVED")
                .queryParam("dateFrom", dateFrom.toString())
                .queryParam("dateTo", LocalDate.now(ZoneOffset.UTC).toString())
                .queryParam("merchantId", "MERCH0000000001")
                .queryParam("issuerId", "ISS001")
                .queryParam("mcc", "5411")
                // when
                .when().get("/api/transactions/search")
                // then
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/transaction-search-response.json"))
                .body("total", equalTo(1))
                .body("transactions.id", contains(id))
                .body("transactions.id", not(hasItem(otherId)));
    }

    @ParameterizedTest(name = "search - PAN и несовпадающий {0}={1} - пустая выборка")
    @CsvSource({
            "status, DECLINED",
            "dateFrom, 2099-01-01",
            "dateTo, 2000-01-01",
            "merchantId, UNKNOWN",
            "issuerId, UNKNOWN",
            "mcc, 9999",
            "declineReason, CARD_BLOCKED"
    })
    void search_filterDoesNotMatchStoredTransaction_returnsEmpty(String parameter, String value) {
        // given
        String pan = TestCardFactory.createCard();
        storeTransaction(pan, TestDataFactory.uniqueStan());

        given(ApiSpec.given())
                .queryParam("pan", pan)
                .queryParam(parameter, value)
                // when
                .when().get("/api/transactions/search")
                // then
                .then()
                .statusCode(200)
                .body("total", equalTo(0))
                .body("transactions", hasSize(0));
    }

    @Test
    @DisplayName("GET /api/transactions/export - 200, CSV с выбранной транзакцией")
    void export_filteredByPan_returnsCsvWithTransaction() {
        // given
        String pan = TestCardFactory.createCard();
        String stan = TestDataFactory.uniqueStan();
        String id = storeTransaction(pan, stan);
        String otherId = storeTransaction(TestCardFactory.createCard(), TestDataFactory.uniqueStan());

        given(ApiSpec.given())
                .queryParam("pan", pan)
                // when
                .when().get("/api/transactions/export")
                // then
                .then()
                .statusCode(200)
                .header("Content-Type", startsWith("text/csv"))
                .header("Content-Disposition", containsString("transactions.csv"))
                .body(startsWith("id,mti,stan,rrn,pan,"))
                .body(containsString(id))
                .body(containsString(stan))
                .body(not(containsString(otherId)));
    }

    private String storeTransaction(String pan, String stan) {
        String id = UUID.randomUUID().toString();
        given(ApiSpec.given().baseUri(Config.getLoggerUrl()))
                .body(TestDataFactory.transactionBody(id, pan, stan, 100_000))
                .when().post("/api/internal/log")
                .then().statusCode(201);
        return id;
    }
}
