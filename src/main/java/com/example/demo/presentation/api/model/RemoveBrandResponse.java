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

package com.example.demo.presentation.api.model;

/**
 * Removal confirmation for a soft-deleted brand.
 *
 * <p>Mirrors {@code RemoveDispensaryResponse}: identity plus core business fields plus the parent
 * lookup-state field ({@code brandType}) plus {@code adminId}. A removal confirmation is a
 * distinct, smaller contract than the read model.
 */
public record RemoveBrandResponse(
    Long id,
    String name,
    String description,
    String stateLicense,
    String brandType,
    Integer adminId) {}
