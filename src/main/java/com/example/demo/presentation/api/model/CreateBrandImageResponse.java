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

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Upload target issued for a newly registered brand image. Exactly three {@code String} fields:
 * {@code String} types are load-bearing (the shared {@code ObjectMapper} has no {@code
 * JavaTimeModule}, so {@code Instant} would fail serialization, and {@code HttpMethod} serializes
 * as an object, not {@code "PUT"}). The persisted brand image row is never exposed here.
 */
public record CreateBrandImageResponse(
    @Schema(
            description = "Short-lived, credential-free upload URL (bearer capability)",
            example = "https://example.invalid/brands/images/placeholder")
        String uploadUrl,
    @Schema(description = "HTTP method to use against the upload URL", example = "PUT")
        String uploadMethod,
    @Schema(
            description = "Expiry instant of the upload URL (ISO-8601 UTC)",
            example = "2026-09-22T10:15:30Z")
        String expiresAt) {}
