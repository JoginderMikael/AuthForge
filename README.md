# AuthForge: Multi-Tenant Identity & Access Management Platform

AuthForge is a scalable, developer-friendly Authentication and Authorization service built with Spring Boot. Unlike a simple login system, AuthForge is designed as a standalone identity platform—similar to a lightweight Auth0 or Okta—allowing multiple applications to centralize user management, token issuance, and role-based access control (RBAC).

🚀 **Key Features**
- **Multi-Application Support**: Register multiple client applications, each with unique `client_id` and `client_secret` credentials.
- **JWT-Based Authentication**: Stateless authentication using secure JSON Web Tokens with support for Access and Refresh token rotations.
- **Granular RBAC**: Flexible Role and Permission system (e.g., `ROLE_ADMIN`, `READ_PRODUCTS`) for fine-grained authorization.
- **Token Validation API**: A dedicated endpoint for resource servers (microservices) to verify token integrity and retrieve user metadata.
- **Security First**: Built-in BCrypt password hashing, rate limiting to prevent brute-force attacks, and account lockout policies.
- **OAuth2 Compatibility**: Ready for integration with standard OAuth2 flows like Authorization Code and Client Credentials.

🏗️ **System Architecture**
AuthForge acts as the central hub for all your client applications (Web, Mobile, or APIs).

```text
App A (React)       App B (Mobile)      App C (Python)
     |                   |                   |
     └───────────┬───────┴───────────────────┘
                 v
         [ AuthForge Service ]
      (Spring Boot / Spring Security)
    ┌──────────────┬──────────────┐
    | User Service | Token Mgmt   |
    ├──────────────┼──────────────┤
    | Client Reg   | RBAC Engine  |
    └──────────────┴──────────────┘
                 |
          [ PostgreSQL DB ]
```

🛠️ **Tech Stack**
- **Backend**: Java 17+, Spring Boot 3.x, Spring Security
- **Persistence**: Spring Data JPA, PostgreSQL
- **Security**: JWT (jjwt), BCrypt
- **Documentation**: SpringDoc OpenAPI (Swagger)
- **DevOps**: Docker, GitHub Actions (CI/CD)

📖 **Developer Integration Guide**

### 1. Register Your Application
Developers must first register their service to receive credentials.
**Endpoint**: `POST /api/clients/register`
**JSON Request**:
```json
{
  "name": "My E-commerce App",
  "redirectUri": "https://myapp.com/callback"
}
```
**Response**:
```json
{
  "clientId": "authforge_abc123",
  "clientSecret": "sec_789xyz..."
}
```

### 2. Authenticate Users
Use the global authentication endpoint to log users in.
**Endpoint**: `POST /auth/login`
**JSON Request**:
```json
{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

### 3. Validate Tokens
Resource servers can verify the `Authorization: Bearer <JWT>` header via the validation endpoint.
**Endpoint**: `POST /auth/validate`

🗄️ **Database Schema**
The system uses a relational structure to manage the complex mapping between users, roles, and client applications.

| Table | Description |
| :--- | :--- |
| **users** | Core user credentials and status. |
| **clients** | Registered applications (OAuth2 clients). |
| **roles** | Defines system roles (User, Admin, etc). |
| **user_roles** | Mapping table for User-to-Role assignment. |
| **refresh_tokens** | Tracks active sessions and allows secure rotation. |

📂 **Project Structure**
```text
src/main/java/com/authforge/
├── config/         # Configuration classes (Security, Redis, Swagger)
├── controller/     # API Endpoints (Auth, Clients, User)
├── service/        # Business Logic (Token generation, Validation)
├── security/       # JWT Filters, SecurityConfig, UserDetails
├── entity/         # JPA Models (User, Client, Role)
├── repository/     # Data Access Objects
├── dto/            # Request/Response Data Transfer Objects
├── mapper/         # Object Mappings
├── util/           # Utility classes
└── exception/      # Custom Exception handling
```

🚦 **Getting Started**
1. **Clone the repo**: `git clone https://github.com/yourusername/authforge.git`
2. **Configure Database**: Update `application.yml` with your PostgreSQL credentials.
3. **Run with Maven**: `./mvnw spring-boot:run`
4. **Explore Documentation**: Access `http://localhost:8080/swagger-ui.html` to see the full API spec.