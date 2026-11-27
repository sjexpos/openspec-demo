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

package com.example.demo.domain.repositories;

import com.example.demo.domain.models.brand.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {

  /**
   * Counts live (non-soft-deleted) products referencing the given brand.
   *
   * <p>Soft-deleted products do not count, so a brand referenced exclusively by deleted products
   * remains deletable. Loads no {@code Product} entity and adds no inverse collection to {@code
   * Brand}; the native query is the anti-corruption seam between the two aggregates.
   *
   * @param brandId the brand identifier to check
   * @return the number of live products referencing the brand
   */
  @Query(
      value =
          "SELECT COUNT(*) FROM products p WHERE p.brand_id = :brandId AND p.deleted_at IS NULL",
      nativeQuery = true)
  long countLiveProductsByBrandId(@Param("brandId") Long brandId);
}
