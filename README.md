# Sport Event Bets Settlement Service

This project is a backend application that simulates an event-driven pipeline for processing sports betting outcomes, built with Java 21 and the Spring Boot framework.

It demonstrates a decoupled, asynchronous architecture using industry-standard messaging systems. The flow begins with a REST API endpoint for ingesting event outcomes, which are then published to an **Apache Kafka** topic. A Kafka consumer processes these events, matches them against bets stored in an H2 in-memory database, and triggers the settlement process by publishing messages to an **Apache RocketMQ** topic. A final RocketMQ consumer listens for these settlement messages to complete the workflow.

## Features

- **REST API** for ingesting sport event outcomes.
- **Apache Kafka** for decoupled, real-time event streaming.
- **Apache RocketMQ** for reliable task queuing for bet settlements.
- **In-Memory Database (H2)** for storing and querying bets.
- **Object-Oriented Bet Processing**: Bet outcome logic is encapsulated within the `Bet` entity.
- **Production-Ready Health Checks** via Spring Boot Actuator.
- **Event-Driven Architecture** to ensure loose coupling and scalability.
- **Robust Exception Handling**: Specific handling for concurrency and integration issues.
- **Comprehensive Testing** with unit tests, including exception handling scenarios, and a full end-to-end integration test.
- **Containerized Deployment** with Docker for portability and ease of use.

## Architectural Notes

This project was designed to demonstrate not just a functional implementation, but also an understanding of key principles in distributed systems engineering.

### 1. Guaranteed Ordering (FIFO)

To ensure data consistency, it is critical that bet settlements for a single user are processed in the exact order they are received. This is achieved by using RocketMQ's **Orderly Message** feature. When a settlement task is published, the `userId` is set as the **Message Group**. This guarantees that all messages for the same user will be sent to the same queue and consumed by the same thread, preserving a strict First-In-First-Out processing order per user.

### 2. Producer Reliability

To handle transient network issues or temporary broker unavailability, the RocketMQ producer is configured with a **retry policy**. It will attempt to send a message up to 3 times before failing. This significantly increases the guarantee of "at-least-once" delivery, which is critical for a financial transaction like settling a bet.

### 3. Idempotent Consumer & Robust Bet Processing

The `EventOutcomeConsumer` and the `Bet` entity are designed for idempotency and resilience in a highly concurrent environment:

- **Encapsulated Bet Logic**: The `Bet.processEventOutcome(EventOutcome eventOutcome)` method directly handles the logic of determining if a bet is `WON` or `LOST` based on the event's result. This method updates the bet's internal status and `settledDate`.
- **Idempotency in `Bet` Entity**: The `processEventOutcome` method includes an internal check (`if (this.status != BetStatus.PENDING)`) to ensure that if the method is called multiple times for an already processed bet, it will not re-process the outcome but will return the winning status based on its current state.
- **Optimistic Locking**: The `@Version` field in the `Bet` entity, combined with `@Transactional` in the consumer, ensures that concurrent updates to the same bet are handled gracefully. If an `OptimisticLockingFailureException` occurs (indicating a concurrent modification), it is caught and logged at a `WARN` level, as this is an expected scenario in highly concurrent systems and message re-delivery.
- **Specific Exception Handling**: The `EventOutcomeConsumer` employs expectation-driven exception handling, specifically catching and logging `IllegalArgumentException` (from `Bet.processEventOutcome`), `JsonProcessingException` (during bet serialization for RocketMQ), and `ClientException` (during RocketMQ message sending) at an `ERROR` level. This provides clear insights into specific failure modes without relying on a generic catch-all.

## How to Run (Docker - Recommended)

This is the simplest way to run the entire application stack, including the required infrastructure.

### Prerequisites

- Java 21 (for the build)
- Docker and Docker Compose

### 1. Build the Application JAR

First, use the Gradle wrapper to build the executable JAR file:
```bash
./gradlew bootJar
```

### 2. Start Infrastructure and Configure RocketMQ Topic

Start all services *except* the application, then manually configure the RocketMQ topic for FIFO messaging.

```bash
docker compose up --build --scale app=0 -d
```

Wait a few moments for RocketMQ to fully start (you can check `docker compose logs rocketmq-broker`). Then, execute the topic creation command:

```bash
docker compose exec -it rocketmq-broker sh mqadmin updateTopic -n rocketmq-namesrv:9876 -c DefaultCluster -t bet-settlements -a "+message.type=FIFO"
```

### 3. Start the Application

Now, scale up and start your application service:

```bash
docker compose up --scale app=1 -d
```
The application will be available at `http://localhost:8080`.

## Use the Application

Once the application is running, you can interact with it.

### Check Application Health

The application exposes a production-ready health check endpoint via Spring Boot Actuator. You can check the status of the application and its dependencies (Database, Kafka) by accessing:

- **URL**: `http://localhost:8080/actuator/health`

### Publish an Event Outcome

Send a `POST` request to the `/events/outcome` endpoint.

**Standard `curl` (Linux/macOS/Git Bash):**
```bash
curl -X POST http://localhost:8080/events/outcome \
-H "Content-Type: application/json" \
-d '{
      "eventId": "22e653d5-b12d-4e48-995c-73fdaabdc99c",
      "eventName": "Football Match",
      "eventWinnerId": "dfba1b4f-4a2e-4940-b630-18a81c7d3bf6"
    }'
```

**Windows PowerShell:**
In PowerShell, `curl` is an alias for `Invoke-WebRequest`, which uses a different syntax. Use this command instead:
```powershell
Invoke-WebRequest -Uri http://localhost:8080/events/outcome -Method POST -ContentType "application/json" -Body '{"eventId": "22e653d5-b12d-4e48-995c-73fdaabdc99c", "eventName": "Football Match", "eventWinnerId": "dfba1b4f-4a2e-4940-b630-18a81c7d3bf6"}'
```

### Verify the Result

You can verify the result by checking the application logs or by accessing the H2 in-memory database console.

- **URL**: `http://localhost:8080/h2-console`
- **JDBC URL**: `jdbc:h2:mem:testdb`
- **Username**: `sa`
- **Password**: `password`

Run the following SQL query to see the updated bet:
```sql
SELECT * FROM BET WHERE EVENT_ID = '22e653d5-b12d-4e48-995c-73fdaabdc99c';
```
You will see that the status of the bet with `EVENT_WINNER_ID = 'dfba1b4f-4a2e-4940-b630-18a81c7d3bf6'` has changed from `PENDING` to `WON` or `LOST` (depending on the `eventWinnerId` in your POST request). The final `SETTLED` status would be applied by the RocketMQ consumer.
