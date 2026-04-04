# Stage 1: Build the application
FROM maven:3.9.14-eclipse-temurin-25-alpine AS build
WORKDIR /app

# Copy pom.xml and download dependencies to cache them
COPY pom.xml .
RUN mvn compile dependency:go-offline -B

# Copy the source code and build the application
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Create the runtime image
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/target/fxdeals-*.jar app.jar

# Expose the application port
EXPOSE 8080

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]
