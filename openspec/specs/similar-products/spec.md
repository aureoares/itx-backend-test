# Similar Products Specification

## Purpose
Provides a consolidated REST API for front-end clients to retrieve ordered details of products similar to a given product ID without issuing multiple client-side requests.

## Requirements

### Requirement: Similar Products Retrieval
The system SHALL expose an HTTP GET endpoint at `/product/{productId}/similar` on port 5000 that returns a JSON array of `ProductDetail` objects corresponding to the similar products of the requested root product ID.

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
