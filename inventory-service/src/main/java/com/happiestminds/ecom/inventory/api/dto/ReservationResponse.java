package com.happiestminds.ecom.inventory.api.dto;

import com.happiestminds.ecom.inventory.domain.StockReservation;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record ReservationResponse(String reservationId, String orderRef, String status, Instant expiresAt,
                                  List<ReservationItem> items) {

    // One order owns one row per product; the row with the smallest id is the order's reservation id.
    public static ReservationResponse from(List<StockReservation> rows) {
        StockReservation representative = rows.stream().min(Comparator.comparing(StockReservation::getId)).orElseThrow();
        List<ReservationItem> items = rows.stream()
                .sorted(Comparator.comparing(StockReservation::getProductId))
                .map(r -> new ReservationItem(r.getProductId(), r.getQuantity()))
                .toList();
        return new ReservationResponse(representative.getId(), representative.getOrderRef(),
                representative.getStatus().name(), representative.getExpiresAt(), items);
    }
}
