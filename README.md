# Sport Event Bets Settlement Service

This project is a backend application that simulates an event-driven pipeline for processing sports betting outcomes, built with Java 21 and the Spring Boot framework.

It demonstrates a decoupled, asynchronous architecture using industry-standard messaging systems. The flow begins with a REST API endpoint for ingesting event outcomes, which are then published to an **Apache Kafka** topic. A Kafka consumer processes these events, matches them against bets stored in an H2 in-memory database, and triggers the settlement process by publishing messages to an **Apache RocketMQ** topic. A final RocketMQ consumer listens for these settlement messages to complete the workflow.

## Features

- **REST API** for ingesting sport event outcomes.
- **Apache Kafka** for decoupled, real-time event streaming.
- **Apache RocketMQ** for reliable task queuing for bet settlements.
- **In-Memory Database (H2)** for storing and querying bets.
- **Event-Driven Architecture** to ensure loose coupling and scalability.
- **Comprehensive Testing** with unit tests and a full end-to-end integration test.

## Architectural Notes

This project was designed to demonstrate not just a functional implementation, but also an understanding of key principles in distributed systems engineering.

### 1. Guaranteed Ordering (FIFO)

To ensure data consistency, it is critical that bet settlements for a single user are processed in the exact order they are received. This is achieved by using RocketMQ's **Orderly Message** feature. When a settlement task is published, the `userId` is set as the **Message Group**. This guarantees that all messages for the same user will be sent to the same queue and consumed by the same thread, preserving a strict First-In-First-Out processing order per user.

### 2. Producer Reliability

To handle transient network issues or temporary broker unavailability, the RocketMQ producer is configured with a **retry policy**. It will attempt to send a message up to 3 times before failing. This significantly increases the guarantee of "at-least-once" delivery, which is critical for a financial transaction like settling a bet.

### 3. Idempotent Consumer

The final consumer that updates the bet status is designed to be idempotent. If the same settlement message were to be delivered more than once (a possibility in "at-least-once" systems), the logic would simply re-set the bet's status to `SETTLED`. This has no negative side effects and ensures the system remains consistent even in the face of message redelivery.

## How to Run

### Prerequisites

- Java 21
- Docker and Docker Compose

### 1. Start Infrastructure Services

The project requires Apache Kafka and Apache RocketMQ to be running. A `docker-compose.yml` file is provided to start both services with a single command.

From the root of the project, run:
```bash
docker-compose up -d
```
This will start Kafka (on port `9092`) and RocketMQ (on port `9876`) in the background.

### 2. Run the Application

Once the infrastructure is running, you can start the Spring Boot application using the Gradle wrapper:

```bash
./gradlew bootRun
```
The application will start on `http://localhost:8080`.

### 3. Use the Application

You can now interact with the service.

#### Publish an Event Outcome

Send a `POST` request to the `/events/outcome` endpoint. This simulates a sports event finishing and a winner being declared.

**Example Request:**
```bash
curl -X POST http://localhost:8080/events/outcome \
-H "Content-Type: application/json" \
-d '{
      "eventId": "event1",
      "eventName": "Football Match",
      "eventWinnerId": "winner1"
    }'
```

When this request is sent, the application will:
1. Publish the outcome to the `event-outcomes` Kafka topic.
2. The Kafka consumer will find the matching bet in the database (which was pre-populated by `DataInitializer.java`).
3. The consumer will publish a settlement task to the `bet-settlements` RocketMQ topic.
4. The RocketMQ consumer will receive the task and update the bet's status to `SETTLED`.

#### Verify the Result

You can verify the result by checking the application logs or by accessing the H2 in-memory database console.

- **URL**: `http://localhost:8080/h2-console`
- **JDBC URL**: `jdbc:h2:mem:testdb`
- **Username**: `sa`
- **Password**: `password`

Run the following SQL query to see the updated bet:
```sql
SELECT * FROM BET WHERE EVENT_ID = 'event1';
```
You will see that the status of the bet with `EVENT_WINNER_ID = 'winner1'` has changed from `PENDING` to `SETTLED`.
