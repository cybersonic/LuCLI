# FROM eclipse-temurin:17-jre
FROM eclipse-temurin:21-jre

# Set working directory
WORKDIR /app

# Copy the JAR file and executable
COPY target/lucli.jar /app/lucli.jar
# COPY target/lucli /usr/local/bin/lucli

# Make the executable file executable (in case permissions are lost)
# RUN chmod +x /usr/local/bin/lucli

# Create .lucli directory for configuration
RUN mkdir -p /root/.lucli

# Warm up LuCLI (optional)
# RUN java -jar /app/lucli.jar --version

# Set entrypoint to run the JAR
ENTRYPOINT ["java", "-jar", "/app/lucli.jar"]

# Default command (can be overridden)
CMD ["--help"]
