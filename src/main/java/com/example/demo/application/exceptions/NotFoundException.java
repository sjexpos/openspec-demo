package com.example.demo.application.exceptions;

public class NotFoundException extends DomainException {
  public NotFoundException(String message) {
    super(message);
  }
}
