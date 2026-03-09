# Project Structure

```text
auth-service
│
├── config
│   ├── SecurityConfig
│   ├── RedisConfig
│   ├── SwaggerConfig
│   └── OAuth2ProvidersConfig
│
├── controller
│   ├── AuthController
│   ├── ClientController
│   ├── UserController
│   └── OAuth2Controller
│
├── service
│   ├── AuthService
│   ├── UserService
│   ├── ClientService
│   ├── TokenService
│   ├── OAuth2Service
│   └── SocialAuthService
│
├── security
│   ├── jwt
│   │   ├── JwtProvider
│   │   ├── JwtAuthenticationFilter
│   │   └── JwtTokenValidator
│   │
│   ├── oauth
│   │   ├── OAuth2UserService
│   │   ├── OAuth2LoginSuccessHandler
│   │   └── OAuth2LoginFailureHandler
│   │
│   └── SecurityConstants
│
├── entity
│   ├── User
│   ├── Role
│   ├── Client
│   ├── RefreshToken
│   ├── SocialAccount
│   └── Permission
│
├── repository
│   ├── UserRepository
│   ├── RoleRepository
│   ├── ClientRepository
│   ├── RefreshTokenRepository
│   └── SocialAccountRepository
│
├── dto
│   ├── request
│   │   ├── LoginRequest
│   │   ├── RegisterRequest
│   │   └── RefreshTokenRequest
│   │
│   └── response
│       ├── AuthResponse
│       ├── TokenResponse
│       └── UserResponse
│
├── mapper
│   └── UserMapper
│
├── util
│   ├── TokenUtils
│   └── SecurityUtils
│
└── exception
    ├── GlobalExceptionHandler
    └── AuthException
```