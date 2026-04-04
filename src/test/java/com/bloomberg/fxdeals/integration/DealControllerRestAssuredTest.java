package com.bloomberg.fxdeals.integration;

import com.bloomberg.fxdeals.dto.DealRequest;
import com.bloomberg.fxdeals.repository.DealRepository;
import com.bloomberg.fxdeals.entity.Deal;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
class DealControllerRestAssuredTest {

    @LocalServerPort
    private int port;

    @Autowired
    private DealRepository dealRepository;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost:" + port;
        dealRepository.deleteAll();
    }

    private DealRequest createValidDeal(String id) {
        return new DealRequest(
                id,
                "USD",
                "EUR",
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                BigDecimal.valueOf(1000.5)
        );
    }

    @Test
    @DisplayName("Single Deal - Success")
    void shouldSaveSingleDeal() {
        DealRequest request = createValidDeal("RA-001");

        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/deals")
        .then()
            .statusCode(201)
            .body("status", equalTo("SAVED"))
            .body("dealUniqueId", equalTo("RA-001"));

        assertThat(dealRepository.findByDealUniqueId("RA-001")).isPresent();
        Deal savedDeal = dealRepository.findByDealUniqueId("RA-001").get();
        assertThat(savedDeal.getFromCurrencyIsoCode()).isEqualTo("USD");
        assertThat(savedDeal.getToCurrencyIsoCode()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Single Deal - Validation Failure (Missing Field)")
    void shouldFailWhenMissingDealId() {
        DealRequest request = new DealRequest(
                null, 
                "USD", 
                "EUR", 
                LocalDateTime.now(), 
                BigDecimal.valueOf(1000)
        );

        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/deals")
        .then()
            .statusCode(400)
            .body("details.dealUniqueId", notNullValue());
            
        // Assuming validation error map contains the field name as a key or within a message
    }

    @Test
    @DisplayName("Single Deal - Invalid Currency Format")
    void shouldFailWhenInvalidCurrency() {
        DealRequest request = new DealRequest(
                "RA-002", 
                "US", 
                "EUR", 
                LocalDateTime.now(), 
                BigDecimal.valueOf(1000)
        );

        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/deals")
        .then()
            .statusCode(400)
            .body(containsString("3-letter"));
    }

    @Test
    @DisplayName("Single Deal - Duplicate")
    void shouldFailWhenDuplicateDealId() {
        DealRequest request = createValidDeal("DUPE-001");

        // Insert first
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/deals")
        .then()
            .statusCode(201);

        // Send again
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/deals")
        .then()
            .statusCode(409)
            .body("status", equalTo("DUPLICATE"));

        assertThat(dealRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Batch Import - All Valid")
    void shouldImportValidBatch() {
        List<DealRequest> requests = List.of(
                createValidDeal("BATCH-001"),
                createValidDeal("BATCH-002"),
                createValidDeal("BATCH-003")
        );

        given()
            .contentType(ContentType.JSON)
            .body(requests)
        .when()
            .post("/api/deals/batch")
        .then()
            .statusCode(200)
            .body("succeeded", equalTo(3))
            .body("failures", equalTo(0))
            .body("duplicates", equalTo(0));

        assertThat(dealRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("Batch Import - Mixed Results (No Rollback)")
    void shouldHandleMixedBatch() {
        // Pre-insert
        DealRequest existing = createValidDeal("MIXED-001");
        given().contentType(ContentType.JSON).body(existing).post("/api/deals");

        List<DealRequest> requests = List.of(
                createValidDeal("MIXED-001"),
                createValidDeal("MIXED-002")
        );

        given()
            .contentType(ContentType.JSON)
            .body(requests)
        .when()
            .post("/api/deals/batch")
        .then()
            .statusCode(200)
            .body("succeeded", equalTo(1))
            .body("duplicates", equalTo(1))
            .body("failures", equalTo(0));

        assertThat(dealRepository.count()).isEqualTo(2);
        assertThat(dealRepository.findByDealUniqueId("MIXED-001")).isPresent();
        assertThat(dealRepository.findByDealUniqueId("MIXED-002")).isPresent();
    }

    @Test
    @DisplayName("Batch Import - Validation Error in One Item")
    void shouldHandleBatchWithValidationError() {
        List<DealRequest> requests = List.of(
                createValidDeal("ERR-001"),
                new DealRequest("ERR-002", null, "EUR", LocalDateTime.now(), BigDecimal.valueOf(100.0)),
                createValidDeal("ERR-003")
        );

        given()
            .contentType(ContentType.JSON)
            .body(requests)
        .when()
            .post("/api/deals/batch")
        .then()
            .statusCode(200)
            .body("succeeded", equalTo(2))
            .body("failures", equalTo(1));

        assertThat(dealRepository.count()).isEqualTo(2);
        assertThat(dealRepository.findByDealUniqueId("ERR-001")).isPresent();
        assertThat(dealRepository.findByDealUniqueId("ERR-003")).isPresent();
    }

    @Test
    @DisplayName("Get Deal - Found")
    void shouldGetDealById() {
        DealRequest request = createValidDeal("GET-001");
        given().contentType(ContentType.JSON).body(request).post("/api/deals");

        given()
            .pathParam("id", "GET-001")
        .when()
            .get("/api/deals/{id}")
        .then()
            .statusCode(200)
            .body("dealUniqueId", equalTo("GET-001"))
            .body("fromCurrencyIsoCode", equalTo("USD"));
    }

    @Test
    @DisplayName("Get Deal - Not Found")
    void shouldReturn404ForUnknownDeal() {
        given()
            .pathParam("id", "NON-EXISTENT")
        .when()
            .get("/api/deals/{id}")
        .then()
            .statusCode(404)
            .body("message", notNullValue());
    }
}