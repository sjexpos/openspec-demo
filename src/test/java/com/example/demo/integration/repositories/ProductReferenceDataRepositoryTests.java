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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.domain.models.product.Category;
import com.example.demo.domain.models.product.Collection;
import com.example.demo.domain.models.product.Subcategory;
import com.example.demo.domain.models.product.Unit;
import com.example.demo.domain.repositories.CategoryRepository;
import com.example.demo.domain.repositories.CollectionRepository;
import com.example.demo.domain.repositories.SubcategoryRepository;
import com.example.demo.domain.repositories.UnitRepository;
import java.util.Optional;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProductReferenceDataRepositoryTests extends RepositoryTest {

  @Autowired private CollectionRepository collectionRepository;

  @Autowired private CategoryRepository categoryRepository;

  @Autowired private SubcategoryRepository subcategoryRepository;

  @Autowired private UnitRepository unitRepository;

  @Test
  void should_returnItWithName_when_collectionExists() {
    // Given
    Collection collection = new Collection();
    collection.setName("Flowers");
    Collection persisted = this.entityManager.persistAndFlush(collection);

    // When
    Optional<Collection> result = collectionRepository.findById(persisted.getId());

    // Then
    assertTrue(result.isPresent());
    assertEquals("Flowers", result.orElseThrow().getName());
  }

  @Test
  void should_returnAllScalarFields_when_categoryExists() {
    // Given
    Category category = new Category();
    category.setName("Edibles");
    category.setImageUrl("https://example.com/category.png");
    category.setTagIcon("icon");
    category.setTagColor("#000000");
    Category persisted = this.entityManager.persistAndFlush(category);

    // When
    Optional<Category> result = categoryRepository.findById(persisted.getId());

    // Then
    assertTrue(result.isPresent());
    Category found = result.orElseThrow();
    assertEquals("Edibles", found.getName());
    assertEquals("https://example.com/category.png", found.getImageUrl());
    assertEquals("icon", found.getTagIcon());
    assertEquals("#000000", found.getTagColor());
  }

  @Test
  void should_returnParentIdWithoutInitialisingProxy_when_subcategoryPersisted() {
    // Given
    Category category = new Category();
    category.setName("Concentrates");
    category.setImageUrl("https://example.com/category.png");
    category.setTagIcon("icon");
    category.setTagColor("#000000");
    Category persistedCategory = this.entityManager.persistAndFlush(category);

    Subcategory subcategory = new Subcategory();
    subcategory.setName("Wax");
    subcategory.setCategory(persistedCategory);
    subcategory.setImageUrl("https://example.com/subcategory.png");
    subcategory.setTagIcon("icon");
    subcategory.setTagColor("#111111");
    Subcategory persistedSubcategory = this.entityManager.persistAndFlush(subcategory);
    this.entityManager.clear();

    // When
    Subcategory reloaded =
        subcategoryRepository.findById(persistedSubcategory.getId()).orElseThrow();

    // Then
    assertEquals(persistedCategory.getId(), reloaded.getCategory().getId());
    assertFalse(Hibernate.isInitialized(reloaded.getCategory()));
  }

  @Test
  void should_returnEachWithName_when_twoUnitsPersisted() {
    // Given
    Unit gram = new Unit();
    gram.setName("gram");
    Unit persistedGram = this.entityManager.persistAndFlush(gram);

    Unit unit = new Unit();
    unit.setName("unit");
    Unit persistedUnit = this.entityManager.persistAndFlush(unit);

    // When
    Optional<Unit> foundGram = unitRepository.findById(persistedGram.getId());
    Optional<Unit> foundUnit = unitRepository.findById(persistedUnit.getId());

    // Then
    assertTrue(foundGram.isPresent());
    assertEquals("gram", foundGram.orElseThrow().getName());
    assertTrue(foundUnit.isPresent());
    assertEquals("unit", foundUnit.orElseThrow().getName());
  }
}
