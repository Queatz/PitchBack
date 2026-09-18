# Saigon.Social

A social talk-and-meet hub for Saigon. Browse newest pitches, pitch back, accept a meet, then send one final message. No chat thread.

## Stack

- Kotlin / Ktor server
- Compose HTML web app
- ArangoDB
- Qwen3.8-Flash (Beijing DashScope) for streamed AI copy

## Run locally

1. Copy `.env.example` to `.env` and set `ARANGO_PASSWORD` (and `DASHSCOPE_API_KEY` for AI).
2. Start ArangoDB on `http://127.0.0.1:8529`.
3. Server:

```shell
./gradlew :server:run
```

4. Web (Compose HTML, http://localhost:8081):

```shell
./gradlew :app:webApp:jsBrowserDevelopmentRun
```

The first account you create is the admin. Open **Me → Admin desk** to add locations and approve pitches.

Production (`APP_ENV=prod`) serves the compiled web app from the Ktor server after:

```shell
./gradlew :app:webApp:jsBrowserDistribution :server:run
```

## Tests

Critical domain rules (visibility, expiry, first-admin, phone, length):

```shell
./gradlew :core:jvmTest :server:test
```
