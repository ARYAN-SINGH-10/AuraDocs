# Stage 1: Build the Tailwind CSS using Node.js
FROM node:20-alpine AS css-builder
WORKDIR /app
# Copy package files and install dependencies
COPY package*.json ./
RUN npm ci
# Copy all source files and build the CSS
COPY . .
RUN npm run build:css

# Stage 2: Build the Java application using Maven
FROM maven:3.9.6-eclipse-temurin-17 AS java-builder
WORKDIR /app
# Copy the pom.xml and source code
COPY pom.xml .
COPY src ./src
# Copy the built CSS from the previous stage
COPY --from=css-builder /app/src/main/resources/static/css/output.css ./src/main/resources/static/css/output.css
# Build the application (skipping tests for faster deployment)
RUN mvn clean package -DskipTests

# Stage 3: Run the application in a lightweight container
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Copy the built jar from the java-builder stage
COPY --from=java-builder /app/target/auradocs-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
# Render assigns a dynamic PORT via environment variable, Spring Boot respects server.port
ENV PORT=8080
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
