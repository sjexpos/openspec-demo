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

/**
 * Shared lifecycle of any persisted asset reference pointing at a blob in object storage. PENDING
 * means the asset reference was recorded before its blob upload was confirmed. UPLOADED means the
 * upload was confirmed and is the only client-visible state. DELETED means the asset reference is
 * retired while its blob may still exist. Resource-agnostic: every asset resource reuses these
 * states and transition rules without modification.
 */
public enum AssetStatus {
  PENDING,
  UPLOADED,
  DELETED;

  /**
   * True when a transition from this state to the target state is legal. Legal transitions are
   * PENDING to UPLOADED, PENDING to DELETED and UPLOADED to DELETED. Null targets and
   * self-transitions are always rejected.
   */
  public boolean canTransitionTo(AssetStatus target) {
    if (target == null) {
      return false;
    }
    return switch (this) {
      case PENDING -> target == UPLOADED || target == DELETED;
      case UPLOADED -> target == DELETED;
      case DELETED -> false;
    };
  }

  /** True only for DELETED, the terminal state. */
  public boolean isTerminal() {
    return this == DELETED;
  }

  /** True only for UPLOADED, the only client-visible state. */
  public boolean isVisible() {
    return this == UPLOADED;
  }
}
