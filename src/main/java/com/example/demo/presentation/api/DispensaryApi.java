package com.example.demo.presentation.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.example.demo.presentation.api.model.CreateDispensaryRequest;
import com.example.demo.presentation.api.model.CreateDispensaryResponse;
import com.example.demo.presentation.api.model.GetAllDispensariesResponse;
import com.example.demo.presentation.api.model.GetDispensaryResponse;
import com.example.demo.presentation.api.model.RemoveDispensaryResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RequestMapping("/api/dispensaries")
@Tag(name = "Dispensaries", description = "Dispensary management endpoints")
@Validated
public interface DispensaryApi {

    @GetMapping
    @ResponseStatus(value = HttpStatus.OK)
    @Operation(summary = "List all dispensaries", description = "Returns a list of all dispensaries")
    @ApiResponse(responseCode = "200", description = "Dispensaries retrieved successfully")
    DataResponse<List<GetAllDispensariesResponse>> getAll();

    @PostMapping
    @ResponseStatus(value = HttpStatus.CREATED)
    @Operation(summary = "Create a new dispensary", description = "Creates a new dispensary and returns it with the generated ID")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Dispensary created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    DataResponse<CreateDispensaryResponse> create(@Valid @RequestBody CreateDispensaryRequest request);

    @GetMapping("/{id}")
    @ResponseStatus(value = HttpStatus.OK)
    @Operation(summary = "Get dispensary by ID", description = "Returns a single dispensary by their ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispensary found"),
            @ApiResponse(responseCode = "404", description = "Dispensary not found")
    })
    DataResponse<GetDispensaryResponse> getById(@Parameter(description = "Dispensary ID", required = true) @PathVariable Long id);

    @DeleteMapping("/{id}")
    @ResponseStatus(value = HttpStatus.OK)
    @Operation(summary = "Delete a dispensary", description = "Deletes a dispensary by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Dispensary deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Dispensary not found")
    })
    DataResponse<RemoveDispensaryResponse> delete(@Parameter(description = "Dispensary ID", required = true) @PathVariable Long id);

}
