# Agent Guidelines: ITX Backend Technical Test

This repository contains the technical assessment for a **Senior Backend Engineer**. All AI coding agents operating in this repository must adhere to the engineering standards, architecture requirements, and spec-driven workflow described below.

---

## 1. Engineering Mission & Objectives

The goal is to develop a production-ready **Spring Boot application** that exposes a REST API providing the details of similar products for a given product ID.

- **Primary Goal**: Implement `GET /product/{productId}/similar` on port `5000`.
- **Target Contract**: Defined in [similarProducts.yaml](similarProducts.yaml).
- **Upstream Services**: Existing APIs documented in [existingApis.yaml](existingApis.yaml) (hosted on port `3001` via Simulado mock service).
- **Assessment Pillars**:
  1. **Code Clarity & Maintainability**: Clean, modular, idiomatic architecture (e.g., Hexagonal / Clean Architecture) with strong separation of concerns, comprehensive documentation, and comprehensive automated test coverage (unit + integration).
  2. **Performance**: Must handle high concurrency (evaluated with 200 constant virtual users in k6). Parallel fetching of product details, non-blocking I/O (Spring WebFlux / WebClient or Java Virtual Threads), connection pooling, and smart caching strategies.
  3. **Resilience**: Gracefully handle upstream service anomalies, including network delays (up to 50s), HTTP 404 (missing products), and HTTP 500 (internal server errors) without cascading failures or thread starvation. Timeouts, fallbacks, and circuit breakers must be considered.

---

## 2. Mandatory Workflow: Spec-Driven Development

