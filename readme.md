# Backend dev technical test
We want to offer a new feature to our customers showing similar products to the one they are currently seeing. To do this we agreed with our front-end applications to create a new REST API operation that will provide them the product detail of the similar products for a given one. [Here](./similarProducts.yaml) is the contract we agreed.

We already have an endpoint that provides the product Ids similar for a given one. We also have another endpoint that returns the product detail by product Id. [Here](./existingApis.yaml) is the documentation of the existing APIs.

**Create a Spring boot application that exposes the agreed REST API on port 5000.**

![Diagram](./assets/diagram.jpg "Diagram")

Note that _Test_ and _Mocks_ components are given, you must only implement _yourApp_.

## Testing and Self-evaluation
You can run the same test we will put through your application. You just need to have docker installed.

First of all, you may need to enable file sharing for the `shared` folder on your docker dashboard -> settings -> resources -> file sharing.

Then you can start the mocks and other needed infrastructure with the following command.
```
docker-compose up -d simulado influxdb grafana
```
Check that mocks are working with a sample request to [http://localhost:3001/product/1/similarids](http://localhost:3001/product/1/similarids).

To execute the test run:
```
docker-compose run --rm k6 run scripts/test.js
```
Browse [http://localhost:3000/d/Le2Ku9NMk/k6-performance-test](http://localhost:3000/d/Le2Ku9NMk/k6-performance-test) to view the results.

## Evaluation
The following topics will be considered:
- Code clarity and maintainability
- Performance
- Resilience

## Architecture & Engineering Standards
- **[System Topology & Boundaries](docs/topology.md)**: Ports, protocols, network diagrams, and service interactions.
- **[Architectural Decision Records (ADRs)](docs/technical-decisions.md)**:
  - [ADR-001: Java 21 LTS](docs/technical-decisions.md#adr-001-java-version)
  - [ADR-002: Framework Stack & Concurrency Model (Spring MVC + Virtual Threads)](docs/technical-decisions.md#adr-002-framework-stack--concurrency-model-spring-mvc-with-virtual-threads-over-webflux)
  - [ADR-003: Downstream Partial Failure Handling (Graceful Degradation)](docs/technical-decisions.md#adr-003-downstream-partial-failure-handling-graceful-degradation)
  - [ADR-004: Strict Upstream Call Timeout (2.5s)](docs/technical-decisions.md#adr-004-timeout-strategy-for-upstream-dependencies)
  - [ADR-005: Root Product 404 vs. Empty List Semantics](docs/technical-decisions.md#adr-005-root-product-404-vs-empty-list-semantics)
  - [ADR-006: In-Memory Caching Strategy (Caffeine)](docs/technical-decisions.md#adr-006-in-memory-caching-strategy-caffeine)
- **[Performance & Resilience Benchmark Report](docs/performance.md)**: Full k6 test metrics (200 constant VUs, 0% errors, 24.3ms avg latency).

## How to Run & Test the Application

### 1. Start Infrastructure & Upstream Mocks
Start the mock dependencies before running the application:
```bash
docker-compose up -d simulado influxdb grafana
```
Verify the mock is responding:
```bash
curl http://localhost:3001/product/1/similarids
```

### 2. Start the Application
The application listens on port `5000` (`server.port=5000`).

#### Option A: Running in Docker (Recommended — Zero Host Prerequisites)
This approach requires only Docker and guarantees an exact Java 21 LTS runtime without needing Java or Maven installed on the host machine:

```bash
# 1. Package the JAR using a Maven 3.9 + Java 21 container
docker run --rm -v "${PWD}:/app" -v "m2-cache:/root/.m2" -w /app maven:3.9.9-eclipse-temurin-21 mvn clean package -DskipTests

# 2. Run the application container connected to the compose network
docker run -d --name similar-products-app \
  --network itx-backend-test_default \
  -p 5000:5000 \
  -e UPSTREAM_CATALOG_BASE_URL=http://simulado:80 \
  -v "${PWD}/target:/app" \
  eclipse-temurin:21-jre java -jar /app/similar-products-0.0.1-SNAPSHOT.jar
```

Verify that the application is running:
```bash
curl http://localhost:5000/actuator/health
# Returns: {"status":"UP"}
```

#### Option B: Running Locally (Requires Java 21 LTS & Maven 3.9+)
If you have Java 21 and Maven pre-installed and available on your system `PATH`:

```bash
mvn spring-boot:run
```
Or package and run the JAR directly:
```bash
mvn clean package -DskipTests
java -jar target/similar-products-0.0.1-SNAPSHOT.jar
```

### 3. Test the Application

#### Automated Test Suite
Run the 41 automated tests (unit, integration with `MockRestServiceServer`, and live E2E):

```bash
# Via Docker (Recommended — zero local setup):
docker run --rm --network itx-backend-test_default -v "${PWD}:/app" -v "m2-cache:/root/.m2" -w /app maven:3.9.9-eclipse-temurin-21 mvn test

# Or using local Maven (if Java 21 is installed):
mvn test
```

#### Manual Endpoint Verification
Sample requests verifying core behaviors:
```bash
# Standard lookup (returns products 2, 3, 4 in similarity order)
curl -s http://localhost:5000/product/1/similar

# Timeout truncation (5s & 50s delays truncated at 2.5s; returns product 100 in <3s)
curl -s http://localhost:5000/product/3/similar

# In-memory cache hit (repeated query returns in <10ms from Caffeine cache)
curl -s http://localhost:5000/product/3/similar

# Defensive degradation (product 5 returns 404 upstream; omitted, returns products 1 and 2)
curl -s http://localhost:5000/product/4/similar

# Defensive degradation (product 6 returns 500 upstream; omitted, returns products 1 and 2)
curl -s http://localhost:5000/product/5/similar

# Root product not found (returns 404 Not Found)
curl -s -i http://localhost:5000/product/999/similar
```

#### Performance Benchmark (k6)
Run the k6 load generator (200 constant virtual users across all 5 scenarios):
```bash
docker-compose run --rm k6 run scripts/test.js
```
Telemetry results can be inspected in real time on Grafana at [http://localhost:3000/d/Le2Ku9NMk/k6-performance-test](http://localhost:3000/d/Le2Ku9NMk/k6-performance-test).

