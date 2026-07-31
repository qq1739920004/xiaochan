FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-noble

WORKDIR /app
COPY --from=build /workspace/target/xiaocan.jar xiaocan.jar

EXPOSE 10234
ENTRYPOINT ["java", "-Xmx256m", "-jar", "xiaocan.jar"]
