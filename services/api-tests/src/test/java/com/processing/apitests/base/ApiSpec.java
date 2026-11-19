package com.processing.apitests.base;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public final class ApiSpec {
    private ApiSpec() {}

    public static RequestSpecification given() {
    return new RequestSpecBuilder()
            .setBaseUri(Config.get("api.base.url"))
            .setContentType(ContentType.JSON)
            .build();
    }
}
