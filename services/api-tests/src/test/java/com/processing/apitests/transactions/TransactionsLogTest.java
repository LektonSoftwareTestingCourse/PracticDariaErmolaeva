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

class TransactionsLogTest {

    @Test
    @DisplayName("POST /api/internal/log - новое сообщение - 201, status=stored")
    void log_newMessage_returns201() {
        // given
        String pan = TestCardFactory.createCard();
        String id = UUID.randomUUID().toString();

        given(ApiSpec.given().baseUri(Config.getLoggerUrl()))
                .body(TestDataFactory.transactionBody(id, pan, TestDataFactory.uniqueStan(), 100_000))
                // when
                .when().post("/api/internal/log")
                // then
                .then()
                .statusCode(201)
                .body("id", equalTo(id))
                .body("status", equalTo("stored"));
    }

    @Test
    @DisplayName("POST /api/internal/log - повтор с тем же id и данными - 200, без дубликата")
    void log_sameMessage_returns200NoDuplicate() {
        // given
        String pan = TestCardFactory.createCard();
        String id = UUID.randomUUID().toString();
        String body = TestDataFactory.transactionBody(id, pan, TestDataFactory.uniqueStan(), 100_000);

        given(ApiSpec.given().baseUri(Config.getLoggerUrl()))
                .body(body).when().post("/api/internal/log")
                .then().statusCode(201);

        given(ApiSpec.given().baseUri(Config.getLoggerUrl()))
                .body(body)
                // when
                .when().post("/api/internal/log")
                // then
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("amount", equalTo(100_000))
                .body("status", notNullValue());

        given(ApiSpec.given().baseUri(Config.getLoggerUrl()))
                .queryParam("pan", pan)
                .when().get("/api/transactions/search")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("transactions.id", contains(id));
    }

    @Test
    @DisplayName("POST /api/internal/log - тот же id, другие данные - 409")
    void log_sameIdDifferentData_returns409() {
        // given
        String pan = TestCardFactory.createCard();
        String id = UUID.randomUUID().toString();
        String stan = TestDataFactory.uniqueStan();

        given(ApiSpec.given().baseUri(Config.getLoggerUrl()))
                .body(TestDataFactory.transactionBody(id, pan, stan, 100_000))
                .when().post("/api/internal/log")
                .then().statusCode(201);

        given(ApiSpec.given().baseUri(Config.getLoggerUrl()))
                .body(TestDataFactory.transactionBody(id, pan, stan, 200_000))
                // when
                .when().post("/api/internal/log")
                // then
                .then()
                .statusCode(409)
                .body("error", notNullValue());
    }

    @Test
    @DisplayName("POST /api/internal/log - невалидный PAN - 400")
    void log_invalidPan_returns400WithFieldError() {
        // given
        String body = TestDataFactory.transactionBody(
                UUID.randomUUID().toString(), "123", TestDataFactory.uniqueStan(), 100_000);

        given(ApiSpec.given().baseUri(Config.getLoggerUrl()))
                .body(body)
                // when
                .when().post("/api/internal/log")
                // then
                .then()
                .statusCode(400)
                .body("error", equalTo("Validation error"))
                .body("message", containsString("pan"))
                .body("serviceName", equalTo("transaction-logger"));
    }
}
