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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Slf4j
@Configuration
@EnableConfigurationProperties(AwsS3Properties.class)
@RequiredArgsConstructor
public class S3Config {

  private final AwsS3Properties properties;

  @Bean
  public S3Client s3Client() {
    S3ClientBuilder builder =
        S3Client.builder()
            .region(Region.of(this.properties.region()))
            .credentialsProvider(DefaultCredentialsProvider.create());
    if (this.properties.hasEndpointOverride()) {
      log.info("Overriding S3 endpoint with {}", this.properties.s3().endpoint());
      builder
          .endpointOverride(this.properties.s3().endpoint())
          .forcePathStyle(this.properties.s3().pathStyleAccess());
    }
    return builder.build();
  }

  @Bean
  public S3Presigner s3Presigner() {
    S3Presigner.Builder builder =
        S3Presigner.builder()
            .region(Region.of(this.properties.region()))
            .credentialsProvider(DefaultCredentialsProvider.create());
    if (this.properties.hasEndpointOverride()) {
      builder
          .endpointOverride(this.properties.s3().endpoint())
          .serviceConfiguration(
              S3Configuration.builder()
                  .pathStyleAccessEnabled(this.properties.s3().pathStyleAccess())
                  .build());
    }
    return builder.build();
  }
}
