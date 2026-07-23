package com.example.demo.integration.repositories;

import java.util.Optional;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@TestConfiguration
@EnableJpaRepositories(basePackages = "com.example.demo.domain.repositories")
@EntityScan("com.example.demo.domain.models")
@EnableTransactionManagement
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@ComponentScan
class RepositoryTestConfig {
  public static final String AUDITOR_NAME = "jpa_tests";

  @Bean
  AuditorAware<String> auditorProvider() {
    return new AuditorAware<String>() {

      @Override
      public Optional<String> getCurrentAuditor() {
        return Optional.of(AUDITOR_NAME);
      }
    };
  }

}
