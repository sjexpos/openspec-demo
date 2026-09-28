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

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.serialization.events.LambdaEventSerializers;
import io.awspring.cloud.sqs.support.converter.SqsMessagingMessageConverter;
import java.nio.charset.StandardCharsets;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.MimeType;

/**
 * Converts SQS messages carrying S3 event notifications into {@code S3Event} payloads. The payload
 * mapping uses the canonical Lambda serialization (the same code path as the Lambda runtime):
 * plain Jackson mappers cannot bind {@code S3Event} (capitalized {@code Records} envelope, no
 * default constructor on the record type). A poison body surfaces as a {@code
 * MessageConversionException} so {@code ON_SUCCESS} withholds the acknowledgement (redrive to DLQ
 * after {@code maxReceiveCount}); it is never swallowed.
 */
public class S3EventMessageConverter extends SqsMessagingMessageConverter {

  public S3EventMessageConverter() {
    setPayloadMessageConverter(new S3EventPayloadConverter());
  }

  static class S3EventPayloadConverter extends AbstractMessageConverter {

    S3EventPayloadConverter() {
      super(new MimeType("application", "json"));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
      return S3Event.class.isAssignableFrom(clazz);
    }

    @Override
    protected Object convertFromInternal(Message<?> message, Class<?> targetClass, Object hint) {
      Object payload = message.getPayload();
      String json =
          payload instanceof String text
              ? text
              : new String((byte[]) payload, StandardCharsets.UTF_8);
      try {
        return LambdaEventSerializers.serializerFor(S3Event.class, getClass().getClassLoader())
            .fromJson(json);
      } catch (RuntimeException ex) {
        throw new MessageConversionException(message, "Cannot convert SQS body to S3Event", ex);
      }
    }
  }
}
