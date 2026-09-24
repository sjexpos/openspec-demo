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
import io.awspring.cloud.sqs.listener.acknowledgement.AcknowledgementResultCallback;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Logs batch acknowledgement outcomes. Only counts and the exception type are logged: never
 * payloads, keys, URLs, queue URLs with account IDs, or exception messages (which may embed key
 * material). The generic type must match the container ({@code <S3Event>}); a raw-type callback
 * compiles but never fires.
 */
@Slf4j
@Component
public class SqsAcknowledgementLoggingCallback implements AcknowledgementResultCallback<S3Event> {

  @Override
  public void onSuccess(Collection<Message<S3Event>> messages) {
    log.debug("Acknowledged {} asset-event message(s)", messages.size());
  }

  @Override
  public void onFailure(Collection<Message<S3Event>> messages, Throwable throwable) {
    log.error(
        "Failed to acknowledge {} asset-event message(s): {}",
        messages.size(),
        throwable.getClass().getName());
  }
}
