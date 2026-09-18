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

package com.example.demo.application.services.model;

import lombok.Builder;

// Application-layer command for ProductService.create (D3). An immutable record with the 17
// request fields is preferred over positional parameters (the Brand/Dispensary precedent):
// see design.md D3 for the rationale. This type intentionally does not depend on
// jakarta.validation or io.swagger, keeping the presentation DTO out of the application layer.
@Builder
public record CreateProductCommand(
    String ocpc,
    String title,
    String description,
    Long collectionId,
    Long categoryId,
    Long subcategoryId,
    Long brandId,
    Long strainId,
    Integer formatValue,
    Long formatUnitId,
    Integer contentValue,
    Long contentUnitId,
    Boolean isCoreProduct,
    Boolean approved,
    Integer thc,
    Integer cbd,
    Boolean enabled) {}
