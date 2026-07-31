---
description: Backend development standards, best practices, and conventions for this Java SpringBoot application including Domain-Driven Design, SOLID principles, architecture patterns, API design, and testing practices
globs: ["src/**/*.java","src/**/*.properties","src/**/*.yaml","src/**/*.yml"]
alwaysApply: true
---

# Backend Project Standards and Best Practices

## Table of Contents

- [Overview](#overview)
- [Technology Stack](#technology-stack)
  - [Core Technologies](#core-technologies)
  - [Database & ORM](#database--orm)
  - [Testing Framework](#testing-framework)
- [Architecture Overview](#architecture-overview)
  - [Domain-Driven Design (DDD)](#domain-driven-design-ddd)
  - [Layered Architecture](#layered-architecture)
  - [Project Structure](#project-structure)
- [Domain-Driven Design Principles](#domain-driven-design-principles)
  - [Entities](#entities)
  - [Value Objects](#value-objects)
  - [Aggregates](#aggregates)
  - [Repositories](#repositories)
  - [Domain Services](#domain-services)
  - [Additional Recommendations](#additional-recommendations)
- [SOLID and DRY Principles](#solid-and-dry-principles)
  - [Single Responsibility Principle (SRP)](#single-responsibility-principle-srp)
  - [Open/Closed Principle (OCP)](#openclosed-principle-ocp)
  - [Liskov Substitution Principle (LSP)](#liskov-substitution-principle-lsp)
  - [Interface Segregation Principle (ISP)](#interface-segregation-principle-isp)
  - [Dependency Inversion Principle (DIP)](#dependency-inversion-principle-dip)
  - [DRY (Don't Repeat Yourself)](#dry-dont-repeat-yourself)
- [Coding Standards](#coding-standards)
  - [Naming Conventions](#naming-conventions)
  - [Error Handling](#error-handling)
  - [Validation Patterns](#validation-patterns)
  - [Logging Standards](#logging-standards)
- [API Design Standards](#api-design-standards)
  - [REST Endpoints](#rest-endpoints)
  - [Request/Response Patterns](#requestresponse-patterns)
  - [Error Response Format](#error-response-format)
  - [CORS Configuration](#cors-configuration)
- [Database Patterns](#database-patterns)
  - [Migrations](#migrations)
  - [Repository Pattern](#repository-pattern)
- [Testing Standards](#testing-standards)
  - [Unit Testing](#unit-testing)
  - [Integration Testing](#integration-testing)
  - [Test Coverage Requirements](#test-coverage-requirements)
  - [Mocking Standards](#mocking-standards)
- [Performance Best Practices](#performance-best-practices)
  - [Database Query Optimization](#database-query-optimization)
  - [Async/Await Patterns](#asyncawait-patterns)
  - [Error Handling Performance](#error-handling-performance)
- [Security Best Practices](#security-best-practices)
  - [Input Validation](#input-validation)
  - [Environment Variables](#environment-variables)
  - [Dependency Injection](#dependency-injection)
- [Development Workflow](#development-workflow)
  - [Git Workflow](#git-workflow)
  - [Development Scripts](#development-scripts)
  - [Code Quality](#code-quality)

---

## Overview

This document outlines the best practices, conventions, and standards used in this application. The backend follows Domain-Driven Design (DDD) principles and implements a layered architecture to ensure code consistency, maintainability, and scalability.

## Technology Stack

### Core Technologies
- **Java**: Runtime environment
- **SpringBoot**: Web application framework
- **JPA(Hibernate)**: Modern ORM for database access

### Database & ORM
- **PostgreSQL**: Relational database (Docker container)
- **Flyway Migrate**: Database migration tool

### Testing Framework
- **JUnit5 + SpringBoot**: Testing framework
- **Coverage Threshold**: 90% for branches, functions, lines, and statements
- **Test Location**: `src/test/java` directories and `*Test.java`|`*Tests.java`|`*IntegrationTest.java`|`*IntegrationTests.java`|`*ITest.java`|`*ITests.java` files

## Architecture Overview

### Domain-Driven Design (DDD)

Domain-Driven Design is a methodology that focuses on modeling software according to business logic and domain knowledge. By centering development on a deep understanding of the domain, DDD facilitates the creation of complex systems.

**Benefits:**
- **Improved Communication**: Promotes a common language between developers and domain experts, improving communication and reducing interpretation errors.
- **Clear Domain Models**: Helps build models that accurately reflect business rules and processes.
- **High Maintainability**: By dividing the system into subdomains, it facilitates maintenance and software evolution.

### Layered Architecture

The backend follows a layered DDD architecture:

**Presentation Layer** (`src/main/java/com/example/demo/presentation/`)
- Controllers handle HTTP requests/responses
- Routes define API endpoints
- Controllers use services from Application layer

**Application Layer** (`src/main/java/com/example/demo/application/`)
- Services contain business logic and orchestration
- Validator handles input validation
- Services use repositories from Domain layer

**Domain Layer** (`src/main/java/com/example/demo/domain/`)
- Models define core business entities (User, Role, etc.)
- Repository interfaces define data access contracts
- Pure business logic without external dependencies

**Infrastructure Layer** (`src/main/java/com/example/demo/infrastructure/`)
- JPA handles database operations
- Repository implementations (via SpringBoot) satisfy domain interfaces

### Project Structure

```
src/
├── main/
|   ├── java/
|   |   └── com.example.demo
|   |       ├── application/
|   |       │   ├── exceptions/           # Business errors
|   |       |   ├── services/             # Business logic services interfaces
|   |       |   |   └── impl/             # Business logic services implementations
|   |       │   └── validators/           # Business validators
|   |       ├── domain/
|   |       │   ├── models/               # Domain entities
|   |       │   └── repositories/         # Repository interfaces
|   |       ├── infrastructure/
|   |       |   ├── adapters/             # third-party access implementations, and repositories implementation
|   |       │   └── config/               # SpringBoot setup
|   |       ├── presentation/
|   │       |   ├── api/                  # Controller interfaces
|   |       |   |   └── model/            # Http request/response DTOs
|   │       |   └── controllers/          # HTTP request handlers and controllers implementations
|   |       └── DemoApplication.java      # Application entry point
|   └── resources/
|       └── application.yml               # SpringBoot properties
└── test/
    └── java/
        ├── com.example.demo
        |   ├── application/
        |   |   └── services/             # Business unit tests, all dependencies are mocked
        |   ├── domain/
        |   |   └── models/               # Business unit tests, all dependencies are mocked
        |   ├── infrastructure/
        |   |   └── adapters/             # Unit tests, all dependencies are mocked
        |   ├── presentation/
        |   |   └── controllers/          # Controllers unit tests, all dependencies are mocked (e.g. services or repositories)
        |   └── integration/
        |       ├── adapters/             # Adapters integration unit tests when it is needed
        |       ├── endpoints/            # Endpoint integration unit tests, all dependencies are real implementations, no mocked
        |       └── repositories/         # Repository integration unit tests, all dependencies are real implementations, no mocked
        └── resources/
            └── application.yml           # SpringBoot testing properties
```
## Domain-Driven Design Principles (DDD)

### Entities

Entities are objects with a distinct identity that persists over time.

**Best Practice**: Entities should encapsulate business logic related to their domain concept and maintain consistency of their internal state.

### Value Objects

Value Objects describe aspects of the domain without conceptual identity. They are defined by their attributes rather than an identifier.

### Aggregates

Aggregates are clusters of objects that must be treated as a unit. They have a root entity that enforces invariants and consistency boundaries.

**Recommendation**: Aggregates should be carefully designed to ensure that all operations within the aggregate boundary maintain consistency.

### Repositories

Repositories provide interfaces for accessing aggregates and entities, encapsulating data access logic.

**Recommendation**: 
- Develop complete repository interfaces for each entity and aggregate, ensuring all database interactions for those entities pass through the repository
- Implement repository methods that handle collections of entities that can be filtered or modified in bulk
- Use dependency injection to inject Datasource into repositories when Spring data is not enough.

### Domain Services

Domain Services contain business logic that doesn't naturally belong to an entity or value object.

### Additional Recommendations

**Use of Factories**

Factories are useful in DDD to encapsulate the logic of creating complex objects, ensuring that all created objects comply with domain rules from the moment of creation.
Implement factories for the creation of entities and aggregates, especially those that are complex and require specific initial configuration that complies with business rules.

**Improvement in Relationship Modeling**

Relationships between entities and aggregates must be clear and consistent with business rules.
Review and possibly redesign relationships between entities to ensure they accurately reflect domain needs and rules. This may include removing unnecessary relationships or adding new relationships that facilitate business operations.

**Domain Events Integration**

Domain events are an important part of DDD and can be used to handle side effects of domain operations in a decoupled manner.
Implement a domain event system that allows entities and aggregates to publish events that other system components can handle without being tightly coupled to the entities that generate them.

## SOLID and DRY Principles

### SOLID Principles

SOLID principles are five object-oriented design principles that help create more understandable, flexible, and maintainable systems.

#### Single Responsibility Principle (SRP)

Each class should have a single responsibility or reason to change. 
Separate data access logic into a repository layer to adhere more closely to SRP.

#### Open/Closed Principle (OCP)

Software entities should be open for extension but closed for modification.
Use factory methods to create instances, allowing for easier extension without modifying existing code.

#### Liskov Substitution Principle (LSP)

Objects of a derived class should be replaceable with objects of the base class without altering the program's functionality.
Continue using composition to avoid LSP violations and ensure that any future inheritance structures allow derived classes to substitute their base classes without altering how the program works.

#### Interface Segregation Principle (ISP)

Many specific interfaces are better than a single general interface.
Define more granular interfaces for service classes to ensure they only implement the methods they need.

#### Dependency Inversion Principle (DIP)

High-level modules should not depend on low-level modules; both should depend on abstractions.
Use dependency injection to invert the dependency, relying on abstractions rather than concrete implementations. Inject through the constructor when possible, or by setter method failing that. 

### DRY (Don't Repeat Yourself)

The DRY principle focuses on reducing duplication in code. Each piece of knowledge should have a single, unambiguous, and authoritative representation within a system.
Abstract common database operation logic into a reusable function or class.

## Coding Standards

### Naming Conventions

- **Variable Naming**: Use camelCase for variables and functions (e.g., `candidateId`, `findCandidateById`)
- **Class Naming**: Use PascalCase for classes and interfaces (e.g., `Candidate`, `CandidateRepository`)
- **Constants Naming**: Use UPPER_SNAKE_CASE for constants (e.g., `MAX_CANDIDATES_PER_PAGE`)
- **Type Naming**: Use PascalCase for types and interfaces (e.g., `CandidateData`, `ICandidateRepository`)
- **File Naming**: Use camelCase for file names (e.g., `CandidateService.java`, `CandidateController.java`)

### Error Handling

- **Custom Error Classes**: Create domain-specific error classes
- **Error Handler**: Use global error handler for consistent error responses
- **Error Messages**: Provide descriptive error messages for debugging

```java
public class NotFoundException extends Exception {
  NotFoundException(String message) {
    super(message);
  }
}

// In controller
  var candidate = candidateService.findById(id);
  if (!candidate) {
    throw new NotFoundException('Candidate not found');
  }
  candidate;
```

### Validation Patterns

- **Input Validation**: Validate all inputs at the application layer
- **Use Validator Module**: Centralize validation logic in package `application/validators`
- **Validate Before Processing**: Always validate before executing business logic


### Logging Standards

- **Use Class's Logger**: Use the lombok annotation `Slf4j` at class level.
- **Log Levels**: Use appropriate log levels (info, error, warn, debug)
- **Structured Logging**: Include relevant context in log messages

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApplicationReadyInitializer {
}

log.info('User created {}', user.id );
log.error('Failed to create user {}', error.message);
```

## API Design Standards

### REST Endpoints

- **RESTful Naming**: Use RESTful conventions for endpoint naming
- **HTTP Methods**: Use appropriate HTTP methods (GET, POST, PUT, DELETE, PATCH)
- **Resource-Based URLs**: URLs should represent resources, not actions

```java
GET    /<entities>           // List entities
GET    /<entities>/{id}      // Get entity by ID
POST   /<entities>           // Create new entity
PUT    /<entities>/{id}      // Update entity
DELETE /<entities>/{id}      // Delete entity
```

### Request/Response Patterns

- **JSON Format**: Use JSON for request and response bodies
- **Consistent Structure**: Maintain consistent response structure across all endpoints
- **Status Codes**: Use appropriate HTTP status codes

```json
// Success response
{
    "success": true,
    "data": { ... },
    "message": "Operation completed successfully"
}

// Error response
{
    "success": false,
    "error": {
        "message": "Error description",
        "code": "ERROR_CODE"
    }
}
```

### Error Response Format

- **Consistent Format**: All errors should follow the same response structure
- **Error Codes**: Use meaningful error codes for different error types
- **HTTP Status Codes**: Map errors to appropriate HTTP status codes

```json
// 400 Bad Request
{
    "success": false,
    "error": {
        "message": "Validation failed",
        "code": "VALIDATION_ERROR",
        "details": [ ... ]
    }
}

// 404 Not Found
{
    "success": false,
    "error": {
        "message": "Resource not found",
        "code": "NOT_FOUND"
    }
}
```

### CORS Configuration

- **Enable CORS**: Configure CORS to allow frontend origin
- **Secure Configuration**: Only allow specific origins in production
- **Credentials**: Configure credentials handling appropriately

## Database Patterns

### JPA Persistence Schema

- **Single Source of Truth**: package `domain/models` is the single source of truth for database structure
- **Relationships**: Define relationships using JPA relations
- **Naming Conventions**: Use consistent naming conventions (camelCase for fields, PascalCase for models)

### Migrations

- **Version Control**: All database changes must be version-controlled through migrations using Flyway
- **Migration Naming**: Use descriptive names for migrations
- **Review Migrations**: Review migration files before applying

```bash
  TODO - Add Flyway commando to migrate DB
```

### Repository Pattern

- **Repository Interfaces**: Define repository interfaces in the domain layer
- **Spring Data Implementation**: Implement repositories using Spring Data in the infrastructure layer
- **Dependency Injection**: Inject Datasource into repositories when it is needed

```java
// Domain layer interface
@Repository
public interface IEntityRepository extends JpaRepository {
    Entity findById(long id);
    save(Entity entity);
}
```

## Testing Standards

The project has strict requirements for code quality and maintainability. These are the unit testing standards and best practices that must be applied. 

### Test File Structure
- Use descriptive test file names: `[componentName]Test.java`
- Place unit test files in folder /src/test/java alongside the package where source code they test 
- Place integration test file in folder /src/test/java/<maven group id>/integration/endpoints, /src/test/java/<maven group id>/integration/repositories, etc 
- Use Junit as the testing framework
- Maintain 90% coverage threshold for branches, functions, lines, and statements

### Test Case Naming Convention
- Use descriptive, behavior-driven naming: `should_[expected_behavior]_when_[condition]`

### Test Structure (AAA/GWT Pattern)
Always follow the Arrange Act Assert or Given-When-Then pattern:

Assertion pattern:
- Use specific matchers: `Assertions.assertThrowsExactly()`, `BDDMockito.then().should(times(1))`
- Verify both successful operations and error conditions
- Check that mocks were called with correct parameters
- Assert on return values and side effects

### Mocking Standards

- Mock all external dependencies (models, services, database clients)
- Mock repository layers in service unit tests
- Mock service layers in controller unit tests
- Create mock instances with realistic data structures
- Clear all mocks in `beforeEach()` to ensure test isolation

### Test Coverage Requirements

- **Comprehensive test coverage**: Include these test categories for each function:
1. **Happy Path Tests**: Valid inputs producing expected outputs
2. **Error Handling Tests**: Invalid inputs, missing data, database errors
3. **Edge Cases**: Boundary values, null/undefined inputs, empty data
4. **Validation Tests**: Input validation, business rule enforcement
5. **Integration Points**: External service calls, database operations

- **Threshold**: 90% for branches, functions, lines, and statements
- **Coverage Reports**: Generate coverage reports with `mvn test`
- **Coverage Files**: Coverage reports in `target/` directory


### Error Testing
- Test both expected errors and unexpected errors
- Verify error messages are descriptive and helpful
- Test error propagation through service layers
- Ensure proper HTTP status codes in controller tests

### Controller Testing Specifics
- Mock the service layer completely
- Test HTTP request/response handling
- Verify parameter parsing and validation
- Test error response formatting
- Use realistic Request/Response mocks

### Service Testing Specifics
- Mock domain models and repositories
- Test business logic in isolation
- Verify data transformation and validation
- Test error handling and edge cases
- Mock external dependencies (JPA, validators)

### Database Testing
- Mock Datasource and all database operations
- Test both successful and failed database operations
- Verify correct database queries and parameters
- Test transaction handling and rollback scenarios

### Test Data Management
- Use factory functions for creating test data
- Keep test data consistent and realistic
- Avoid hardcoded values in multiple places
- Use meaningful test data that reflects real-world scenarios

### Integration Testing

- **Controller Testing**: Test HTTP request/response handling
- **Database Testing**: Test repository implementations with database
- **End-to-End Flow**: Test complete request flows


### Code Quality Standards

#### Documentation
- Write clear, descriptive test names that explain the scenario
- Add comments for complex test setups
- Document any special test conditions or edge cases
- Keep test code as readable as production code

#### Performance Considerations
- Keep tests fast and focused
- Avoid unnecessary async operations in tests
- Use appropriate mock strategies to avoid real I/O
- Group related tests to minimize setup/teardown overhead

### Integration with Development Workflow
- Run tests before every commit
- Ensure all tests pass before merging
- Use test-driven development when appropriate
- Update tests when modifying existing functionality

### Common Anti-Patterns to Avoid
- Don't test implementation details, test behavior
- Don't create overly complex test setups
- Don't ignore failing tests or skip error scenarios
- Don't use real database connections in unit tests
- Don't create tests that depend on external services
- Don't write tests that are too tightly coupled to implementation

## Performance Best Practices

### Database Query Optimization

- **Select Specific Fields**: Only select fields that are needed
- **Use Indexes**: Ensure proper database indexes for frequently queried fields
- **Avoid N+1 Queries**: Use JPA's `inner fetch` to fetch related data efficiently

### Error Handling Performance

- **Early Returns**: Return early to avoid unnecessary processing
- **Error Propagation**: Let errors propagate naturally through the call stack
- **Avoid Over-Wrapping**: Don't wrap errors unnecessarily

## Security Best Practices

### Input Validation

- **Validate All Inputs**: Validate all user inputs before processing
- **Sanitize Data**: Sanitize data to prevent injection attacks
- **Type Checking**: Use validation to ensure type safety

### Environment Variables

- **Never Commit Secrets**: Never commit `.env` files or secrets to version control
- **Use Environment Variables**: Use environment variables for configuration
- **Validate Environment**: Validate required environment variables at startup

### Dependency Injection

- **Inject Datasource**: Inject dataousrce via Spring
- **Avoid Global State**: Avoid global state for database connections
- **Testability**: Use dependency injection to improve testability

## Development Workflow

### Git Workflow

- **Feature Branches**: Develop features in separate branches, adding descriptive suffix "-backend" to allow working in parallel and avoid conflicts or collisions
- **Descriptive Commits**: Write descriptive commit messages in English
- **Code Review**: Code review before merging
- **Small Branches**: Keep branches small and focused

### Development Scripts

```bash
docker compose up                          # Start thrid-party service for development and testing
docker compose down                        # Stop and clean thrid-party service for development and testing
mvn flyway:migrate                         # run flyway scripts on development database
mvn flyway:info                            # show flyway development database info
mvn spring-boot:run                        # Development server
mvn package                                # Build for production
mvn test                                   # Run tests
mvn -PnativeTest test                      # Run native tests
mvn clean spring-boot:build-image -Pnative
```

### Code Quality

- **ESLint Validation**: Run ESLint before commits
- **Java Compilation**: Ensure Java compiles without errors
- **All Tests Passing**: Ensure all tests pass before deployment
- **Code Review**: Review code for adherence to standards

