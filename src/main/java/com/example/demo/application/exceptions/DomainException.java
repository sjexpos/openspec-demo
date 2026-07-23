package com.example.demo.application.exceptions;

public class DomainException extends RuntimeException {
  public DomainException(String message) {
    super(message);
  }
}
