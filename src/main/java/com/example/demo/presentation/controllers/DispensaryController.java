package com.example.demo.presentation.controllers;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.application.exceptions.NotFoundException;
import com.example.demo.application.services.DispensaryService;
import com.example.demo.domain.models.dispensary.Dispensary;
import com.example.demo.presentation.api.model.CreateDispensaryRequest;
import com.example.demo.presentation.api.model.CreateDispensaryResponse;
import com.example.demo.presentation.api.model.GetAllDispensariesResponse;
import com.example.demo.presentation.api.model.GetDispensaryResponse;
import com.example.demo.presentation.api.model.RemoveDispensaryResponse;
import com.example.demo.presentation.api.DataResponse;
import com.example.demo.presentation.api.DispensaryApi;

@RestController
public class DispensaryController implements DispensaryApi {

    private final DispensaryService dispensaryService;

    public DispensaryController(DispensaryService dispensaryService) {
        this.dispensaryService = dispensaryService;
    }

    @Override
    public DataResponse<List<GetAllDispensariesResponse>> getAll() {

        return StreamSupport.stream(this.dispensaryService.findAll().spliterator(), false)
            .map(dispensary -> new GetAllDispensariesResponse(
                        dispensary.getId(),
                        dispensary.getName(),
                        dispensary.getLicense(),
                        dispensary.getPhone(),
                        dispensary.getEmail(),
                        dispensary.getInstagramUrl(),
                        dispensary.getTwitterUrl(),
                        dispensary.getFacebookUrl(),
                        dispensary.getWebsiteUrl(),
                        dispensary.getCommission().doubleValue(),
                        dispensary.getAdminId(),
                        dispensary.getEnabled(),
                        dispensary.getLicenseStatus().getState(),
                        dispensary.getAddress().getAddress(),
                        "",
                        dispensary.getAddress().getLongitude().doubleValue(),
                        dispensary.getAddress().getLatitude().doubleValue()
                ))
                .collect(Collectors.collectingAndThen(Collectors.toList(), DataResponse::new));
    }

    @Override
    public DataResponse<CreateDispensaryResponse> create(CreateDispensaryRequest request) {

        Dispensary dispensary = this.dispensaryService.create(
                request.getName(),
                request.getLogoImageURL(),
                request.getDescription(),
                request.getLicense(),
                request.getLicenseStatus(),
                request.getPhone(),
                request.getEmail(),
                request.getInstagramURL(),
                request.getTwitterURL(),
                request.getFacebookURL(),
                request.getWebsiteURL(),
                request.getAddress(),
                request.getCommission(),
                request.getAdminId(),
                request.getEnabled()
        );
        return new DataResponse<>(new CreateDispensaryResponse(dispensary.getId(),
                dispensary.getName(),
                dispensary.getLogoImageUrl(),
                dispensary.getDescription(),
                dispensary.getLicense(),
                dispensary.getLicenseStatus().getState(),
                dispensary.getPhone(),
                dispensary.getEmail(),
                dispensary.getInstagramUrl(),
                dispensary.getTwitterUrl(),
                dispensary.getFacebookUrl(),
                dispensary.getWebsiteUrl(),
                Optional.ofNullable(dispensary.getAddress()).map(addr -> addr.getAddress()).orElse(null),
                dispensary.getCommission(),
                dispensary.getAdminId(),
                dispensary.getEnabled()));
    }

    @Override
    public DataResponse<GetDispensaryResponse> getById(Long id) {
        Dispensary dispensary = this.dispensaryService.getById(id)
            .orElseThrow(() -> new NotFoundException("Dispensary not found with ID: " + id));
        return new DataResponse<>(new GetDispensaryResponse(
                dispensary.getId(),
                dispensary.getName(),
                dispensary.getLogoImageUrl(),
                dispensary.getDescription(),
                dispensary.getLicense(),
                dispensary.getLicenseStatus().getState(),
                dispensary.getPhone(),
                dispensary.getEmail(),
                dispensary.getInstagramUrl(),
                dispensary.getTwitterUrl(),
                dispensary.getFacebookUrl(),
                dispensary.getWebsiteUrl(),
                Optional.ofNullable(dispensary.getAddress()).map(addr -> addr.getAddress()).orElse(null),
                dispensary.getCommission(),
                dispensary.getAdminId(),
                dispensary.getEnabled())
            );
    }

    @Override
    public DataResponse<RemoveDispensaryResponse> delete(Long id) {
        Dispensary dispensary = this.dispensaryService.deleteById(id)
            .orElseThrow(() -> new NotFoundException("Dispensary not found with ID: " + id));
        return new DataResponse<>(new RemoveDispensaryResponse(
            dispensary.getId(), 
            dispensary.getName(),
            dispensary.getDescription(),
            dispensary.getLicense(),
            dispensary.getLicenseStatus().getState(),
            Optional.ofNullable(dispensary.getAddress()).map(addr -> addr.getAddress()).orElse(null),
            dispensary.getAdminId()
        ));
    }

}
