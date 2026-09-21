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

package com.example.demo.domain.models;

import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpMethod;

/**
 * Pre-authorised upload target issued by the {@code BlobStorage} port. The caller uploads binary
 * content directly to {@code url} with {@code method} before {@code expiresAt}, without holding
 * store credentials.
 */
public record BlobUploadTarget(String key, URI url, HttpMethod method, Instant expiresAt) {}
