# KAN-6: Create Brands Entity and CRUD Endpoints - Enriched User Story

## Original Content

**Summary:** Create Brands Entity and CRUD Endpoints

**User Story:** As an user, I want to create brands.

**Description:**
The user need to create brands which represents a cannabis product brand that manufactures or supplies products.
The Entity is not mapped in JPA, so it should be mapped.

**Endpoints specified:**
1. GET /api/brands — Get all brands which are not deleted
2. POST /api/brands — Create a new brand
3. GET /api/brands/{brandId} — Get info for a brand if it is not deleted
4. PATCH /api/brands/{brandId} — Partial or full brand update, if it is not deleted

## Analysis

The original user story is **insufficiently detailed** for a developer to work autonomously. It lacks:

- BrandType entity (referenced via `brand_type_id` FK) is not mentioned as a dependency
- No package structure defined for the Brand domain (following DDD modularization)
- No service interface/implementation pattern defined
- No API interface (following the existing `DispensaryApi` pattern)
- No request/response DTOs defined (request validation, response structures)
- No PATCH endpoint design defined (how partial update works, what fields are updatable)
- No repository interfaces defined
- No unit test specifications
- No integration test specifications
- No validation rules specified per field
- No non-functional requirements (security, performance, error handling)
- No definition of done with acceptance criteria

---

## Enriched User Story

### Description

As a platform administrator, I want to create, read, update, and soft-delete brands so that I can manage the cannabis product brands that manufacture or supply products in the system.

The Brand domain follows the same DDD layered architecture pattern as the existing Dispensary domain. This includes:

1. **Brand** entity with full JPA mapping (already has DB table via Flyway migration `V0.1.0`)
2. **BrandType** lookup entity (already has DB table via Flyway migration `V0.1.0`)
3. Full CRUD endpoints (GET all, GET by ID, POST create, PATCH update)
4. Soft-delete semantics (filter out deleted records, allow restoring on update)

### Technical Requirements

#### 1. Project Structure

All new files follow the established DDD layered architecture:

```
src/main/java/com/example/demo/
├── domain/
│   ├── models/
│   │   └── brand/                          # <-- NEW PACKAGE
│   │       ├── Brand.java                  # Brand entity (extends BaseEntity)
│   │       └── BrandType.java              # BrandType lookup entity
│   └── repositories/
│       ├── BrandRepository.java            # <-- NEW
│       └── BrandTypeRepository.java        # <-- NEW
├── application/
│   ├── services/
│   │   ├── BrandService.java               # <-- NEW (interface)
│   │   └── impl/
│   │       └── BrandServiceImpl.java       # <-- NEW (implementation)
│   └── validators/                         # (optional validation logic)
├── presentation/
│   ├── api/
│   │   ├── BrandApi.java                   # <-- NEW (endpoint interface)
│   │   └── model/
│   │       ├── CreateBrandRequest.java     # <-- NEW
│   │       ├── CreateBrandResponse.java    # <-- NEW
│   │       ├── GetAllBrandsResponse.java   # <-- NEW
│   │       ├── GetBrandResponse.java       # <-- NEW
│   │       └── UpdateBrandRequest.java     # <-- NEW (for PATCH)
│   └── controllers/
│       └── BrandController.java            # <-- NEW
```

#### 2. Domain Entities

##### BrandType Entity

Package: `com.example.demo.domain.models.brand`

- Simple lookup entity (comparable to `LicenseStatus`)
- Does NOT extend `BaseEntity` (no audit/soft-delete fields needed)
- Table: `brand_types`
- Fields (from data model):
  - `id` — `Integer`, PK, auto-increment
  - `name` — `String`, NOT NULL

**Entity implementation pattern** (follows `LicenseStatus.java` style):

```java
package com.example.demo.domain.models.brand;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(name = "brand_types")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(nullable = false)
    private String name;
}
```

##### Brand Entity

Package: `com.example.demo.domain.models.brand`

