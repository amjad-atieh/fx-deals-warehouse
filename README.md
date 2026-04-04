# FX Deals Import API (Bloomberg Data Warehouse)

## 1. Project Overview
The **FX Deals Import API** is a robust Spring Boot application designed for the Bloomberg FX deals data warehouse project. Its primary purpose is to accept Foreign Exchange (FX) deal details, strictly validate the incoming data, prevent the import of duplicate deals, and persistently store the records in a PostgreSQL database. A critical feature of the system is its batch processing capability, which strictly enforces a "no rollback" policy on batch failures, ensuring that valid records are saved even if a subset of a batch contains errors.

## 2. Tech Stack
- **Languages:** Java 25
- **Frameworks:** Spring Boot 3.5.x
- **Build Tool:** Maven
- **Database:** PostgreSQL (Production) / H2 (Testing)
- **Containerization:** Docker, Docker Compose
- **Testing SDKs & Tools:** JaCoCo (Coverage), K6 (Performance), Postman (API Testing)

## 3. Prerequisites
To run and develop this project locally, ensure you have the following installed:
- **Docker & Docker Compose**
- **Java 25** (optional if only running via Docker, required for local Maven builds)
- **Maven** (optional, included via wrapper/Docker)
- **Make** (optional, but recommended for convenient commands)

## 4. Quick Start (Running the Application)
A Makefile is provided to simplify common tasks (build, start, test, coverage).

The docker-compose.yml file serves as the deployment sample. It defines both the Spring Boot app and PostgreSQL services.

The easiest way to bootstrap the application along with its PostgreSQL dependency is using the provided `Makefile` commands:

