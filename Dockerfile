FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS agent
ARG NEW_RELIC_AGENT_VERSION=9.4.0
WORKDIR /agent
RUN apk add --no-cache curl unzip \
    && curl -sSL -o newrelic-java.zip \
       "https://download.newrelic.com/newrelic/java-agent/newrelic-agent/${NEW_RELIC_AGENT_VERSION}/newrelic-java-${NEW_RELIC_AGENT_VERSION}.zip" \
    && unzip -q newrelic-java.zip \
    && test -f newrelic/newrelic.jar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache openssl
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=agent /agent/newrelic/newrelic.jar /app/newrelic/newrelic.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN sed -i 's/\r$//' /app/docker-entrypoint.sh && chmod +x /app/docker-entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