- Extends `BaseEntity` (supports soft-delete and audit fields)
- Table: `brands`
- Fields (from data model):
  - `id` — `Integer`, PK, auto-increment
  - `name` — `String`, NOT NULL
  - `description` — `String`, TEXT column, NOT NULL
  - `email` — `String`, NOT NULL
  - `stateLicense` — `String`, column `state_license`, NOT NULL
  - `brandType` — `BrandType`, @ManyToOne(fetch = LAZY), @JoinColumn(name = "brand_type_id")
  - `logoImageUrl` — `String`, column `logo_image_url`, NOT NULL
  - `instagramUrl` — `String`, column `instagram_url`, nullable
  - `twitterUrl` — `String`, column `twitter_url`, nullable
  - `facebookUrl` — `String`, column `facebook_url`, nullable
  - `websiteUrl` — `String`, column `website_url`, nullable
  - `adminId` — `Integer`, column `admin_id`, NOT NULL
  - `enabled` — `Boolean`, nullable

**Entity implementation pattern** (follows `Dispensary.java` style):

```java
package com.example.demo.domain.models.brand;

import com.example.demo.domain.models.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "brands")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Brand extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(nullable = false)
    private String email;

    @Column(name = "state_license", nullable = false)
    private String stateLicense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_type_id")
    private BrandType brandType;

    @Column(name = "logo_image_url", nullable = false)
    private String logoImageUrl;

    @Column(name = "instagram_url")
    private String instagramUrl;

    @Column(name = "twitter_url")
    private String twitterUrl;

    @Column(name = "facebook_url")
    private String facebookUrl;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "admin_id", nullable = false)
    private Integer adminId;

    private Boolean enabled;
}
```

#### 3. Repository Interfaces

##### BrandRepository

Package: `com.example.demo.domain.repositories`

```java
package com.example.demo.domain.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.demo.domain.models.brand.Brand;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {

    Iterable<Brand> findAllByDeletedAtIsNull();

    java.util.Optional<Brand> findByIdAndDeletedAtIsNull(Integer id);
}
```

##### BrandTypeRepository

Package: `com.example.demo.domain.repositories`

```java
package com.example.demo.domain.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.demo.domain.models.brand.BrandType;

@Repository
public interface BrandTypeRepository extends JpaRepository<BrandType, Long> {

    Optional<BrandType> findByName(String name);
}
```

> **Note:** The `findByName` method allows looking up BrandType by name (similar to `LicenseStatusRepository.findByState`). This supports the creation flow where the API receives a brand type name string and resolves it to the entity.

#### 4. Service Layer

##### BrandService Interface

Package: `com.example.demo.application.services`

```java
package com.example.demo.application.services;

import java.util.Optional;
import com.example.demo.application.exceptions.NotFoundException;
import com.example.demo.domain.models.brand.Brand;

public interface BrandService {

    Iterable<Brand> findAll();

    Brand create(String name, String description, String email, String stateLicense,
                 String brandTypeName, String logoImageUrl, String instagramUrl,
                 String twitterUrl, String facebookUrl, String websiteUrl,
                 Integer adminId, Boolean enabled);

    Optional<Brand> getById(Integer id);

    Brand update(Integer id, String name, String description, String email, String stateLicense,
                 String brandTypeName, String logoImageUrl, String instagramUrl,
                 String twitterUrl, String facebookUrl, String websiteUrl,
                 Integer adminId, Boolean enabled) throws NotFoundException
}
```

##### BrandServiceImpl Implementation

Package: `com.example.demo.application.services.impl`

- Follows the same pattern as `DispensaryServiceImpl`
- Uses constructor injection for `BrandRepository` and `BrandTypeRepository`
- `findAll()` returns `brandRepository.findAllByDeletedAtIsNull()`
- `create()` resolves `BrandType` by name, creates and saves `Brand`
- `getById()` finds by ID and filters out soft-deleted records (`deletedAt == null`)
- `update()` finds existing brand (throws `NotFoundException` if not found or deleted), updates all fields, saves and returns updated entity

#### 5. API Interface (BrandApi)

Package: `com.example.demo.presentation.api`

Follows the `DispensaryApi` pattern with OpenAPI/Swagger annotations:

| Method | Endpoint | Status | Description |
|--------|----------|--------|-------------|
| GET | `/api/brands` | 200 OK | List all non-deleted brands |
| POST | `/api/brands` | 201 Created | Create a new brand |
| GET | `/api/brands/{brandId}` | 200 OK | Get brand by ID (only if not deleted) |
| PATCH | `/api/brands/{brandId}` | 200 OK | Partial update of brand (only if not deleted) |

**Error responses:**
- 400 Bad Request — validation errors
- 404 Not Found — brand not found or already deleted

