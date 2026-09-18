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

package com.example.demo.domain.models.strain;

import com.example.demo.domain.models.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

// Minimal, read-only-in-production mapping (D5): only the strains table's NOT NULL columns are
// mapped, plus id and soft-delete. strain_type_id / seed_company_id are bare Integer columns on
// purpose — StrainType and SeedCompany belong to the future Strain epic and are not modelled here.
@Entity
@Table(name = "strains")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE strains SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Strain extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @EqualsAndHashCode.Include
  private Long id;

  @Column(nullable = false)
  private String ucpc;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String description;

  @Column(name = "strain_type_id", nullable = false)
  private Integer strainTypeId;

  @Column(name = "seed_company_id", nullable = false)
  private Integer seedCompanyId;

  @Column(name = "calming_energizing_value", nullable = false)
  private Integer calmingEnergizingValue;
}
