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

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AssetStatusTests {

  @Test
  void should_containExactlyThreeConstants_when_valuesAreInspectedInOrder() {
    // Arrange: the shared lifecycle contract

    // Act
    AssetStatus[] values = AssetStatus.values();

    // Assert
    assertThat(values)
        .containsExactly(AssetStatus.PENDING, AssetStatus.UPLOADED, AssetStatus.DELETED);
  }

  @Test
  void should_persistLiteralNames_when_nameIsRead() {
    // Arrange: the persistence contract (renaming a state requires a data migration)

    // Act + Assert
    assertThat(AssetStatus.PENDING.name()).isEqualTo("PENDING");
    assertThat(AssetStatus.UPLOADED.name()).isEqualTo("UPLOADED");
    assertThat(AssetStatus.DELETED.name()).isEqualTo("DELETED");
  }

  static Stream<Arguments> transitionMatrix() {
    return Stream.of(
        // Legal transitions
        Arguments.of(AssetStatus.PENDING, AssetStatus.UPLOADED, true),
        Arguments.of(AssetStatus.PENDING, AssetStatus.DELETED, true),
        Arguments.of(AssetStatus.UPLOADED, AssetStatus.DELETED, true),
        // Illegal transitions
        Arguments.of(AssetStatus.PENDING, AssetStatus.PENDING, false),
        Arguments.of(AssetStatus.UPLOADED, AssetStatus.UPLOADED, false),
        Arguments.of(AssetStatus.DELETED, AssetStatus.DELETED, false),
        Arguments.of(AssetStatus.UPLOADED, AssetStatus.PENDING, false),
        Arguments.of(AssetStatus.DELETED, AssetStatus.PENDING, false),
        Arguments.of(AssetStatus.DELETED, AssetStatus.UPLOADED, false),
        // Null targets
        Arguments.of(AssetStatus.PENDING, null, false),
        Arguments.of(AssetStatus.UPLOADED, null, false),
        Arguments.of(AssetStatus.DELETED, null, false));
  }

  @ParameterizedTest
  @MethodSource("transitionMatrix")
  void should_evaluateTransition_when_sourceAndTargetAreGiven(
      AssetStatus source, AssetStatus target, boolean expected) {
    // Arrange: a source state and a target state from the 3x4 matrix

    // Act
    boolean allowed = source.canTransitionTo(target);

    // Assert
    assertThat(allowed).isEqualTo(expected);
  }

  @Test
  void should_rejectSelfTransition_when_eachStateTargetsItself() {
    // Arrange: every lifecycle state

    // Act + Assert
    for (AssetStatus status : AssetStatus.values()) {
      assertThat(status.canTransitionTo(status)).isFalse();
    }
  }

  @Test
  void should_reportTerminalOnlyForDeleted_when_isTerminalIsChecked() {
    // Arrange: all lifecycle states

    // Act + Assert
    assertThat(AssetStatus.PENDING.isTerminal()).isFalse();
    assertThat(AssetStatus.UPLOADED.isTerminal()).isFalse();
    assertThat(AssetStatus.DELETED.isTerminal()).isTrue();
  }

  @Test
  void should_reportVisibleOnlyForUploaded_when_isVisibleIsChecked() {
    // Arrange: all lifecycle states

    // Act + Assert
    assertThat(AssetStatus.PENDING.isVisible()).isFalse();
    assertThat(AssetStatus.UPLOADED.isVisible()).isTrue();
    assertThat(AssetStatus.DELETED.isVisible()).isFalse();
  }
}