Agents **must** follow the team's development lifecycle before writing production code. Consult the full guide at:
👉 **[workflow/backlog/user-guide.md](file:///c:/dev/repos/PHPCE/itx-backend-test/workflow/backlog/user-guide.md)**

### Golden Rule: Spec → Plan → Code
> **Code is the last artifact produced, never the first.** Do not jump straight to writing code or creating application classes without first establishing specs and tasks.

### Core Pipeline Stages:
1. **Discover (Knowledge → Mental Model)**:
   - Command: `/discover <topic>`
   - Outputs: [workflow/backlog/discovery/](file:///c:/dev/repos/PHPCE/itx-backend-test/workflow/backlog/discovery/)
   - Guided requirements clarification and context framing.
2. **Architect (Mental Model → Conceptual Model)**:
   - Commands: `/architect <topic>`, `/item-enrich <id>`, `/review-item <id>`
   - Outputs: [workflow/backlog/items/](file:///c:/dev/repos/PHPCE/itx-backend-test/workflow/backlog/items/)
   - Slices discovery into client-facing backlog items with Gherkin acceptance criteria (`Given / When / Then`).
3. **Design & Plan (Conceptual Model → Change Proposal)**:
   - Commands: `/opsx:propose <name>` (or `/opsx:explore` to investigate beforehand), followed by `/review-change <name>`
   - Outputs: `openspec/changes/<name>/` (`proposal.md`, `design.md`, delta specs, and `tasks.md`).
4. **Implement (Tasks → Code)**:
   - Command: `/opsx:apply`
   - Executes implementation task-by-task.
   - **Spec Drift Rule**: If a requirement is found to be incorrect or if code needs to diverge from the spec, **never diverge silently**. Run `/opsx:sync` to realign the specs first, then resume implementation.
5. **Quality & Delivery**:
   - Traceable commits conforming to [workflow/config.yaml](file:///c:/dev/repos/PHPCE/itx-backend-test/workflow/config.yaml).
   - Once implementation is complete, archive changes via `/opsx:archive`.

### Key Distinction: Items vs. Tasks
- **Backlog Items** (`workflow/backlog/items/<id>-<slug>.md`): Value-driven deliverables tracking **What & Why** with acceptance criteria.
- **Tasks** (`openspec/changes/<name>/tasks.md`): Engineering checklist tracking **How** to build the change step-by-step.

---

## 3. Technical Requirements & Standards

### 3.1 Technology Stack
- **Language**: Java 21 (LTS) (see [ADR-001](docs/technical-decisions.md#adr-001-java-version)).
- **Framework**: Spring Boot 3.x (Spring MVC with Virtual Threads `spring.threads.virtual.enabled=true`) (see [ADR-002](docs/technical-decisions.md#adr-002-framework-stack--concurrency-model-spring-mvc-with-virtual-threads-over-webflux)).
- **HTTP Client**: Spring `RestClient` with connection pooling, strict timeouts, and parallel fan-out via a virtual-thread executor.
- **Port**: Application **must** listen on port `5000` (`server.port=5000`).

### 3.2 Contracts & Integrations
- **Exposed API**:
  - `GET /product/{productId}/similar`
  - Returns `200 OK` with JSON array of `ProductDetail`:
    ```json
    [
      { "id": "2", "name": "Dress", "price": 19.99, "availability": true }
    ]
    ```
  - Returns `404 Not Found` when the root product does not exist.
  - Contract source: [similarProducts.yaml](similarProducts.yaml).
- **Upstream API** (Mocks on `http://localhost:3001` or configurable host):
  - `GET /product/{productId}/similarids` -> Returns `["2", "3", "4"]`.
  - `GET /product/{productId}` -> Returns `ProductDetail` or `404 Not Found`.
  - Upstream contract source: [existingApis.yaml](existingApis.yaml).

### 3.3 Non-Functional & Senior Architecture Expectations
- **Concurrency & Parallelism**:
  - Fetching similar IDs gives a list of product IDs. Detail queries for each similar ID **must be executed concurrently in parallel**, never sequentially, to minimize overall response latency.
- **Resilience & Fault Tolerance**:
  - **Delays**: The mock upstream simulates high latencies (e.g., 100ms, 1s, 5s, 50s). The application must enforce strict, reasonable client timeouts and prevent thread exhaustion.
  - **Downstream Failures**: What happens when a similar product returns 404 or 500? Handle errors defensively (e.g., filter out unavailable similar products or document fallback behavior according to contract specifications).
- **Caching**:
  - Utilize local/in-memory caching (e.g., Caffeine) for product details to avoid redundant roundtrips where appropriate, honoring availability/freshness considerations.
- **Observability**:
  - Include Spring Boot Actuator for health checks (`/actuator/health`) and metrics.
  - Meaningful, structured logs with correlation IDs.

---

## 4. Testing, Simulation & Performance Verification

The repository includes a complete test harness in [docker-compose.yaml](docker-compose.yaml):
- **Simulado**: Mock service on port `3001` configured with [shared/simulado/mocks.json](shared/simulado/mocks.json).
- **k6**: Load generator executing [shared/k6/test.js](shared/k6/test.js) (runs scenarios: `normal`, `notFound`, `error`, `slow`, `verySlow` with 200 concurrent VUs).
- **InfluxDB & Grafana**: Real-time metrics dashboard at `http://localhost:3000/d/Le2Ku9NMk/k6-performance-test`.

### Verification Commands:
```bash
# 1. Start test infrastructure and mocks
docker-compose up -d simulado influxdb grafana

# 2. Verify mocks are responding
curl http://localhost:3001/product/1/similarids

# 3. Start your Spring Boot application on port 5000

# 4. Run performance benchmark with k6
docker-compose run --rm k6 run scripts/test.js
```

---

## 5. Agent Operational Directives

1. **Follow the Spec-Driven Workflow**: Always begin new initiatives by consulting [workflow/backlog/user-guide.md](workflow/backlog/user-guide.md). Create discovery/item/change artifacts before modifying or generating code.
2. **Preserve Documentation Integrity**: Do not overwrite or erase existing project specifications, contracts, or test assets.
3. **Senior Engineering Standards**: Avoid simplistic or naive implementations. Focus on reactive patterns, fault tolerance, thread safety, testability, and clean architecture.
