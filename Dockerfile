FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml .
RUN set -eux; \
    for attempt in 1 2 3; do \
      if mvn -B -ntp -q -DskipTests \
          -Daether.connector.basic.retry=5 \
          -Dmaven.wagon.http.retryHandler.count=5 \
          dependency:go-offline; then \
        exit 0; \
      fi; \
      echo "Maven dependency prefetch failed (attempt ${attempt}/3), retrying..."; \
      sleep $((attempt * 5)); \
    done; \
    exit 1
COPY src src
RUN set -eux; \
    for attempt in 1 2 3; do \
      if mvn -B -ntp -q -DskipTests \
          -Daether.connector.basic.retry=5 \
          -Dmaven.wagon.http.retryHandler.count=5 \
          package; then \
        exit 0; \
      fi; \
      echo "Maven package failed (attempt ${attempt}/3), refreshing transient artifacts..."; \
      rm -rf /root/.m2/repository/io/netty/netty-common; \
      sleep $((attempt * 5)); \
    done; \
    exit 1

FROM eclipse-temurin:17-jre-noble

WORKDIR /app
COPY --from=build /workspace/target/xiaocan.jar xiaocan.jar

EXPOSE 10234
ENTRYPOINT ["java", "-Xmx256m", "-jar", "xiaocan.jar"]