**PATCH endpoint design:**

The PATCH endpoint accepts a request body where all fields are optional (nullable). Only the fields present in the request body are updated. Fields not included remain unchanged.

> **Important:** Java records cannot use `@Builder` with partial updates cleanly. Use a class with `@Data` and nullable fields for the PATCH request, or use `Map<String, Object>` + manual field mapping. **Recommendation:** Use a class-based DTO (`UpdateBrandRequest`) with `@Data` and all fields nullable, then the service only sets non-null fields on the existing entity.

#### 6. Request/Response DTOs

##### CreateBrandRequest

Package: `com.example.demo.presentation.api.model`

- Lombok `@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor` (same pattern as `CreateDispensaryRequest`)
- Jakarta Validation annotations for required fields
- Fields:
  - `name` — `@NotNull`, `@NotEmpty` — brand name
  - `description` — `@NotNull`, `@NotEmpty` — brand description
  - `email` — `@NotNull`, `@NotEmpty`, `@Email` — contact email
  - `stateLicense` — `@NotNull`, `@NotEmpty` — state license number
  - `brandTypeName` — `@NotNull`, `@NotEmpty` — brand type name (resolved to BrandType entity)
  - `logoImageUrl` — `@NotNull`, `@NotEmpty` — logo image URL
  - `instagramUrl` — nullable
  - `twitterUrl` — nullable
  - `facebookUrl` — nullable
  - `websiteUrl` — nullable
  - `adminId` — `@NotNull` — admin user ID
  - `enabled` — nullable (boolean)

##### CreateBrandResponse

Package: `com.example.demo.presentation.api.model`

- Java `record` (same pattern as `CreateDispensaryResponse`)
- Fields: `id`, `name`, `description`, `email`, `stateLicense`, `brandTypeName`, `logoImageUrl`, `instagramUrl`, `twitterUrl`, `facebookUrl`, `websiteUrl`, `adminId`, `enabled`

##### GetBrandResponse

Package: `com.example.demo.presentation.api.model`

- Java `record`
- Same structure as `CreateBrandResponse` fields

##### GetAllBrandsResponse

Package: `com.example.demo.presentation.api.model`

- Java `record`
- Compact view: `id`, `name`, `description`, `email`, `stateLicense`, `brandTypeName`, `logoImageUrl`, `enabled`

##### UpdateBrandRequest

Package: `com.example.demo.presentation.api.model`

- Lombok `@Data` (class, not record — all fields nullable for partial update)
- Fields identical to `CreateBrandRequest` but ALL optional (no `@NotNull`/`@NotEmpty`)

#### 7. Controller (BrandController)

Package: `com.example.demo.presentation.controllers`

- Implements `BrandApi`
- Uses constructor injection of `BrandService`
- Maps between request DTOs and service method parameters
- Wraps responses in `DataResponse<T>`
- Throws `NotFoundException` when entity not found

#### 8. Endpoint Mappings Summary

| Endpoint | Method | Request Body | Response | Error |
|----------|--------|-------------|----------|-------|
| `/api/brands` | GET | — | `DataResponse<List<GetAllBrandsResponse>>` (200) | — |
| `/api/brands` | POST | `CreateBrandRequest` | `DataResponse<CreateBrandResponse>` (201) | 400 validation |
| `/api/brands/{brandId}` | GET | — | `DataResponse<GetBrandResponse>` (200) | 404 not found |
| `/api/brands/{brandId}` | PATCH | `UpdateBrandRequest` | `DataResponse<GetBrandResponse>` (200) | 404 not found, 400 validation |

#### 9. API Response Format

```json
// Success
{
  "data": { ... }
}

// Error (400/404)
{
  "timestamp": "2026-07-21T22:00:00Z",
  "status": 404,
  "path": "/api/brands/999",
  "errors": [
    { "field": "general", "message": "Brand not found with ID: 999" }
  ]
}
```

#### 10. Unit Testing Requirements

##### Service Unit Tests (`BrandServiceTests.java`)

Location: `src/test/java/com/example/demo/application/services/`

Follows the `DispensaryServiceTests` pattern:

