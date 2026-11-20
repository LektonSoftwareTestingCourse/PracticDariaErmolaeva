package com.processing.apitests.base;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public final class TestDataFactory {
    private TestDataFactory() {}

    public static String uniqueStan() {
        return String.format("%06d", Math.abs(UUID.randomUUID().hashCode()) % 1_000_000);
    }

    public static String uniqueRrn() {
        return String.format("%012d", Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 1_000_000_000_000L);
    }

    public static String authRequestBody(String pan, long amount) {
        return authRequestBody(pan, amount, uniqueStan());
    }

    public static String authRequestBody(String pan, long amount, String stan) {
        return authRequestBody(pan, amount, stan, Instant.now());
    }

    public static String authRequestBody(String pan, long amount, String stan, Instant transmissionDateTime) {
        return """
            {
              "mti": "0100",
              "stan": "%s",
              "pan": "%s",
              "processingCode": "000000",
              "amount": %d,
              "currencyCode": "643",
              "transmissionDateTime": "%s",
              "terminalId": "TERM0001",
              "terminalType": "POS",
              "merchantId": "MERCH0000000001",
              "mcc": "5411",
              "acquirerId": "ACQ001",
              "issuerId": "ISS001"
            }
            """.formatted(stan, pan, amount, transmissionDateTime);
    }

    public static String cardBody(String bin, long initialBalance) {
        return cardBody(bin, initialBalance, 15_000_000, 300_000_000);
    }

    public static String cardBody(String bin, long initialBalance, long dailyLimit, long monthlyLimit) {
        return """
            {
              "bin": "%s",
              "cardholderName": "IVAN IVANOV",
              "currencyCode": "643",
              "dailyLimit": %d,
              "monthlyLimit": %d,
              "initialBalance": %d
            }
            """.formatted(bin, dailyLimit, monthlyLimit, initialBalance);
    }

    public static String patchStatusBody(String status) {
        return """
            { "status": "%s" }
            """.formatted(status);
    }

    public static String transactionBody(String id, String pan, String stan, long amount) {
        Instant timestamp = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return """
            {
              "id": "%s",
              "mti": "0100",
              "stan": "%s",
              "rrn": "000000000001",
              "pan": "%s",
              "processingCode": "000000",
              "amount": %d,
              "currencyCode": "643",
              "terminalId": "TERM0001",
              "terminalType": "POS",
              "merchantId": "MERCH0000000001",
              "mcc": "5411",
              "acquirerId": "ACQ001",
              "issuerId": "ISS001",
              "acquiringFee": 0,
              "status": "APPROVED",
              "declineReason": null,
              "authCode": "ABC123",
              "processingTimeMs": 10,
              "transmissionDateTime": "%s",
              "createdAt": "%s"
            }
            """.formatted(id, stan, pan, amount, timestamp, timestamp);
    }
}
