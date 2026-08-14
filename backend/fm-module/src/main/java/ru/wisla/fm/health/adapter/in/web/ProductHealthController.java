package ru.wisla.fm.health.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.wisla.fm.health.api.ProductHealthDetailDto;
import ru.wisla.fm.health.api.ProductHealthDto;
import ru.wisla.fm.health.api.ProductHealthHistoryDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/health")
public class ProductHealthController {

    private final ProductHealthFacade productHealthFacade;

    public ProductHealthController(ProductHealthFacade productHealthFacade) {
        this.productHealthFacade = productHealthFacade;
    }

    @GetMapping("/products")
    public List<ProductHealthDto> listProducts(
            @RequestParam(required = false) String tenant,
            @RequestParam(required = false) String site,
            @RequestParam(required = false) String tag
    ) {
        return productHealthFacade.listProducts(tenant, site, tag);
    }

    @GetMapping("/products/{id}")
    public ProductHealthDetailDto getProduct(@PathVariable UUID id) {
        return productHealthFacade.getProduct(id);
    }

    @GetMapping("/products/{id}/history")
    public List<ProductHealthHistoryDto> history(
            @PathVariable UUID id,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "15") int bucketMinutes
    ) {
        return productHealthFacade.history(id, from, to, bucketMinutes);
    }
}
