# Use the official Eclipse Temurin image, a standard for Java applications
FROM eclipse-temurin:21-jdk

# Install curl, which is required by the docker-compose healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Set the working directory in the container
WORKDIR /app

# Copy the executable JAR file from the build context into the container
COPY build/libs/*.jar app.jar

# Make port 8080 available to the world outside this container
EXPOSE 8080

# Run the JAR file
ENTRYPOINT ["java", "-jar", "app.jar"]
