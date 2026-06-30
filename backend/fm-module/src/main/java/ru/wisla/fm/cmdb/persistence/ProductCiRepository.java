package ru.wisla.fm.cmdb.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wisla.fm.cmdb.domain.ProductCiEntity;
import ru.wisla.fm.cmdb.domain.ProductCiId;

import java.util.List;
import java.util.UUID;

public interface ProductCiRepository extends JpaRepository<ProductCiEntity, ProductCiId> {

    @Query("SELECT pc.id.ciId FROM ProductCiEntity pc WHERE pc.id.productId = :productId")
    List<UUID> findCiIdsByProductId(@Param("productId") UUID productId);

    @Query("SELECT pc.id.productId FROM ProductCiEntity pc WHERE pc.id.ciId = :ciId")
    List<UUID> findProductIdsByCiId(@Param("ciId") UUID ciId);

    void deleteByIdProductId(UUID productId);

    void deleteByIdCiId(UUID ciId);
}
