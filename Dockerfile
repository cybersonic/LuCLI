# FROM eclipse-temurin:17-jre
FROM eclipse-temurin:21-jre

# Create lucee user and group with specific UID/GID for consistency
# Use GID 1001 to avoid conflict with existing GID 1000
RUN groupadd --gid 1001 lucee && \
    useradd --uid 1001 --gid lucee --create-home --home-dir /home/lucee lucee

# Set working directory
WORKDIR /app

# Copy the JAR file as root, then fix permissions
COPY target/lucli.jar /usr/local/bin/lucli.jar

# Create a wrapper script for the lucli command and set permissions
RUN echo '#!/bin/sh\nexec java -jar /usr/local/bin/lucli.jar "$@"' > /usr/local/bin/lucli && \
    chmod +x /usr/local/bin/lucli && \
    chmod 755 /usr/local/bin/lucli.jar

# Create .lucli directory for configuration with proper ownership
RUN mkdir -p /home/lucee/.lucli && \
    chown -R lucee:lucee /home/lucee/.lucli

# Switch to lucee user for runtime
USER lucee

# Set environment variable for LuCLI home
ENV LUCLI_HOME=/home/lucee/.lucli

# Warm up LuCLI (optional)
# RUN lucli --version

# Set entrypoint to run the JAR
ENTRYPOINT ["/usr/local/bin/lucli"]

# Default to server mode (can be overridden with docker run args)
CMD ["server", "run"]
