package com.processing.apitests.base;

import static io.restassured.RestAssured.given;

public final class TestCardFactory {
    private TestCardFactory() {}

    public static String createCard() {
        return createCard(1_000_000);
    }

    public static String createCard(long initialBalance) {
        return given(ApiSpec.given())
                .body(TestDataFactory.cardBody("400000", initialBalance))
                .when().post("/api/cards")
                .then().statusCode(201)
                .extract().path("pan");
    }
}
