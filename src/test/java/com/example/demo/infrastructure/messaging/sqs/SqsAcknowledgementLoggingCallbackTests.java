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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

/**
 * Unit tests for the acknowledgement logging callback: counts are logged, payloads and key
 * material never are. No network involved.
 */
class SqsAcknowledgementLoggingCallbackTests {

  private static final String KEY_MATERIAL = "brands/images/secret-key-material";

  private SqsAcknowledgementLoggingCallback callback;

  private ListAppender<ILoggingEvent> appender;

  private Logger logger;

  @BeforeEach
  void setUp() {
    callback = new SqsAcknowledgementLoggingCallback();
    logger = (Logger) LoggerFactory.getLogger(SqsAcknowledgementLoggingCallback.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
  }

  @Test
  void should_logWithoutPayload_when_ackSucceeds() {
    // Arrange
    Message<S3Event> message = new GenericMessage<>(new S3Event(List.of()));

    // Act
    assertThatCode(() -> callback.onSuccess(List.of(message))).doesNotThrowAnyException();

    // Assert: count logged, no payload content
    assertThat(appender.list)
        .anySatisfy(
            event -> {
              assertThat(event.getFormattedMessage()).contains("1");
              assertThat(event.getFormattedMessage()).doesNotContain("Records");
            });
  }

  @Test
  void should_logErrorWithoutPayload_when_ackFails() {
    // Arrange: throwable message embeds key material, as AWS errors sometimes do
    Message<S3Event> message = new GenericMessage<>(new S3Event(List.of()));
    RuntimeException failure = new RuntimeException("Delete failed for " + KEY_MATERIAL);

    // Act
    assertThatCode(() -> callback.onFailure(List.of(message), failure)).doesNotThrowAnyException();

    // Assert: count plus exception type logged, key material never logged
    assertThat(appender.list)
        .anySatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getFormattedMessage()).contains("1");
              assertThat(event.getFormattedMessage()).contains("RuntimeException");
              assertThat(event.getFormattedMessage()).doesNotContain(KEY_MATERIAL);
            });
  }
}
