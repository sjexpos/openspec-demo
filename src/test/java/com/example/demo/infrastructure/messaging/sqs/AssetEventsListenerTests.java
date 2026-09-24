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

package com.example.demo.infrastructure.messaging.sqs;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the asset-events listener seam: receiving an event completes without business
 * handling or database access (KAN-13 owns the confirmation). No network involved.
 */
class AssetEventsListenerTests {

  @Test
  void should_completeWithoutThrow_when_listenerReceivesEvent() {
    // Arrange
    AssetEventsListener listener = new AssetEventsListener();
    S3Event event = new S3Event(List.of());

    // Act + Assert
    assertThatCode(() -> listener.onAssetEvent(event)).doesNotThrowAnyException();
  }
}
