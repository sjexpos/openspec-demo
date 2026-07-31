
# Openspec Demo

[![GitHub release](https://img.shields.io/github/release/sjexpos/openspec-demo.svg?style=plastic)](https://github.com/sjexpos/openspec-demo/releases/latest)
[![CI workflow](https://img.shields.io/github/actions/workflow/status/sjexpos/openspec-demo/ci.yaml?branch=main&label=ci&logo=github&style=plastic)](https://github.com/sjexpos/openspec-demo/actions?workflow=CI)
[![Codecov](https://img.shields.io/codecov/c/github/sjexpos/openspec-demo?logo=codecov&style=plastic)](https://codecov.io/gh/sjexpos/openspec-demo)
[![Issues](https://img.shields.io/github/issues-search/sjexpos/openspec-demo?query=is%3Aopen&label=issues&style=plastic)](https://github.com/sjexpos/openspec-demo/issues)
[![Commits](https://img.shields.io/github/last-commit/sjexpos/openspec-demo?logo=github&style=plastic)](https://github.com/sjexpos/openspec-demo/commits)

[![Docker pulls](https://img.shields.io/docker/pulls/sjexpossdd/openspec-demo?logo=docker&style=plastic)](https://hub.docker.com/r/sjexpossdd/openspec-demo)
[![Docker size](https://img.shields.io/docker/image-size/sjexpossdd/openspec-demo?logo=docker&style=plastic)](https://hub.docker.com/r/sjexpossdd/openspec-demo/tags)


## 📋 Overview

This repository is a demo to learn OpenSpec framework and IA Agents. The application is a microservice which handles products in a cannabis e-commerce.

### 🏗️ Architecture

The system follows **Domain-Driven Design (DDD)** principles with a clean, layered architecture:

```
┌─────────────────────────────────────────────────────────┐
│                    Presentation Layer                   │
│  ┌───────────────────────────────────────────────────┐  │
│  │              SpringBoot Controllers               │  │
│  │                     (REST API)                    │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│                   Application Layer                     │
│  ┌───────────────────────────────────────────────────┐  │
│  │              Services & Use Cases                 │  │
│  │     (addressService, dispensaryService, etc.)     │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│                     Domain Layer                        │
│  ┌───────────────────────────────────────────────────┐  │
│  │         Domain Models & Business Logic            │  │
│  │      (Address, Dispensary, LicenseStatus)         │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│                  Infrastructure Layer                   │
│  ┌─────────────────────┐    ┌────────────────────────┐  │
│  │   PostgreSQL        │    │      Hibernate ORM     │  │
│  │   (Database)        │    │    (Data Access)       │  │
│  └─────────────────────┘    └────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 🛠️ Technologies

#### Backend
- **Java 21** - Server-side runtime and type safety
- **SpringBoot** - Web and application framework for REST API
- **Hibernate ORM** - Type-safe database client 
- **Flyway** - database script migrations
- **PostgreSQL** - Primary database for data persistence
- **JUnit** - Unit and integration testing framework

### 📁 Folder Structure

```
openspec-demo/
├── 📁 src/
|   ├── 📁 main/
|   |   ├── 📁 java/
│   |   |   └── 📁 com/example/demo/
│   |   |       ├── 📁 presentation/      # Controllers & Routes
│   |   |       │   ├── 📁 controllers/   # REST API controllers
│   |   |       │   └── 📁 api/           # HTTP request handlers and controllers
│   |   |       │       └── 📁 model/     # Http request/response DTOs
│   |   |       ├── 📁 application/       # Application services
│   |   |       │   ├── 📁 exceptions/    # Business errors
│   |   |       │   └── 📁 services/      # Business logic services interfaces
│   |   |       │       └── 📁 impl/      # Business logic services implementations
│   |   |       ├── 📁 domain/            # Domain layer
│   |   |       │   ├── 📁 models/        # Domain entities
│   |   |       │   └── 📁 repositories/  # Repository interfaces
│   |   |       ├── 📁 infrastructure/    # Infrastructure layer
│   |   |       │   ├── 📁 adapters/      # third-party access implementations, and repositories implementation
│   |   |       │   └── 📁 config/        # SpringBoot setup
│   |   |       └── DemoApplication       # Application entry point
|   |   └── 📁 resources/ 
|   |       └── application.yml           # Springboot properties for development
|   ├── 📁 test/
|   |   ├── 📁 java/
|   |   |   └── 📁 com/example/demo/
|   |   |       ├── 📁 application/
|   |   |       |   └── 📁 services/      # Unit tests (with mocked dependencies) for services and use cases
|   |   |       ├── 📁 presentation/
|   |   |       |   └── 📁 controllers/   # Unit tests (with mocked dependencies) for REST API controllers
|   |   |       └── 📁 integration/
|   |   |           ├── 📁 endpoints/     # Full integration tests for http endpoints
|   |   |           └── 📁 repositories/  # Repository tests with real postgres and rollback between tests
|   |   └── 📁 resources/ 
|   |       └── application.yml           # Springboot properties for unit and integration tests
|   └── 📁 site/
|       ├── site.xml                      # site maven plugin configuration
|       └── sonar-report.groovy           # Groovy script to add sonar report in site generation
│
├── 📁 docs/                              # Project documentation
|   ├── backend-standards.md              # All backend rules to apply when a feature is developed
│   ├── data-model.md                     # Data model and entity documentation
│   └── documentation-standards.md        # Rules to apply when this documentation is updated
│
├── 📁 flyway/                            # Flyway database scripts migration
|
├── 📁 openspec/                          # OpenSpec structure
|   ├── 📁 schemas
|   |   └── story-sdd                     # Custom OpenSpec schema (support for extended testing tasks)
|   ├── 📁 changes                        # OpenSpec historical specifications
|   └── config.yaml                       # OpenSpec configuration file 
│
├── 📁 .agents/                           # Reusable agentic artifacts
|   ├── 📁 agents                         # Extra agents definitions
|   ├── 📁 opencode
|   |   ├── 📁 commands                   # These commands must be replaced by symlink in subfolder commands in Opencode configuration
|   |   └── 📁 skills                     # These skill must be replaced by symlink in subfolder skills in Opencode configuration
|   ├── 📁 openspec
|   ├── 📁 prompts                        # Agent prompts to be used in SDD agentic flow
|   |   ├── 📁 opsx                       # Subagent to be triggered by opsx-orchestrator when it wants to run each OpenSpec step
|   |   └── opsx-orchestrator.md          # Main agent to orchestrate OpenSpec flow. It can run step by step or full automatic
|   ├── 📁 skills                         # All available skills in the project
|   └── opencode.json                     # Opencode configuration file
│
├── 📁 .opencode/                         # Opencode project configuration (sym links to subfolder in `.agents` folder)
|   ├── 📁 agents                         # symlink to `.agents/agents`
|   └── 📁 commands                       # Openspec commands and symlink to `.agents/opencode/commands`
|       └── 📁 skills                     # Openspec skills and symlink to `.agents/opencode/skills`
│
├── AGENTS.md                             # Agent agnostic file
├── .coderabbit.yaml                      # Coderabbit code review configuration for Github
├── docker-compose.yml                    # PostgreSQL containerization
├── Dockerfile                            # Containerization for this application
├── LICENSE                               # License file
├── license-header.txt                    # Header to use in all java source files
├── pom.xml                               # Apache Maven configuration file
└── README.md                             # This file
```

## 🤖 Agentic layer

- **OpenSpec** - Spec Driven Development framework
- **OpenCode** - Agent tool
- **CodeRabbit** - agentic review on pull requests

opencode run -m opencode/big-pickle --agent backend-developer   "Review the recent code changes and provide feedback on:
  - Code quality and readability
  - Possible bugs or issues
  - Security considerations
  - Best-practices compliance

  Provide specific improvement suggestions."



opencode run -m opencode/big-pickle --agent backend-developer   "Review (using skill /adversarial-review) the recent code changes and provide feedback on:
  - Code quality and readability
  - Possible bugs or issues
  - Security considerations
  - Best-practices compliance

  Provide specific improvement suggestions."
  
  
  