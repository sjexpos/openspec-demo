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

package com.example.demo.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Gap-only SQS properties: carries exactly what the Spring Cloud AWS library does not model (queue
 * name, ack batching, client timeout). Listener tuning, observation and region/endpoint/credentials
 * come from the library {@code SqsProperties} ({@code spring.cloud.aws.sqs.*}) and are never
 * re-declared here.
 */
@ConfigurationProperties(prefix = "aws.sqs")
@Validated
public record AwsSqsProperties(
    @NotBlank String assetsEventsQueue,
    @NotNull Duration acknowledgementInterval,
    @Positive int acknowledgementThreshold,
    @NotNull Duration apiCallTimeout) {}
