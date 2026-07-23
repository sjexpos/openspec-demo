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
import com.example.demo.domain.models.dispensary.Dispensary;
import java.math.BigDecimal;
import java.util.Optional;

public interface DispensaryService {

  Iterable<Dispensary> findAll();

  Dispensary create(
      String name,
      String logoImageURL,
      String description,
      String license,
      String licenseStatus,
      String phone,
      String email,
      String instagramURL,
      String twitterURL,
      String facebookURL,
      String websiteURL,
      String address,
      BigDecimal commission,
      Integer adminId,
      Boolean enabled);

  Optional<Dispensary> getById(Long id);

  Optional<Dispensary> deleteById(Long id) throws NotFoundException;
}
