package com.happiestminds.ecom.inventory.service;

import com.happiestminds.ecom.inventory.api.dto.AdjustmentRequest;
import com.happiestminds.ecom.inventory.api.dto.StockResponse;
import com.happiestminds.ecom.inventory.domain.InventoryAdjustment;
import com.happiestminds.ecom.inventory.domain.InventoryStock;
import com.happiestminds.ecom.inventory.repository.InventoryAdjustmentRepository;
import com.happiestminds.ecom.inventory.repository.InventoryStockRepository;
import com.happiestminds.ecom.inventory.service.exception.ConflictException;
import com.happiestminds.ecom.inventory.service.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final InventoryStockRepository stocks;
    private final InventoryAdjustmentRepository adjustments;

    public StockService(InventoryStockRepository stocks, InventoryAdjustmentRepository adjustments) {
        this.stocks = stocks;
        this.adjustments = adjustments;
    }

    @Transactional(readOnly = true)
    public StockResponse get(String productId) {
        return StockResponse.from(stocks.findById(productId)
                .orElseThrow(() -> new NotFoundException("STOCK_NOT_FOUND", "No stock record for product")));
    }

    @Transactional
    public StockResponse adjust(String productId, AdjustmentRequest request, String actor) {
        String reference = request.reference() == null || request.reference().isBlank() ? null : request.reference();
        if (reference != null && adjustments.findByProductIdAndReference(productId, reference).isPresent()) {
            return get(productId);
        }
        int delta = request.delta();
        if (stocks.existsById(productId)) {
            if (stocks.adjust(productId, delta, Instant.now()) == 0) {
                throw negative();
            }
        } else {
            if (delta < 0) {
                throw negative();
            }
            stocks.save(InventoryStock.of(productId, delta, 0));
        }
        adjustments.save(new InventoryAdjustment(productId, delta, request.reason(), actor, reference));
        log.info("Stock adjusted productId={} delta={} actor={}", productId, delta, actor);
        return get(productId);
    }

    private static ConflictException negative() {
        return new ConflictException("NEGATIVE_STOCK", "Adjustment would make available stock negative");
    }
}
