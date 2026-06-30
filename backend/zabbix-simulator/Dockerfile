# Build stage — Debian + Maven retry (avoids Alpine Central TLS flakes)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY maven-settings.xml /root/.m2/settings.xml
RUN apt-get update && apt-get install -y --no-install-recommends maven && \
    mvn -B -s /root/.m2/settings.xml -Dmaven.test.skip=true dependency:resolve dependency:resolve-plugins || \
    mvn -B -s /root/.m2/settings.xml -Dmaven.test.skip=true dependency:resolve dependency:resolve-plugins
COPY src ./src
RUN mvn -B -s /root/.m2/settings.xml -Dmaven.test.skip=true package || \
    mvn -B -s /root/.m2/settings.xml -Dmaven.test.skip=true package && \
    cp target/*.jar app.jar && \
    rm -rf /root/.m2 && \
    apt-get purge -y maven && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

# Runtime stage
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/app.jar app.jar

ENV PORT=8082
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
