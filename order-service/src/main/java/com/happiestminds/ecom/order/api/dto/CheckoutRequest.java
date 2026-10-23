package com.happiestminds.ecom.order.api.dto;

import jakarta.validation.constraints.Pattern;

public record CheckoutRequest(
        @Pattern(regexp = "SUCCESS|DECLINED|TIMEOUT", message = "must be SUCCESS, DECLINED or TIMEOUT")
        String simulatedPaymentOutcome) {
}
