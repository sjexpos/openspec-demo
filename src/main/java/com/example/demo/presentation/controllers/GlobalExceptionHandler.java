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

package com.example.demo.presentation.controllers;

import com.example.demo.application.exceptions.NotFoundException;
import com.example.demo.presentation.api.ErrorResponse;
import com.example.demo.presentation.api.FieldError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(
      NotFoundException ex, HttpServletRequest request) {
    List<FieldError> fieldErrors = List.of(new FieldError("general", ex.getMessage()));
    return build(HttpStatus.NOT_FOUND, request, fieldErrors);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    List<FieldError> fieldErrors = List.of(new FieldError("data_integrity", ex.getMessage()));
    return build(HttpStatus.CONFLICT, request, fieldErrors);
  }

  @ExceptionHandler(WebExchangeBindException.class)
  public ResponseEntity<ErrorResponse> handleBeanValidation(
      WebExchangeBindException ex, HttpServletRequest request) {
    List<FieldError> errors =
        ex.getFieldErrors().stream()
            .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
            .toList();
    return build(HttpStatus.BAD_REQUEST, request, errors);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<FieldError> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
    return build(HttpStatus.BAD_REQUEST, request, fieldErrors);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(
      NoResourceFoundException ex, HttpServletRequest request) {
    List<FieldError> fieldErrors = List.of(new FieldError("general", "Resource not found"));
    return build(HttpStatus.NOT_FOUND, request, fieldErrors);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
    log.error("An unexpected error occurred", ex);
    List<FieldError> fieldErrors =
        List.of(new FieldError("general", "An unexpected error occurred"));
    return build(HttpStatus.INTERNAL_SERVER_ERROR, request, fieldErrors);
  }

  private ResponseEntity<ErrorResponse> build(
      HttpStatus status, HttpServletRequest request, List<FieldError> errors) {
    return ResponseEntity.status(status).body(buildError(status, request.getRequestURI(), errors));
  }

  private ErrorResponse buildError(HttpStatus status, String path, List<FieldError> errors) {
    return new ErrorResponse(Instant.now().toString(), status.value(), path, errors);
  }
}
