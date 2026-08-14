package ru.wisla.fm.health.application.port.in;

import ru.wisla.fm.health.domain.ProductHealthDetail;
import ru.wisla.fm.health.domain.ProductHealthView;

import java.util.List;
import java.util.UUID;

public interface GetProductHealthUseCase {

    List<ProductHealthView> list(String tenant, String site, String tag);

    ProductHealthDetail get(UUID productId);
}
