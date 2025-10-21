FROM eclipse-temurin:17-jre

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY target/lucli.jar /app/lucli.jar

# Create .lucli directory for configuration
RUN mkdir -p /root/.lucli

# Set entrypoint to run the JAR
ENTRYPOINT ["java", "-jar", "/app/lucli.jar"]

# Default command (can be overridden)
CMD ["--help"]
