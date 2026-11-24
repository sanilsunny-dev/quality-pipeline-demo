package com.example.customers;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integration test: the real service + a real PostgreSQL running in Docker.
 *
 * 1. @Container tells Testcontainers to ask Docker to start postgres:16 before the tests.
 * 2. @ServiceConnection hands the container's JDBC URL, user and password to Spring Boot.
 * 3. Flyway runs V1__create_customer.sql against the fresh database (so migrations are tested too).
 * 4. REST Assured calls the API over HTTP; the data really goes into PostgreSQL.
 * 5. When the tests finish, Testcontainers tells Docker to stop and delete the container.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    CustomerRepository repository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        repository.deleteAll(); // each test starts from a clean table
    }

    @Test
    void createsAndReadsCustomerFromPostgres() {
        Integer id = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Ada Lovelace\",\"email\":\"ada@example.com\"}")
            .when()
                .post("/customers")
            .then()
                .statusCode(201)
                .body("id", notNullValue())
                .extract().path("id");

        RestAssured.given()
            .when()
                .get("/customers/{id}", id)
            .then()
                .statusCode(200)
                .body("name", equalTo("Ada Lovelace"))
                .body("email", equalTo("ada@example.com"));
    }

    @Test
    void rejectsDuplicateEmailBecausePostgresEnforcesTheUniqueConstraint() {
        String body = "{\"name\":\"Grace Hopper\",\"email\":\"grace@example.com\"}";

        RestAssured.given().contentType(ContentType.JSON).body(body)
            .when().post("/customers")
            .then().statusCode(201);

        // The UNIQUE constraint lives in the real PostgreSQL schema, so the second insert fails.
        RestAssured.given().contentType(ContentType.JSON).body(body)
            .when().post("/customers")
            .then().statusCode(500);
    }

    @Test
    void returns404ForUnknownCustomer() {
        RestAssured.given()
            .when().get("/customers/{id}", 999999)
            .then().statusCode(404);
    }
}
