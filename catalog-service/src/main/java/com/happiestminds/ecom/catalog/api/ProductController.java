package com.happiestminds.ecom.catalog.api;

import com.happiestminds.ecom.catalog.api.dto.PageResponse;
import com.happiestminds.ecom.catalog.api.dto.ProductRequest;
import com.happiestminds.ecom.catalog.api.dto.ProductResponse;
import com.happiestminds.ecom.catalog.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/products")
@Validated
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse update(@PathVariable String id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @GetMapping
    public PageResponse<ProductResponse> list(@RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size,
                                              @RequestParam(required = false) String sort) {
        return productService.search(null, null, null, null, null, page, size, sort);
    }

    @GetMapping("/search")
    public PageResponse<ProductResponse> search(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) String categoryId,
                                                @RequestParam(required = false) BigDecimal minPrice,
                                                @RequestParam(required = false) BigDecimal maxPrice,
                                                @RequestParam(required = false) Boolean active,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size,
                                                @RequestParam(required = false) String sort) {
        return productService.search(q, categoryId, active, minPrice, maxPrice, page, size, sort);
    }

    @GetMapping("/by-sku/{sku}")
    public ProductResponse bySku(@PathVariable String sku) {
        return productService.getBySku(sku);
    }

    @GetMapping("/{id}")
    public ProductResponse byId(@PathVariable String id) {
        return productService.get(id);
    }
}
