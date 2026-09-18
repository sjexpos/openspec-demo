/**********
 This project is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the
 Free Software Foundation; either version 3.0 of the License, or (at your
 option) any later version. (See <https://www.gnu.org/licenses/gpl-3.0.html>.)

 This project is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 more details.

 You should have received a copy of the GNU General Public License
 along with this project; if not, write to the Free Software Foundation, Inc.,
 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
 **********/
// Copyright (c) 2026-2027 Sergio Exposito.  All rights reserved.              

package com.example.demo.integration.repositories;

import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * Shared native-SQL seeding for {@code strain_types} and {@code seed_companies} (D5: deliberately
 * unmapped entities). Reused by {@link StrainRepositoryTests} and {@link ProductRepositoryTests}
 * so the "insert a row nothing maps" boilerplate is not duplicated across the two fixture-heavy
 * test classes. Each test's own arrange block stays explicit and self-contained (dry-principle:
 * over-DRY fixtures are an anti-pattern) — only this narrow, mechanical seeding is shared.
 */
final class StrainReferenceDataFixtures {

  private StrainReferenceDataFixtures() {}

  static Integer seedStrainType(TestEntityManager entityManager) {
    return ((Number)
            entityManager
                .getEntityManager()
                .createNativeQuery("INSERT INTO strain_types (name) VALUES ('Sativa') RETURNING id")
                .getSingleResult())
        .intValue();
  }

  static Integer seedSeedCompany(TestEntityManager entityManager) {
    return ((Number)
            entityManager
                .getEntityManager()
                .createNativeQuery(
                    "INSERT INTO seed_companies (name) VALUES ('Acme Seeds') RETURNING id")
                .getSingleResult())
        .intValue();
  }
}
