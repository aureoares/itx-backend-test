# Similar Products Specification

## Purpose
Provides a consolidated REST API for front-end clients to retrieve ordered details of products similar to a given product ID without issuing multiple client-side requests.

## Requirements

### Requirement: Similar Products Retrieval
The system SHALL expose an HTTP GET endpoint at `/product/{productId}/similar` on port 5000 that returns a JSON array of `ProductDetail` objects corresponding to the successfully resolved similar products of the requested root product ID. If any individual similar product detail request fails upstream (due to HTTP 404, HTTP 500, network error, or timeout), the system SHALL omit that product from the response array and return all remaining successfully fetched similar products without failing the overall request.

#### Scenario: Existing root product with similar products
- **WHEN** a client issues a `GET /product/{productId}/similar` request for an existing product with similar product IDs
- **THEN** the system returns HTTP status `200 OK`
- **AND** the response body is a JSON array of `ProductDetail` objects containing `id`, `name`, `price`, and `availability`
- **AND** the array elements preserve the exact similarity ranking order returned by upstream `/product/{productId}/similarids`

#### Scenario: Root product not found
- **WHEN** a client issues a `GET /product/{productId}/similar` request for a product ID that does not exist upstream (`/product/{productId}/similarids` returns 404)
- **THEN** the system returns HTTP status `404 Not Found`

#### Scenario: Root product with zero similar products
- **WHEN** a client issues a `GET /product/{productId}/similar` request for an existing product whose upstream `/product/{productId}/similarids` returns an empty array `[]`
- **THEN** the system returns HTTP status `200 OK`
- **AND** the response body is an empty JSON array `[]`

#### Scenario: Blank or whitespace product ID parameter
- **WHEN** a client issues a `GET /product/{productId}/similar` request where `productId` consists only of whitespace
- **THEN** the system returns HTTP status `400 Bad Request`

#### Scenario: Graceful degradation when an individual similar product is not found (404)
- **WHEN** product "4" has similar IDs ["1", "2", "5"] upstream
- **AND** products "1" and "2" exist, but product "5" returns HTTP 404 Not Found upstream
- **THEN** the system returns HTTP status `200 OK`
- **AND** the response array contains details for products "1" and "2" in original order
- **AND** product "5" is omitted from the response array

#### Scenario: Graceful degradation when an individual similar product fails upstream (500)
- **WHEN** product "5" has similar IDs ["1", "2", "6"] upstream
- **AND** products "1" and "2" exist, but product "6" returns HTTP 500 Internal Server Error upstream
- **THEN** the system returns HTTP status `200 OK`
- **AND** the response array contains details for products "1" and "2" in original order
- **AND** product "6" is omitted from the response array

#### Scenario: All similar products fail or time out upstream
- **WHEN** product "8" has similar IDs ["5", "6"] upstream
- **AND** all similar product lookups fail, return 404/500, or exceed client timeouts
- **THEN** the system returns HTTP status `200 OK`
- **AND** the response body is an empty JSON array `[]`

### Requirement: Concurrent Downstream Fan-out
The system SHALL execute downstream calls to fetch product details for each similar product ID in parallel across Java 21 Virtual Threads, avoiding sequential blocking.

#### Scenario: Parallel retrieval of multiple product details
- **WHEN** the upstream `/similarids` endpoint returns multiple similar product IDs
- **THEN** the system dispatches detail requests concurrently
- **AND** the total processing duration is bounded by the slowest individual request rather than the cumulative sum of sequential durations

### Requirement: Upstream Outage Handling
The system SHALL handle upstream connectivity or internal server failures gracefully when resolving root similar product IDs.

#### Scenario: Upstream service error on root similar IDs lookup
- **WHEN** the upstream service on port 3001 returns HTTP 500 or is unreachable during the `/product/{productId}/similarids` call
- **THEN** the system returns HTTP status `502 Bad Gateway`
- **AND** the response body contains a structured error payload detailing upstream unavailability

### Requirement: Upstream Call Timeout Protection
The system SHALL enforce a strict client read timeout of 2.5 seconds and connect timeout of 500 milliseconds on each upstream HTTP call to prevent slow or delayed downstream dependencies from exhausting connection pools or blocking server threads.

#### Scenario: Upstream delay exceeding client timeout is canceled and omitted
- **WHEN** product "3" has similar IDs ["100", "1000", "10000"] upstream
- **AND** product "100" responds within 1.0 second, while products "1000" and "10000" exceed the 2.5-second timeout threshold
- **THEN** the overall request is returned within 3.0 seconds
- **AND** the system returns HTTP status `200 OK`
- **AND** the response array contains details only for product "100"
- **AND** products "1000" and "10000" are omitted due to exceeding the 2.5-second timeout

### Requirement: In-Memory Performance Caching
The system SHALL cache similar product ID lists and product detail records in an in-memory Caffeine cache with bounded capacity (maximum 5,000 entries) and a write expiration TTL of 20 seconds to eliminate redundant network roundtrips under high concurrency.

#### Scenario: Repeated queries served from in-memory cache
- **WHEN** product "1" similar details have been fetched and cached
- **AND** a subsequent `GET /product/1/similar` request arrives within the 20-second cache TTL
- **THEN** the response is returned in under 50 milliseconds
- **AND** no new outgoing HTTP requests are issued to the upstream service on port 3001

#### Scenario: Cache entry eviction on expiration
- **WHEN** product "1" similar details are cached with a 20-second TTL
- **AND** more than 20 seconds elapse since the entry was cached
- **AND** a subsequent `GET /product/1/similar` request arrives
- **THEN** fresh data is retrieved from the upstream service and the cache is repopulated
