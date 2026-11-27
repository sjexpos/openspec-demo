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

package com.example.demo.application.services;

import com.example.demo.application.exceptions.NotFoundException;
import com.example.demo.domain.models.brand.Brand;
import java.util.Optional;

public interface BrandService {

  Iterable<Brand> findAll();

  Brand create(
      String name,
      String description,
      String email,
      String stateLicense,
      String brandTypeName,
      String logoImageUrl,
      String instagramUrl,
      String twitterUrl,
      String facebookUrl,
      String websiteUrl,
      Integer adminId,
      Boolean enabled);

  Optional<Brand> getById(Long id);

  Brand update(
      Long id,
      String name,
      String description,
      String email,
      String stateLicense,
      String brandTypeName,
      String logoImageUrl,
      String instagramUrl,
      String twitterUrl,
      String facebookUrl,
      String websiteUrl,
      Integer adminId,
      Boolean enabled)
      throws NotFoundException;

  /**
   * Soft-deletes the brand with the given identifier.
   *
   * <p>Resolves the brand first (a missing or already soft-deleted brand answers {@code
   * NotFoundException}, never a conflict), then rejects the delete with {@code ConflictException}
   * when at least one live product still references the brand, and finally marks {@code deletedAt}
   * and saves.
   *
   * @param id the brand identifier
   * @return the soft-deleted brand
   * @throws NotFoundException when no live brand exists with the given identifier
   * @throws com.example.demo.application.exceptions.ConflictException when a live product still
   *     references the brand
   */
  Brand deleteById(Long id);
}
