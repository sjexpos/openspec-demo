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

package com.example.demo.application.services.impl;

import com.example.demo.application.exceptions.ConflictException;
import com.example.demo.application.exceptions.NotFoundException;
import com.example.demo.application.services.ProductService;
import com.example.demo.application.services.model.CreateProductCommand;
import com.example.demo.domain.models.brand.Brand;
import com.example.demo.domain.models.product.Category;
import com.example.demo.domain.models.product.Collection;
import com.example.demo.domain.models.product.Product;
import com.example.demo.domain.models.product.Subcategory;
import com.example.demo.domain.models.product.Unit;
import com.example.demo.domain.models.strain.Strain;
import com.example.demo.domain.repositories.BrandRepository;
import com.example.demo.domain.repositories.CategoryRepository;
import com.example.demo.domain.repositories.CollectionRepository;
import com.example.demo.domain.repositories.ProductRepository;
import com.example.demo.domain.repositories.StrainRepository;
import com.example.demo.domain.repositories.SubcategoryRepository;
import com.example.demo.domain.repositories.UnitRepository;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// The three guards (OCPC uniqueness, reference resolution, taxonomy coherence) run in the fixed
// D1 order, all before any write, inside the single transaction declared on create (D6).
@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

  private final ProductRepository productRepository;
  private final CollectionRepository collectionRepository;
  private final CategoryRepository categoryRepository;
  private final SubcategoryRepository subcategoryRepository;
  private final BrandRepository brandRepository;
  private final StrainRepository strainRepository;
  private final UnitRepository unitRepository;

  public ProductServiceImpl(
      ProductRepository productRepository,
      CollectionRepository collectionRepository,
      CategoryRepository categoryRepository,
      SubcategoryRepository subcategoryRepository,
      BrandRepository brandRepository,
      StrainRepository strainRepository,
      UnitRepository unitRepository) {
    this.productRepository = productRepository;
    this.collectionRepository = collectionRepository;
    this.categoryRepository = categoryRepository;
    this.subcategoryRepository = subcategoryRepository;
    this.brandRepository = brandRepository;
    this.strainRepository = strainRepository;
    this.unitRepository = unitRepository;
  }

  @Override
  @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
  public Product create(CreateProductCommand command) {
    // Guard 1 (D1): OCPC uniqueness, cheapest rejection first.
    if (this.productRepository.existsByOcpc(command.ocpc())) {
      log.warn("Rejected product creation: OCPC {} already used by a live product", command.ocpc());
      throw new ConflictException("Product already exists with OCPC: " + command.ocpc());
    }

    Collection collection =
        resolveOrNotFound(this.collectionRepository, command.collectionId(), "Collection");
    Category category =
        resolveOrNotFound(this.categoryRepository, command.categoryId(), "Category");
    Subcategory subcategory =
        resolveOrNotFound(this.subcategoryRepository, command.subcategoryId(), "Subcategory");
    Brand brand = resolveOrNotFound(this.brandRepository, command.brandId(), "Brand");
    Strain strain = resolveOrNotFound(this.strainRepository, command.strainId(), "Strain");
    Unit formatUnit = resolveOrNotFound(this.unitRepository, command.formatUnitId(), "FormatUnit");
    Unit contentUnit =
        resolveOrNotFound(this.unitRepository, command.contentUnitId(), "ContentUnit");

    // Guard 3 (D1): taxonomy coherence, checked last because it needs the loaded subcategory.
    // Reads only the proxy id (no initialisation, zero extra queries per D12).
    if (!Objects.equals(subcategory.getCategory().getId(), category.getId())) {
      log.warn(
          "Rejected product creation: subcategory {} does not belong to category {}",
          subcategory.getId(),
          category.getId());
      throw new ConflictException(
          "Subcategory "
              + subcategory.getId()
              + " does not belong to category "
              + category.getId());
    }

    Product product =
        Product.builder()
            .ocpc(command.ocpc())
            .title(command.title())
            .description(command.description())
            .collection(collection)
            .category(category)
            .subcategory(subcategory)
            .brand(brand)
            .strain(strain)
            .formatValue(command.formatValue())
            .formatUnit(formatUnit)
            .contentValue(command.contentValue())
            .contentUnit(contentUnit)
            .isCoreProduct(command.isCoreProduct())
            .approved(command.approved())
            .thc(command.thc())
            .cbd(command.cbd())
            .enabled(command.enabled())
            .build();

    Product saved = this.productRepository.save(product);
    log.info("Created product with id {}", saved.getId());
    return saved;
  }

  // DRY helper (D4): a single private generic method replaces seven identical orElseThrow
  // chains, one per reference. Kept private to this impl (not promoted to a shared utility)
  // since there is exactly one consumer today and the user-visible error wording stays owned by
  // the service that produces it.
  private <T> T resolveOrNotFound(
      JpaRepository<T, Long> repository, Long id, String referenceName) {
    return repository
        .findById(id)
        .orElseThrow(
            () -> {
              log.warn("Rejected product creation: {} not found with ID: {}", referenceName, id);
              return new NotFoundException(referenceName + " not found with ID: " + id);
            });
  }
}