- Use `@ExtendWith(SpringExtension.class)` with `@TestConfiguration` + `@ComponentScan`
- Mock `BrandRepository` and `BrandTypeRepository` with `@MockitoBean`
- Test cases:
  1. `findAll()` returns list of non-deleted brands
  2. `create()` with valid data creates and saves brand successfully
  3. `create()` with invalid brand type name throws `NotFoundException`
  4. `getById()` with valid ID returns brand
  5. `getById()` with deleted brand returns empty
  6. `update()` with valid data updates brand fields
  7. `update()` with non-existent ID throws `NotFoundException`
  8. `update()` with deleted brand throws `NotFoundException`

##### Controller Unit Tests (`BrandControllerTests.java`)

Location: `src/test/java/com/example/demo/presentation/controllers/`

- Mock `BrandService`
- Test HTTP status codes and response structures
- Test validation error handling

##### Integration Tests (`BrandEndpointsTests.java`)

Location: `src/test/java/com/example/demo/integration/endpoints/`

- Extends `EndpointIntegrationTest`
- Uses `MockMvc` for full request/response testing
- Test cases:
  1. POST brand with valid data returns 201
  2. POST brand with invalid data returns 400
  3. GET all brands returns 200
  4. GET brand by valid ID returns 200
  5. GET brand by non-existent ID returns 404
  6. PATCH brand with valid data returns 200
  7. PATCH brand by non-existent ID returns 404

##### Repository Integration Tests (`BrandRepositoryTests.java`)

Location: `src/test/java/com/example/demo/integration/repositories/`

- Tests `findAllByDeletedAtIsNull()` filtering
- Tests `findByIdAndDeletedAtIsNull()`

#### 11. Implementation Order

The implementation should follow this dependency order:

1. **Domain Entities** — `BrandType.java`, `Brand.java`
2. **Repository Interfaces** — `BrandTypeRepository.java`, `BrandRepository.java`
3. **Service Interface** — `BrandService.java`
4. **Service Implementation** — `BrandServiceImpl.java`
5. **Request/Response DTOs** — All `*Request.java` and `*Response.java`
6. **API Interface** — `BrandApi.java`
7. **Controller** — `BrandController.java`
8. **Unit Tests** — Service tests, controller tests
9. **Integration Tests** — Endpoint tests, repository tests

#### 12. Non-Functional Requirements

- **Performance**: Use `FetchType.LAZY` on the `brandType` relationship; avoid N+1 queries
- **Security**: All inputs validated via Jakarta Validation annotations; no raw SQL
- **Error Handling**: Use `GlobalExceptionHandler` (already exists) for consistent error responses
- **Maintainability**: Follow existing DDD layered pattern; one class per file; SRP compliance
- **Soft Delete**: Brand records are never physically deleted; use `deletedAt` field for filtering
- **Logging**: Use `@Slf4j` annotation on service implementation class
- **Type Safety**: Use `Integer` for nullable fields, primitives where applicable
- **Code Coverage**: Minimum 90% coverage for branches, functions, lines, and statements

#### 13. Definition of Done

- [ ] `BrandType` entity created in `domain/models/brand/` with correct JPA mapping
- [ ] `Brand` entity created in `domain/models/brand/` extending `BaseEntity` with correct JPA mapping
- [ ] `BrandTypeRepository` and `BrandRepository` interfaces created in `domain/repositories/`
- [ ] `BrandService` interface created in `application/services/`
- [ ] `BrandServiceImpl` created in `application/services/impl/` with full CRUD operations
- [ ] Request/Response DTOs created in `presentation/api/model/`
- [ ] `BrandApi` interface created in `presentation/api/` with OpenAPI documentation
- [ ] `BrandController` created in `presentation/controllers/` implementing `BrandApi`
- [ ] All 4 endpoints working: `GET /api/brands`, `POST /api/brands`, `GET /api/brands/{id}`, `PATCH /api/brands/{id}`
- [ ] Soft-delete semantics correctly applied (deleted brands excluded from GET/list, update on deleted returns 404)
- [ ] Service unit tests created and passing
- [ ] Controller unit tests created and passing
- [ ] Integration endpoint tests created and passing
- [ ] `mvn compile` completes without errors
- [ ] `mvn test` passes (90% coverage threshold)
- [ ] Code follows project conventions (DDD layered architecture, naming conventions, annotations)
- [ ] No unused imports or dead code
- [ ] Generated OpenAPI spec includes all new endpoints (via existing OpenApiConfig)
