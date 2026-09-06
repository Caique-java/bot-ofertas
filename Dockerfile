FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --gid 10001 bot && useradd --uid 10001 --gid bot --no-create-home bot \
    && mkdir -p /app/logs && chown -R bot:bot /app
COPY --from=build --chown=bot:bot /build/target/bot-ofertas-1.0.0.jar app.jar
USER bot
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java","-jar","app.jar"]
