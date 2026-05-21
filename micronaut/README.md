# Micronaut vs Spring Boot — CoP Demo

A side-by-side comparison of the same CRUD application built with Spring Boot 3.5 and
Micronaut 4.6.3, demonstrating startup time, memory, and build time differences across
three deployment profiles: Spring JVM, Micronaut JVM, and Micronaut native binary.

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 25 | via Homebrew or SDKMAN |
| GraalVM CE | 25.0.2-graalce | Only needed for native binary; install via SDKMAN |
| Docker | any recent | Only needed for `docker-compare.sh` |

**Install GraalVM via SDKMAN (native binary only):**
```bash
sdk install java 25.0.2-graalce
```

## Project structure

```
├── spring-app/       Spring Boot 3.5 app (port 8080)
├── micronaut-app/    Micronaut 4.6.3 app (port 8081 native, 8082 JVM)
├── compare.sh        Runs both apps, prints startup/memory/build-time table + request benchmarks
└── docker-compare.sh Builds Docker images, prints image size comparison
```

Both apps expose the same endpoints:
- `GET  /books`
- `GET  /books/{id}`
- `GET  /books/search?author=<name>`
- `POST /books`
- `PUT  /books/{id}`
- `DELETE /books/{id}`

## Running the comparison

```bash
./compare.sh
```

This builds missing JARs automatically, starts all three processes, prints a comparison
table, then runs 100 sequential requests per endpoint and reports avg/min/max latency.
Press Enter when done to stop all processes.

## Building manually

```bash
# JVM JARs
./gradlew :spring-app:bootJar :micronaut-app:shadowJar -x test

# Native binary (~12 min — do this before the demo)
GRAALVM_HOME=$HOME/.sdkman/candidates/java/25.0.2-graalce \
  ./gradlew :micronaut-app:nativeCompile -x test

# Docker images
./docker-compare.sh
```

## Running tests

```bash
./gradlew test
```

## Ports

| App | Port |
|-----|------|
| Spring Boot (JVM) | 8080 |
| Micronaut (native) | 8081 |
| Micronaut (JVM) | 8082 |
