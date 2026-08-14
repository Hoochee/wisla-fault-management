package ru.wisla.fm.health.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.cmdb.persistence.ProductRepository;
import ru.wisla.fm.health.application.port.out.ProductAggregateWritePort;

import java.util.UUID;

@Component
public class ProductAggregateWriteAdapter implements ProductAggregateWritePort {

    private final ProductRepository productRepository;

    public ProductAggregateWriteAdapter(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public void updateHealthFields(UUID productId, String maxSeverity, int activeEventCount) {
        productRepository.findById(productId).ifPresent(product -> {
            product.setMaxSeverity(maxSeverity);
            product.setActiveEventCount(activeEventCount);
            productRepository.save(product);
        });
    }
}
