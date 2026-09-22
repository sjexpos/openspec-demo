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

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Blob types supported by the blob storage port. Each type owns a distinct key prefix within the
 * single configured bucket. Prefixes are the single source of truth for key construction and
 * canonical-key validation.
 */
public enum BlobType {
  BRAND_IMAGE("brands/images/"),
  BRAND_VIDEO("brands/videos/"),
  STRAIN_IMAGE("strains/images/"),
  STRAIN_VIDEO("strains/videos/"),
  PRODUCT_IMAGE("products/images/"),
  PRODUCT_VIDEO("products/videos/");

  private static final Pattern RANDOM_PART = Pattern.compile("[0-9a-f]{32}");

  private final String prefix;

  BlobType(String prefix) {
    this.prefix = prefix;
  }

  /** Key prefix owned by this blob type, always ending with {@code /}. */
  public String prefix() {
    return this.prefix;
  }

  /**
   * True when the key was issued for this blob type: this type's prefix plus a 32-char lowercase
   * hex suffix. Derived from the same prefix constants used to build keys.
   */
  public boolean isKeyOf(String key) {
    if (key == null) {
      return false;
    }
    if (!key.startsWith(this.prefix)) {
      return false;
    }
    return RANDOM_PART.matcher(key.substring(this.prefix.length())).matches();
  }

  /**
   * True when the key was issued by this port: a known prefix plus a 32-char lowercase hex suffix.
   * Derived from the same prefix constants used to build keys, so a new blob type extends
   * validation automatically.
   */
  public static boolean isCanonicalKey(String key) {
    if (key == null) {
      return false;
    }
    return Arrays.stream(values()).anyMatch(type -> type.isKeyOf(key));
  }
}
