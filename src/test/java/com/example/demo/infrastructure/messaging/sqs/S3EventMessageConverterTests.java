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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import io.awspring.cloud.sqs.support.converter.MessageConversionContext;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

/**
 * Unit tests for the S3 event message converter: the S3 to SQS JSON body maps to {@code S3Event},
 * and a poison body surfaces as a conversion exception (ack withheld, redrive to DLQ) instead of
 * being swallowed. No network involved.
 */
class S3EventMessageConverterTests {

  private final S3EventMessageConverter converter = new S3EventMessageConverter();

  private final MessageConversionContext s3EventContext = () -> S3Event.class;

  private String fixture() throws Exception {
    try (var stream = getClass().getResourceAsStream("/sqs/s3-notification.json")) {
      assertThat(stream).isNotNull();
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void should_deserialiseS3Event_when_bodyIsS3NotificationJson() throws Exception {
    // Arrange
    software.amazon.awssdk.services.sqs.model.Message sqsMessage =
        software.amazon.awssdk.services.sqs.model.Message.builder()
            .messageId(UUID.randomUUID().toString())
            .body(fixture())
            .build();

    // Act
    Message<?> message = converter.toMessagingMessage(sqsMessage, s3EventContext);

    // Assert
    assertThat(message.getPayload()).isInstanceOf(S3Event.class);
    S3Event event = (S3Event) message.getPayload();
    assertThat(event.getRecords()).hasSize(1);
    assertThat(event.getRecords().get(0).getS3().getBucket().getName()).isEqualTo("develop-assets");
    assertThat(event.getRecords().get(0).getS3().getObject().getKey())
        .isEqualTo("brands%2Fimages%2F0123456789abcdef0123456789abcdef");
  }

  @Test
  void should_throwConversionException_when_bodyIsNotJson() { // Arrange: poison body that can never
    // be an S3 notification
    software.amazon.awssdk.services.sqs.model.Message sqsMessage =
        software.amazon.awssdk.services.sqs.model.Message.builder()
            .messageId(UUID.randomUUID().toString())
            .body("not-json{{{")
            .build();

    // Act + Assert: surfaces as a listener exception, never swallowed
    assertThatThrownBy(() -> converter.toMessagingMessage(sqsMessage, s3EventContext)).isNotNull();
  }

  @Test
  void should_deserialiseS3Event_when_payloadIsByteArray() throws Exception {
    // Arrange: defensive path for non-String payloads
    byte[] body = fixture().getBytes(StandardCharsets.UTF_8);
    org.springframework.messaging.Message<?> message =
        new org.springframework.messaging.support.GenericMessage<>(body);

    // Act
    Object payload =
        new S3EventMessageConverter.S3EventPayloadConverter().fromMessage(message, S3Event.class);

    // Assert
    assertThat(payload).isInstanceOf(S3Event.class);
    S3Event event = (S3Event) payload;
    assertThat(event.getRecords()).hasSize(1);
    assertThat(event.getRecords().get(0).getS3().getBucket().getName()).isEqualTo("develop-assets");
  }
}
