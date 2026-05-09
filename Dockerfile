FROM node:22.15-alpine3.21 AS frontend-builder
WORKDIR /vue-app
COPY pocketr-ui/package-lock.json .
COPY pocketr-ui/package.json .
RUN npm ci
COPY pocketr-ui/ .
RUN npm run build-only

FROM eclipse-temurin:25-jdk-alpine AS backend-builder
WORKDIR /backend
COPY pocketr-api/ .
COPY --from=frontend-builder /vue-app/dist/. /backend/src/main/resources/static/
RUN chmod +x ./gradlew
RUN ./gradlew bootJar -x test

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S pocketr && \
    adduser -S -G pocketr pocketr && \
    mkdir -p /opt/pocketr/avatars && \
    chown -R pocketr:pocketr /opt/pocketr
WORKDIR /opt/pocketr
COPY --from=backend-builder --chown=pocketr:pocketr /backend/build/libs/*.jar app.jar
USER pocketr
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
