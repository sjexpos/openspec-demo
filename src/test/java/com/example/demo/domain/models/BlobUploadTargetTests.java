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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class BlobUploadTargetTests {

  @Test
  void should_exposeKeyUrlMethodAndExpiresAt_when_targetIsCreated() throws Exception {
    // Arrange
    String key = "products/images/9f2a4c1ed3b74e8fa1c6b0d2e5f7a913";
    URI url = new URI("http://localhost:4566/develop-assets/" + key);
    Instant expiresAt = Instant.now().plusSeconds(900);

    // Act
    BlobUploadTarget target = new BlobUploadTarget(key, url, HttpMethod.PUT, expiresAt);

    // Assert
    assertThat(target.key()).isEqualTo(key);
    assertThat(target.url()).isEqualTo(url);
    assertThat(target.method()).isEqualTo(HttpMethod.PUT);
    assertThat(target.expiresAt()).isEqualTo(expiresAt);
  }
}
