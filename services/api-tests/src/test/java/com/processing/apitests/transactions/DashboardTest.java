package com.processing.apitests.transactions;

import com.processing.apitests.base.ApiSpec;
import com.processing.apitests.base.Config;
import com.processing.apitests.base.TestCardFactory;
import com.processing.apitests.base.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class DashboardTest {

    @Test
    @DisplayName("GET /api/dashboard/stats - 200, статистика содержит счётчики и суммы")
    void stats_returnsTransactionCountersAndAmounts() {
        // given
        given(ApiSpec.given())
                // when
                .when().get("/api/dashboard/stats")
                // then
                .then()
                .statusCode(200)
                .body("totalTransactions", greaterThanOrEqualTo(0))
                .body("approvedCount", greaterThanOrEqualTo(0))
                .body("declinedCount", greaterThanOrEqualTo(0))
                .body("totalAmount", notNullValue())
                .body("approvalRate", notNullValue());
    }

    @Test
    @DisplayName("GET /api/dashboard/recent - limit=1, возвращается последняя транзакция")
    void recent_limitOne_returnsNewlyStoredTransaction() {
        // given
        String pan = TestCardFactory.createCard();
        String id = UUID.randomUUID().toString();
        given(ApiSpec.given().baseUri(Config.getLoggerUrl()))
                .body(TestDataFactory.transactionBody(id, pan, TestDataFactory.uniqueStan(), 100_000))
                .when().post("/api/internal/log")
                .then().statusCode(201);

        given(ApiSpec.given())
                .queryParam("limit", 1)
                // when
                .when().get("/api/dashboard/recent")
                // then
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(id))
                .body("[0].pan", equalTo(pan));
    }

    @Test
    @DisplayName("GET /api/dashboard/charts - granularity=week - 400")
    void charts_unsupportedGranularity_returns400() {
        // given
        given(ApiSpec.given())
                .queryParam("granularity", "week")
                // when
                .when().get("/api/dashboard/charts")
                // then
                .then()
                .statusCode(400)
                .body("message", containsString("granularity"));
    }
}
