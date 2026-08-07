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

package com.example.demo.domain.models.dispensary;

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
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "dispensaries")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE dispensaries SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Dispensary extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @EqualsAndHashCode.Include
  private Integer id;

  @Column(nullable = false)
  private String name;

  @Column(name = "logo_image_url")
  private String logoImageUrl;

  @Column(columnDefinition = "TEXT")
  private String description;

  private String license;

  @Column(nullable = false)
  private String phone;

  @Column(nullable = false)
  private String email;

  @Column(name = "instagram_url")
  private String instagramUrl;

  @Column(name = "twitter_url")
  private String twitterUrl;

  @Column(name = "facebook_url")
  private String facebookUrl;

  @Column(name = "website_url")
  private String websiteUrl;

  private BigDecimal commission;

  @Column(name = "admin_id")
  private Integer adminId;

  @Column(nullable = false)
  private Boolean enabled;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "license_status_id")
  private LicenseStatus licenseStatus;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "address_id")
  private Address address;
}