- **Build and start the application and database:**
  \`\`\`bash
  make up
  \`\`\`
  *(Alternatively, you can run `docker-compose up -d --build`)*
- **Stop the containers:**
  \`\`\`bash
  make down
  \`\`\`
- **View application logs:**
  \`\`\`bash
  make logs
  \`\`\`

## 5. Running Tests
The project incorporates a comprehensive suite of unit and integration tests.

- **Run all tests (unit and integration):**
  \`\`\`bash
  make test
  \`\`\`
- **Run tests and generate JaCoCo test coverage report:**
  \`\`\`bash
  make coverage
  \`\`\`
  *The report will be available at: `target/site/jacoco/index.html`*
- **Alternative Maven command directly:**
  \`\`\`bash
  mvn clean test
  \`\`\`

## 6. JaCoCo Coverage & Exclusions
The API enforces a strict standard of **100% line coverage** for all critical business logic, including request parsing, data validation, deduplication, and the import flow.

**Exclusions:**

`FxDealsApplication.java`: The Spring Boot main entry point is excluded as it contains no business logic.


Uncovered Branch in DealService.importBatchDeals
The switch statement on response.status() shows 1 of 4 branches missing in JaCoCo. This is because the Java compiler generates an implicit branch for null when switching on an enum, even though DealResponse.status() is guaranteed never to be null:

DealResponse is constructed only within importSingleDeal, where status is always explicitly set to one of DealStatus.SAVED, DUPLICATE, or ERROR.

There is no code path that can produce a null status.

Adding a default branch would be unreachable and could not be tested without reflection or bytecode manipulation, which is outside the scope of normal unit/integration testing.

Therefore, this missing branch is a false positive introduced by the compiler's null-check. All three logical branches (SAVED, DUPLICATE, ERROR) are fully covered by existing unit and integration tests, satisfying the requirement of 100% coverage for actual business logic.

We choose not to add untestable defensive code (e.g., a default branch that logs an impossible condition) as it would artificially increase coverage without improving correctness.

## 7. Requirement-to-Test Mapping

| Requirement | Test Class / Method |
|-------------|---------------------|
| Accept deal details & persist | `DealServiceTest.shouldSaveNewDeal...`, `DealControllerIntegrationTest.shouldSaveSingleDeal` |
| Validate row structure | `DealControllerIntegrationTest.shouldFailWhenMissingField`, `shouldFailWithInvalidCurrency` |
| No duplicate import | `DealServiceTest.shouldReturnDuplicate...`, Integration test for duplicate checking |
| No rollback (batch) | `DealServiceTest.shouldProcessBatchDeals_withMixedResults`, Integration test for mixed batch |
| Partial success semantics | `DealServiceTest.shouldProcessBatchDeals_withMixedResults`, Integration test for mixed batch |
| API endpoint availability | Integrated REST Assured / MockMvc tests covering all exposed endpoints (`DealControllerIntegrationTest`) |

## 8. API Documentation

### 1. Create a Single Deal
**Endpoint:** `POST /api/deals`
**Description:** Accepts and persists a single FX deal.
**Request Body:**
\`\`\`json
{
  "dealUniqueId": "DEAL-1002",
  "fromCurrencyIsoCode": "USD",
  "toCurrencyIsoCode": "EUR",
  "dealTimestamp": "2026-04-04T10:15:30Z",
  "dealAmount": 50000.00
}
\`\`\`
**Status Codes:**
- `201 Created`: Successfully saved.
- `400 Bad Request`: Validation failure (e.g., missing fields, invalid ISO currency codes).
- `409 Conflict`: Duplicate deal found based on `dealUniqueId`.

### 2. Batch Import Deals
**Endpoint:** `POST /api/deals/batch`
**Description:** Accepts an array of FX deals. Processes them independently without rolling back the entire batch if a subset fails.
**Request Body:**
\`\`\`json
[
  {
    "dealUniqueId": "DEAL-2001",
    "fromCurrencyIsoCode": "GBP",
    "toCurrencyIsoCode": "USD",
    "dealTimestamp": "2026-04-04T11:00:00Z",
    "dealAmount": 10000.00
  },
  {
    "dealUniqueId": "DEAL-2002",
    "fromCurrencyIsoCode": "INVALID",
    "toCurrencyIsoCode": "USD",
    "dealTimestamp": "2026-04-04T11:05:00Z",
    "dealAmount": 20000.00
  }
]
\`\`\`
**Response (BatchImportResult):**
\`\`\`json
{
  "totalReceived": 2,
  "succeeded": 1,
  "duplicates": 0,
  "failures": 1,
  "details": [
    { "dealUniqueId": "DEAL-2001", "status": "SAVED", "message": "..." },
    { "dealUniqueId": "DEAL-2002", "status": "ERROR", "message": "Invalid currency code format" }
  ]
}
\`\`\`
**Status Codes:**
- `200 OK`: Batch processed (some or all may have failed, see response body for details).
- `400 Bad Request`: Malformed JSON structure.

### 3. Retrieve a Deal
**Endpoint:** `GET /api/deals/{dealUniqueId}`
**Status Codes:**
- `200 OK`: Deal retrieved successfully.
- `404 Not Found`: Deal does not exist.

## 9. Postman Collection
To thoroughly test the API functionality using Postman:
1. Locate the `fx-deals.postman_collection.json` file in the root directory of this repository.
2. Import the file into Postman.
3. Set the environment variable `base_url` to `http://localhost:8080`.
4. Run the collection to verify all endpoints are functioning correctly.

## 10. Performance Testing with K6
Performance benchmarks have been established using K6 to ensure the system gracefully handles load.
- **To run the test:** 
  \`\`\`bash
  k6 run k6-script.js
  \`\`\`
- **Performance Summary:** The system comfortably processes concurrent deal requests with the 95th percentile (p95) response time **< 75ms** and an error rate of **0%**, demonstrating robust throughput suitable for data warehouse ingestion.

## 11. Assumptions & Design Decisions
- **Database Environments**: Integration tests utilize an **H2 embedded database** to optimize testing speed and pipeline isolation. The production schema runs exclusively on **PostgreSQL**.
- **No Batch Rollback**: The batch processing endpoint (`/api/deals/batch`) deliberately omits the Spring `@Transactional` annotation at the batch wrapper level to preserve valid records when partial errors occur.
- **Race Condition Handling & Deduplication**: To effectively mitigate race conditions and handle deduplication simultaneously, we rely on a PostgreSQL unique constraint on `deal_unique_id` paired with a fast pre-check, catching constraint violations gracefully.
