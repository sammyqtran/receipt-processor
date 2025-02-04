# Step 1: Build the project with Maven in a separate build stage (multi-stage build)
FROM maven:3.8.1-openjdk-11 AS build

# Set the working directory in the container
WORKDIR /app

# Copy the pom.xml and the src directory into the container
COPY pom.xml /app
COPY src /app/src

# Run Maven to build the project and generate the JAR file
RUN mvn clean install

# Step 2: Run the application from the JAR file
FROM openjdk:11-jdk-slim

# Set the working directory in the container
WORKDIR /app

# Copy the JAR file from the build stage into the container
COPY --from=build /app/target/receipt-processor-1.0-SNAPSHOT.jar /app/receipt-processor.jar

# Specify the command to run the JAR file
ENTRYPOINT ["java", "-jar", "receipt-processor.jar"]

EXPOSE 8080