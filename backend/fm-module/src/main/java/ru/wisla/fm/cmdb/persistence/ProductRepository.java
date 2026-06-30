package ru.wisla.fm.cmdb.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wisla.fm.cmdb.domain.ProductEntity;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

    boolean existsByCode(String code);
}
