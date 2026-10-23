package com.happiestminds.ecom.cart.api;

import com.happiestminds.ecom.cart.api.dto.DiscountRequest;
import com.happiestminds.ecom.cart.api.dto.DiscountResponse;
import com.happiestminds.ecom.cart.service.DiscountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/discounts")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class DiscountController {

    private final DiscountService discountService;

    public DiscountController(DiscountService discountService) {
        this.discountService = discountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiscountResponse create(@Valid @RequestBody DiscountRequest request) {
        return discountService.create(request);
    }

    @GetMapping
    public List<DiscountResponse> list() {
        return discountService.list();
    }
}
