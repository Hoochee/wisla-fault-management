package ru.wisla.fm.health.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/health")
public class ProductHealthController {

    private final ProductHealthService productHealthService;

    public ProductHealthController(ProductHealthService productHealthService) {
        this.productHealthService = productHealthService;
    }

    @GetMapping("/products")
    public List<ProductHealthDto> listProducts(
            @RequestParam(required = false) String tenant,
            @RequestParam(required = false) String site,
            @RequestParam(required = false) String tag
    ) {
        return productHealthService.listProducts(tenant, site, tag);
    }

    @GetMapping("/products/{id}")
    public ProductHealthDetailDto getProduct(@PathVariable UUID id) {
        return productHealthService.getProduct(id);
    }
}
