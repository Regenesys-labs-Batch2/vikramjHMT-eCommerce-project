package com.happiestminds.ecom.inventory.api;

import com.happiestminds.ecom.inventory.api.dto.AdjustmentRequest;
import com.happiestminds.ecom.inventory.api.dto.ReservationRequest;
import com.happiestminds.ecom.inventory.api.dto.ReservationResponse;
import com.happiestminds.ecom.inventory.api.dto.StockResponse;
import com.happiestminds.ecom.inventory.security.AuthContext;
import com.happiestminds.ecom.inventory.service.ReservationService;
import com.happiestminds.ecom.inventory.service.StockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@Validated
public class InventoryController {

    private static final String INTERNAL = "hasAnyRole('SERVICE','ADMIN')";

    private final StockService stockService;
    private final ReservationService reservationService;

    public InventoryController(StockService stockService, ReservationService reservationService) {
        this.stockService = stockService;
        this.reservationService = reservationService;
    }

    @GetMapping("/{productId}")
    public StockResponse stock(@PathVariable String productId) {
        return stockService.get(productId);
    }

    @PostMapping("/{productId}/adjustments")
    @PreAuthorize(INTERNAL)
    public StockResponse adjust(@PathVariable String productId, @Valid @RequestBody AdjustmentRequest request,
                                Authentication auth) {
        return stockService.adjust(productId, request, AuthContext.subject(auth));
    }

    @PostMapping("/reservations")
    @PreAuthorize(INTERNAL)
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request) {
        ReservationService.Result result = reservationService.reserve(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.body());
    }

    @PostMapping("/reservations/{id}/confirm")
    @PreAuthorize(INTERNAL)
    public ReservationResponse confirm(@PathVariable String id) {
        return reservationService.confirm(id);
    }

    @PostMapping("/reservations/{id}/release")
    @PreAuthorize(INTERNAL)
    public ReservationResponse release(@PathVariable String id) {
        return reservationService.release(id);
    }

    @GetMapping("/reservations/{id}")
    public ReservationResponse reservation(@PathVariable String id) {
        return reservationService.get(id);
    }
}
