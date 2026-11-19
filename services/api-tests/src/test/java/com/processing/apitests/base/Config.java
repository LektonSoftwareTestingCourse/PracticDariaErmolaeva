package com.processing.apitests.base;

import java.util.Properties;
import java.io.IOException;

public final class Config {
    private static final Properties PROPS = new Properties();
    static {
        try (var in = Config.class.getResourceAsStream("/config.properties")) {
            PROPS.load(in);
        } catch (IOException e) { throw new RuntimeException(e); }
    }
    public static String get(String key) { return PROPS.getProperty(key); }

    public static String getLoggerUrl() {
        return System.getProperty("api.logger.url", get("api.logger.url"));
    }
}
