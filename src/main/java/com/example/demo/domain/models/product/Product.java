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

package com.example.demo.domain.models.product;

import com.example.demo.domain.models.BaseEntity;
import com.example.demo.domain.models.brand.Brand;
import com.example.demo.domain.models.strain.Strain;
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
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

// Aggregate root (D2/D14). All seven references are object associations, deliberately including
// the two references to other aggregates (Brand, Strain) — see design.md D2 for the rationale and
// the structural guard rails: no CascadeType, no orphanRemoval, every association LAZY, no
// inverse collections on Brand/Strain/lookups, and the service never calls a setter on a
// resolved reference.
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@SQLDelete(sql = "UPDATE products SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Product extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @EqualsAndHashCode.Include
  private Long id;

  @Column(nullable = false)
  private String ocpc;

  @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "collection_id", nullable = false)
  private Collection collection;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id", nullable = false)
  private Category category;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subcategory_id", nullable = false)
  private Subcategory subcategory;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "brand_id", nullable = false)
  private Brand brand;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "strain_id", nullable = false)
  private Strain strain;

  @Column(name = "format_value", nullable = false)
  private Integer formatValue;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "format_unit_id", nullable = false)
  private Unit formatUnit;

  @Column(name = "content_value", nullable = false)
  private Integer contentValue;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "content_unit_id", nullable = false)
  private Unit contentUnit;

  @Column(name = "is_core_product")
  private Boolean isCoreProduct;

  private Boolean approved;

  private Integer thc;

  private Integer cbd;

  private Boolean enabled;
}
