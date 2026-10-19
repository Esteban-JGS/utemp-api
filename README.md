# UTEMP API — University Task and Evaluation Management Platform

[![CI](https://github.com/Esteban-JGS/utemp-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Esteban-JGS/utemp-api/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-336791)

REST API for managing university coursework: universities, faculties, departments, courses, enrollments,
assignments, submissions and grades. Students upload their work in chunks, a simulated AI
reviewer stores a preliminary grade in the background, teachers confirm or change it, and students and teachers
get in-app notifications on key events.

Built with Java 21 and Spring Boot 3.3: JWT authentication with refresh tokens, permission-based access control,
PostgreSQL with Flyway migrations, Docker Compose, and tests with JUnit 5, Mockito and Testcontainers.

## Run it in 5 minutes

Requirements: Docker. The login example below also uses `curl` and [`jq`](https://jqlang.github.io/jq/).

```bash
git clone https://github.com/Esteban-JGS/utemp-api.git
cd utemp-api/UTEMP_Project
cp .env.example .env          # then set JWT_SECRET, e.g. with: openssl rand -base64 48
docker compose up --build
```

- API: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui/index.html>
- Health check: <http://localhost:8080/actuator/health>

Docker Compose loads demo data. Every demo user has the password `Demo1234!`:

| Role    | Identification number | Can do                                                        |
|---------|-----------------------|---------------------------------------------------------------|
| Admin   | `100000001`           | Manage universities, faculties, departments, courses, users   |
| Teacher | `200000001`           | Manage assignments, review and grade submissions in their course |
| Student | `300000001`           | Enrolled in "Programming III", can submit files               |
| Student | `300000002`           | Same course; cannot see the other student's files             |

```bash
# Log in and call a protected endpoint
TOKEN=$(curl -s -X POST localhost:8080/utemp/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"identificationNumber":"200000001","password":"Demo1234!"}' | jq -r .data.token)

curl -H "Authorization: Bearer $TOKEN" localhost:8080/utemp/users/me
```

### Run locally without Docker for the API

Requires JDK 21+ and Maven. From the `UTEMP_Project` folder:

```bash
docker compose up -d postgres          # only the database
export $(grep -v '^#' .env | xargs)    # load the variables from .env
mvn spring-boot:run
```

### Tests

Requires JDK 21+ and Maven. From the `UTEMP_Project` folder:

```bash
mvn test
```

Unit tests use JUnit 5 and Mockito. Integration tests start a real PostgreSQL with Testcontainers (Docker must be
running), apply the same Flyway migrations as production and go through the real security filter chain.
GitHub Actions runs the whole suite and builds the Docker image on every push.

## Architecture

```mermaid
flowchart LR
    client["Client / Swagger UI"] -->|"HTTP + Bearer JWT"| filter["JwtRequestFilter"]
    filter --> controllers["Controllers<br/>@PreAuthorize rules"]
    controllers --> services["Services<br/>business rules, ownership checks"]
    services --> repositories["Spring Data JPA repositories"]
    repositories --> db[("PostgreSQL<br/>schema by Flyway")]
    services --> storage[("File storage<br/>UUID file names")]
    services -.->|"event after commit, @Async"| review["Simulated AI review"]
    services -.->|"Observer pattern"| mail["Email notifications"]
    controllers -.->|"exceptions"| advice["GlobalExceptionHandler<br/>@RestControllerAdvice"]
```

```text
UTEMP_Project/src/main/java/org/una/programmingIII/UTEMP_Project
├── configs/        Security, Swagger, async and observer configuration
├── controllers/    REST endpoints (/utemp/...) and request/response wrappers
├── services/       Business logic, one package per domain area
├── repositories/   Spring Data JPA repositories
├── models/         JPA entities
├── dtos/           Objects exposed by the API
├── security/       JWT filter and token provider
├── exceptions/     Domain exceptions and the global exception handler
└── transformers/   ModelMapper-based entity/DTO mappers
```

## Main endpoints

All endpoints are under `/utemp` and, except login and refresh, require `Authorization: Bearer <token>`.
The full, interactive list is in Swagger UI.

| Area          | Endpoints                                                                                  |
|---------------|--------------------------------------------------------------------------------------------|
| Auth          | `POST /auth/login`, `POST /auth/refresh`                                                   |
| Users         | `GET /users/me`, CRUD on `/users`, `/users/{id}/courses`, `/users/{id}/enrollments`         |
| Organization  | CRUD on `/universities`, `/faculties`, `/departments`, `/courses`                           |
| Coursework    | CRUD on `/enrollments`, `/assignments`, `/submissions`, `/grades`                           |
| Files         | `POST /files`, `POST /files/receive-chunk`, `GET /files/download/{id}`, `DELETE /files/{id}` |
| Notifications | `/notifications`, `/notifications/user/{userId}`                                           |

Errors always have the same shape, whether they come from the `@RestControllerAdvice` or from the security
filters (`401` and `403`):

```json
{ "data": null, "statusCode": 403, "message": "Access denied" }
```

## Security decisions

- **Stateless JWT authentication.** Short-lived access tokens (15 min) and refresh tokens (7 days), signed with
  HMAC using jjwt 0.12, which picks HS256, HS384 or HS512 from the key length (a key generated with
  `openssl rand -base64 48` gives HS512). Each token carries a `type` claim, so a refresh token cannot be used to
  call the API and an access token cannot be used to refresh. The session policy is `STATELESS`.
- **No secrets in the repository.** Database, mail and JWT settings come from environment variables
  (`.env.example` documents them). The JWT key must be at least 256 bits; the application refuses to start with a
  weaker key.
- **Passwords hashed with BCrypt** and never returned by the API (the password field is write-only).
- **Authorization at two levels.** `@PreAuthorize` rules check permissions per endpoint, and record-level rules
  (`AccessRules`) check who the record belongs to:
  - only the teacher of a course, or an admin, manages its assignments and reviews and grades its submissions;
  - students see the assignments of the courses they are enrolled in, and only their own grades and notifications;
  - a file can only be read by its owner, the teacher of the course or an admin, and only modified by its owner or
    an admin.

  Anyone else gets `403`, and integration tests cover each rule. A suspended account loses access at once, even
  with a token issued before the suspension.
- **Safe file storage.** Uploaded files are stored under a server-generated UUID; the original name is kept only as
  metadata and never used to build a path, which prevents path traversal. Uploads are limited in size
  (`FILE_MAX_SIZE`, 20 MB by default) and their chunks must arrive in order. Downloads use a properly encoded
  `Content-Disposition` header.
- **Schema under version control.** Flyway owns the schema and Hibernate only validates it (`ddl-auto=validate`).
  Demo data lives in a separate migration location that is only enabled by Docker Compose.

## How the simulated AI review works

When the last chunk of a submission file arrives, `FileService` publishes a `SubmissionFileUploadedEvent`.
`AutoReviewService` handles it with `@TransactionalEventListener(AFTER_COMMIT)` on the async executor, so the upload
response is not delayed and the review always sees the stored file. It saves a preliminary grade and comment
(`PENDING_REVIEW`, `reviewedByAi = true`) and notifies the student; the teacher confirms or changes it through the
manual review. The grade is random but stable per file, which is enough to demo the asynchronous flow.

## Notifications

Notifications are stored in the database and read through `/notifications/user/{userId}`:

- a student is notified when an assignment is added to one of their courses (`POST /courses/{id}/assignments`) and
  when their submission is graded, by the teacher or by the simulated review;
- the teacher is notified when a student hands in work.

Email is only sent about enrollments (`POST /users/{userId}/enrollments/{courseId}` and `PUT /enrollments/{id}`),
to the student and the teacher, using the Observer pattern on the async executor. It needs the SMTP settings in
`.env`; without them each failed email is logged as a single warning.

## Tech stack

Java 21 · Spring Boot 3.3 (Web, Security, Data JPA, Validation, Mail, Actuator) · PostgreSQL 16 · Flyway ·
jjwt · ModelMapper · springdoc-openapi · JUnit 5 · Mockito · Testcontainers · Docker · GitHub Actions

## Team

Built as the final project of the Programming III course at Universidad Nacional (Costa Rica) by:

- **Esteban Javier Granados Sibaja** ([@Esteban-JGS](https://github.com/Esteban-JGS)) — main author of the
  business logic (most of the services, including the asynchronous review and the observer-based notifications) and
  of the data model: entities, DTOs, repositories and mappers. Also part of the controllers.
- **Juan Carlos Camacho Solano** ([@JuanCaUNA](https://github.com/JuanCaUNA)) — most of the REST controllers and
  their OpenAPI documentation, JWT security configuration, Docker Compose setup and part of the services.
- **Noemí Murillo** — the original unit test suite (entities, request/response objects and services) and part of
  the controllers.

After the course, Esteban did the hardening described in this README: security fixes (credentials, JWT, file and
per-user access control), Flyway migrations, Docker image, CI, centralized error handling and the repair of the test
suite.
