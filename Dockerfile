# ============================
# 1. Build stage
# ============================
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml and download dependencies first (for better caching)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# ============================
# 2. Run stage
# ============================
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copy only the built JAR from build stage
COPY --from=build /app/target/pantrypilot-0.0.1-SNAPSHOT.jar .

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "pantrypilot-0.0.1-SNAPSHOT.jar"]

