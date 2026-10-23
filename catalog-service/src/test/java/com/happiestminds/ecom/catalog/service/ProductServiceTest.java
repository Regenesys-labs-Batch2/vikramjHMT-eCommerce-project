package com.happiestminds.ecom.catalog.service;

import com.happiestminds.ecom.catalog.api.dto.PriceDto;
import com.happiestminds.ecom.catalog.api.dto.ProductRequest;
import com.happiestminds.ecom.catalog.repository.CategoryRepository;
import com.happiestminds.ecom.catalog.repository.ProductPriceRepository;
import com.happiestminds.ecom.catalog.repository.ProductRepository;
import com.happiestminds.ecom.catalog.service.exception.BadRequestException;
import com.happiestminds.ecom.catalog.service.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductRepository products;
    @Mock
    CategoryRepository categories;
    @Mock
    ProductPriceRepository prices;

    ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(products, categories, prices);
    }

    @Test
    @DisplayName("AC-CAT-01 R-CAT-02 rejects zero, negative, 3-decimal and non-INR prices")
    void shouldRejectInvalidPrices() {
        for (PriceDto price : new PriceDto[]{
                new PriceDto(BigDecimal.ZERO, "INR"),
                new PriceDto(new BigDecimal("-1.00"), "INR"),
                new PriceDto(new BigDecimal("10.123"), "INR"),
                new PriceDto(new BigDecimal("10.00"), "USD")}) {
            assertThatThrownBy(() -> ProductService.validatePrice(price))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("code").isEqualTo("INVALID_PRICE");
        }
    }

    @Test
    @DisplayName("R-CAT-02 accepts a positive price with at most 2 decimals and normalises the scale")
    void shouldAcceptValidPrice() {
        assertThat(ProductService.validatePrice(new PriceDto(new BigDecimal("899.5"), "INR")))
                .isEqualByComparingTo("899.50")
                .extracting(BigDecimal::scale).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-CAT-02 R-CAT-01 rejects a duplicate SKU")
    void shouldRejectDuplicateSku_whenSkuExists() {
        when(products.existsBySku("LAP-009")).thenReturn(true);
        ProductRequest request = new ProductRequest("lap-009", "Laptop", null, "cat-1",
                new PriceDto(new BigDecimal("100.00"), "INR"), true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SKU_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("R-CAT-04 rejects unsupported sort fields")
    void shouldRejectUnknownSortField() {
        assertThatThrownBy(() -> service.search(null, null, null, null, null, 0, 20, "passwordHash,asc"))
                .isInstanceOf(BadRequestException.class);
    }
}
