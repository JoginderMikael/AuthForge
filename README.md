# AuthForge

AuthForge is a Spring Boot identity and access-management service for multiple client applications. It provides tenant-scoped user authentication, JWT access tokens, rotated refresh tokens, social login, RBAC, and a standards-based OAuth2 `client_credentials` flow.

## What is implemented

- Email/password registration and login using BCrypt.
- Client applications as tenant boundaries. Users explicitly belong to clients, and user JWTs contain `client_id`, `aud`, and role claims.
- Spring Authorization Server at `POST /oauth2/token` with the `client_credentials` grant.
- Google and GitHub login with a short-lived, one-time Redis exchange code. Access and refresh tokens are never placed in redirect URLs.
- Opaque refresh tokens generated from secure random bytes, stored only as SHA-256 hashes, and rotated on every refresh.
- Seeded `ROLE_USER`, `ROLE_ADMIN`, and baseline permissions through Flyway.
- Redis-backed request rate limiting and temporary account lockout after repeated login failures.
- PostgreSQL persistence, Swagger/OpenAPI, Actuator health checks, JaCoCo, and GitHub Actions CI.

## Architecture

```text
Browser / service
       |
       v
AuthForge backend :8082
  |-- Spring Security + JWT
  |-- OAuth2 Authorization Server
  |-- Flyway migrations
  |-- PostgreSQL :5432   users, clients, roles, tokens, authorizations
  `-- Redis :6379        rate limits, lockouts, one-time OAuth codes
```

The Docker stack runs all three runtime services on a private Compose network. Only the API on port `8082` is exposed to the host; PostgreSQL and Redis stay internal to the stack.

## Run everything with Docker

Requirements: Docker Engine or Docker Desktop with Compose v2.

1. Create the local environment file:

   ```powershell
   Copy-Item .env.example .env
   ```

2. Replace the three placeholder secrets in `.env`. `AUTHFORGE_JWT_SECRET` must be at least 32 bytes. The `.env` file is ignored by both Git and the Docker build context.

3. Build and start the stack:

   ```bash
   docker compose up --build -d
   docker compose ps
   ```

4. Verify the API:

   ```bash
   curl http://localhost:8082/actuator/health
   ```

Swagger UI is available at <http://localhost:8082/swagger-ui.html>.

### Browser test console

A small React + TypeScript console lives in `frontend/`. It exercises client provisioning, the `client_credentials` grant, user registration and login, refresh-token rotation, JWT validation, and social OAuth code exchange. API responses and decoded JWT payloads are shown alongside each flow.

With the backend running on port `8082`, start the console in a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. The development server proxies API requests to the backend, so no CORS changes are needed. To use another backend URL, copy `frontend/.env.example` to `frontend/.env.local` and edit `VITE_AUTHFORGE_API_URL`.

To inspect logs or stop the stack:

```bash
docker compose logs -f backend
docker compose down
```

`docker compose down -v` also removes the PostgreSQL and Redis volumes and permanently deletes local development data.

## First API flow

Client creation is protected by the bootstrap token from `.env`. The returned client secret is shown only once.

```bash
curl -X POST http://localhost:8082/api/clients/register \
  -H "Content-Type: application/json" \
  -H "X-AuthForge-Bootstrap-Token: YOUR_BOOTSTRAP_TOKEN" \
  -d '{"name":"Inventory API","scopes":["api.read","inventory.read"]}'
```

Use the returned credentials for a machine-to-machine token:

```bash
curl -u 'CLIENT_ID:CLIENT_SECRET' \
  -X POST http://localhost:8082/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'grant_type=client_credentials&scope=api.read'
```

Register a user within that client boundary:

```bash
curl -X POST http://localhost:8082/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"securePassword123","firstName":"Ada","lastName":"Lovelace","clientId":"CLIENT_ID"}'
```

Then log in:

```bash
curl -X POST http://localhost:8082/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"securePassword123","clientId":"CLIENT_ID"}'
```

Refreshing returns both a new access token and a new refresh token. The submitted refresh token is immediately invalidated:

```bash
curl -X POST http://localhost:8082/auth/refresh-token \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"REFRESH_TOKEN"}'
```

## Social login

Set real Google or GitHub credentials in `.env`, including provider callback URLs such as `http://localhost:8082/login/oauth2/code/google`. Start login with a client context:

```text
GET /auth/oauth2/authorize/google?clientId=CLIENT_ID
```

On success, AuthForge redirects with a one-time `code`. Exchange it once with:

```http
POST /auth/oauth2/exchange
Content-Type: application/json

{"code":"ONE_TIME_CODE"}
```

The code expires after one minute and its Redis record is deleted atomically when exchanged.

## Local development and tests

The project targets Java 25 and Spring Boot 4.0.3.

```bash
./mvnw test
./mvnw spring-boot:run
```

Tests use an isolated H2 database in PostgreSQL compatibility mode. Runtime configuration comes from environment variables; see `.env.example` and `src/main/resources/application.properties` for the complete list.

Flyway owns the schema and Hibernate runs in validation mode. Do not switch `spring.jpa.hibernate.ddl-auto` back to `update`.

## Production notes

- Keep PostgreSQL and Redis on a private network, as the provided Compose stack does.
- Supply secrets through your deployment platform, not a committed `.env` file.
- Persist the authorization-server signing key in a managed keystore before running multiple backend replicas. The development configuration generates an RSA key at startup.
- Put TLS and a trusted reverse proxy in front of the backend and set `AUTHFORGE_JWT_ISSUER` to its public HTTPS URL.
- Rotate the bootstrap token after provisioning clients and restrict the client-registration endpoint at the network layer.
