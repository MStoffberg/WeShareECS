# Stage 1: Build the Maven project
FROM maven:3.9.4-eclipse-temurin-17 as build

# Set the working directory inside the container
WORKDIR /app

# Copy the Maven project files
COPY pom.xml .
COPY src ./src
COPY target ./target

# Build the project and create a JAR file
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:17-jre

# Set the working directory for the application
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=build /app/target/weshare-mvc-exercise-1.0-SNAPSHOT-jar-with-dependencies.jar /app/weshare.jar

# Copy necessary resources
COPY src/main/resources/templates /app/templates
# COPY src/main/resources/static /app/static
# COPY src/main/resources/database/weshare.db /app/weshare.db

# Set environment variables
ENV DATABASE_PATH=/app/weshare.db
ENV SERVER_PORT=7000

# Expose the port for Javelin server
EXPOSE 5050

# Command to run the JAR file
CMD ["java", "-jar", "/app/weshare.jar"]
