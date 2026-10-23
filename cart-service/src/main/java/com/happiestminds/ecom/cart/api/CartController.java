package com.happiestminds.ecom.cart.api;

import com.happiestminds.ecom.cart.api.dto.AddItemRequest;
import com.happiestminds.ecom.cart.api.dto.ApplyDiscountRequest;
import com.happiestminds.ecom.cart.api.dto.CartResponse;
import com.happiestminds.ecom.cart.api.dto.CheckoutSummaryRequest;
import com.happiestminds.ecom.cart.api.dto.CheckoutSummaryResponse;
import com.happiestminds.ecom.cart.api.dto.UpdateItemRequest;
import com.happiestminds.ecom.cart.security.AuthContext;
import com.happiestminds.ecom.cart.service.CartService;
import com.happiestminds.ecom.cart.service.exception.BadRequestException;
import com.happiestminds.ecom.cart.service.exception.ForbiddenException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/carts")
@Validated
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/current")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CartResponse current(Authentication auth) {
        return cartService.current(AuthContext.subject(auth));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public CartResponse addItem(Authentication auth, @Valid @RequestBody AddItemRequest request) {
        return cartService.addItem(AuthContext.subject(auth), request);
    }

    @PutMapping("/items/{itemId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CartResponse updateItem(Authentication auth, @PathVariable String itemId,
                                   @Valid @RequestBody UpdateItemRequest request) {
        return cartService.updateItem(AuthContext.subject(auth), itemId, request.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CUSTOMER')")
    public void removeItem(Authentication auth, @PathVariable String itemId) {
        cartService.removeItem(AuthContext.subject(auth), itemId);
    }

    @PostMapping("/discount")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CartResponse applyDiscount(Authentication auth, @Valid @RequestBody ApplyDiscountRequest request) {
        return cartService.applyDiscount(AuthContext.subject(auth), request.code());
    }

    @PostMapping("/checkout-summary")
    @PreAuthorize("hasAnyRole('CUSTOMER','SERVICE')")
    public CheckoutSummaryResponse checkoutSummary(Authentication auth,
                                                   @RequestBody(required = false) CheckoutSummaryRequest request) {
        String caller = AuthContext.subject(auth);
        String requested = request == null ? null : request.customerId();
        if (requested == null || requested.isBlank()) {
            if (AuthContext.hasRole(auth, "SERVICE") && !AuthContext.hasRole(auth, "CUSTOMER")) {
                throw new BadRequestException("VALIDATION_ERROR", "customerId is required for service callers");
            }
            return cartService.checkoutSummary(caller);
        }
        if (!requested.equals(caller) && !AuthContext.isAdminOrService(auth)) {
            throw new ForbiddenException("You can only summarise your own cart");
        }
        return cartService.checkoutSummary(requested);
    }

    @PostMapping("/{cartId}/checked-out")
    @PreAuthorize("hasRole('SERVICE')")
    public void checkedOut(@PathVariable String cartId) {
        cartService.markCheckedOut(cartId);
    }
}
