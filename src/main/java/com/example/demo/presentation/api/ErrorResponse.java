package com.example.demo.presentation.api;

import java.util.List;

public record ErrorResponse(
        String timestamp,
        int status,
        String path,
        List<FieldError> errors
) {
}
