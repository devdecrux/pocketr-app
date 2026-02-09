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
COPY --from=frontend-builder /vue-app/dist /backend/src/main/resources/static/frontend
RUN chmod +x ./gradlew
RUN ./gradlew bootJar -x test

FROM eclipse-temurin:25-jre-alpine
COPY --from=backend-builder /backend/build/libs/*.jar app.jar
EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
