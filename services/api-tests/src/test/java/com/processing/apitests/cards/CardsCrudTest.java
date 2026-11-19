package com.processing.apitests.cards;

import com.processing.apitests.base.ApiSpec;
import com.processing.apitests.base.TestCardFactory;
import com.processing.apitests.base.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class CardsCrudTest {

    @Test
    @DisplayName("POST /api/cards - 201")
    void createCard_returns201WithGeneratedPan() {
        // given
        given(ApiSpec.given())
                .body(TestDataFactory.cardBody("400000", 1_000_000))
                // when
                .when().post("/api/cards")
                // then
                .then()
                .statusCode(201)
                .body("pan", matchesPattern("\\d{16}"))
                .body("status", equalTo("ACTIVE"))
                .body("id", notNullValue())
                .body("issuerId", notNullValue());
    }

    @Test
    @DisplayName("POST /api/cards - 400 при невалидном BIN")
    void createCard_invalidBin_returns400() {
        // given
        given(ApiSpec.given())
                .body(TestDataFactory.cardBody("12", 1_000_000))
                // when
                .when().post("/api/cards")
                // then
                .then().statusCode(400);
    }

    @Test
    @DisplayName("GET /api/cards/{pan} - 404 для несуществующего PAN")
    void getCard_unknownPan_returns404() {
        // given
        given(ApiSpec.given())
                // when
                .when().get("/api/cards/4000009999999999")
                // then
                .then().statusCode(404);
    }

    @Test
    @DisplayName("GET /api/cards/{pan} - 400 при невалидном PAN")
    void getCard_invalidPan_returns400() {
        // given
        given(ApiSpec.given())
                // when
                .when().get("/api/cards/123")
                // then
                .then()
                .statusCode(400)
                .body("error", notNullValue());
    }

    @Test
    @DisplayName("DELETE /api/cards/{pan} - 204, повторный GET - 404")
    void deleteCard_returns204_thenGet404() {
        // given
        String pan = TestCardFactory.createCard();

        // when
        given(ApiSpec.given())
                .when().delete("/api/cards/" + pan)
                // then
                .then().statusCode(204);

        given(ApiSpec.given())
                .when().get("/api/cards/" + pan)
                .then().statusCode(404);
    }

    @Test
    @DisplayName("GET /api/cards/{pan} - 200, возвращает созданную карту")
    void getCard_existingPan_returns200WithCard() {
        // given
        String pan = TestCardFactory.createCard();

        given(ApiSpec.given())
                // when
                .when().get("/api/cards/" + pan)
                // then
                .then()
                .statusCode(200)
                .body("pan", equalTo(pan))
                .body("bin", equalTo("400000"))
                .body("status", equalTo("ACTIVE"))
                .body("currencyCode", equalTo("643"));
    }

    @Test
    @DisplayName("GET /api/cards - 200, фильтры по BIN и статусу с limit=1")
    void listCards_activeCardsByBin_returnsOneOnPage() {
        // given
        TestCardFactory.createCard();

        given(ApiSpec.given())
                .queryParam("bin", "400000")
                .queryParam("status", "ACTIVE")
                .queryParam("limit", 1)
                // when
                .when().get("/api/cards")
                // then
                .then()
                .statusCode(200)
                .body("total", greaterThanOrEqualTo(1))
                .body("cards", hasSize(1))
                .body("cards[0].bin", equalTo("400000"))
                .body("cards[0].status", equalTo("ACTIVE"));
    }

    @ParameterizedTest(name = "GET /api/cards - {0}={1} - 400")
    @CsvSource({
        "limit, 0",
        "offset, -1",
        "bin, 123"
    })
    void listCards_invalidParameter_returns400(String parameter, String value) {
        // given
        given(ApiSpec.given())
                .queryParam(parameter, value)
                // when
                .when().get("/api/cards")
                // then
                .then()
                .statusCode(400)
                .body("error", equalTo("ConstraintViolationException"))
                .body("message", containsString(parameter));
    }

    @Test
    @DisplayName("PATCH /api/cards/{pan} - 200, статус меняется на BLOCKED")
    void patchCard_blockedStatus_returns200AndPersistsStatus() {
        // given
        String pan = TestCardFactory.createCard();

        given(ApiSpec.given())
                .body(TestDataFactory.patchStatusBody("BLOCKED"))
                // when
                .when().patch("/api/cards/" + pan)
                // then
                .then()
                .statusCode(200)
                .body("pan", equalTo(pan))
                .body("status", equalTo("BLOCKED"));

        given(ApiSpec.given())
                .when().get("/api/cards/" + pan)
                .then()
                .statusCode(200)
                .body("status", equalTo("BLOCKED"));
    }

    @Test
    @DisplayName("POST /api/cards/generate - 201, сгенерирована одна карта с заданным BIN")
    void generateCards_singleCard_returns201() {
        // given
        String body = """
                { "count": 1, "bins": ["400000"] }
                """;

        given(ApiSpec.given())
                .body(body)
                // when
                .when().post("/api/cards/generate")
                // then
                .then()
                .statusCode(201)
                .body("generated", equalTo(1))
                .body("cards", hasSize(1))
                .body("cards[0].bin", equalTo("400000"))
                .body("cards[0].pan", matchesPattern("400000\\d{10}"));
    }

    @Test
    @DisplayName("POST /api/cards/generate - count=0 - 400")
    void generateCards_zeroCount_returns400() {
        // given
        String body = """
                { "count": 0, "bins": ["400000"] }
                """;

        given(ApiSpec.given())
                .body(body)
                // when
                .when().post("/api/cards/generate")
                // then
                .then()
                .statusCode(400)
                .body("error", equalTo("MethodArgumentNotValidException"))
                .body("message", containsString("greater than or equal to 1"));
    }
}
