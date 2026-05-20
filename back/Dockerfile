# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY back/gradlew ./back/gradlew
COPY back/gradle ./back/gradle
COPY back/settings.gradle back/build.gradle ./back/
COPY back/src ./back/src

RUN chmod +x ./back/gradlew \
    && ./back/gradlew -p back --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system aquila \
    && useradd --system --gid aquila --home-dir /app --shell /usr/sbin/nologin aquila

COPY --from=build /workspace/back/build/libs/*.jar /app/aquila-buddy-back.jar

ENV SPRING_PROFILES_ACTIVE=dev
EXPOSE 8080

USER aquila
ENTRYPOINT ["java", "-jar", "/app/aquila-buddy-back.jar"]
