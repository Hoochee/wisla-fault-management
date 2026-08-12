package ru.wisla.fm.ingestion.adapter.out.persistence;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import ru.wisla.fm.ingestion.application.port.out.RawEventStorePort;
import ru.wisla.fm.ingestion.domain.RawEvent;
import ru.wisla.fm.ingestion.domain.RawEventBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class RawEventPersistenceAdapter implements RawEventStorePort {

    private final RawEventJpaRepository rawEventJpaRepository;
    private final RawEventJpaMapper rawEventJpaMapper;

    public RawEventPersistenceAdapter(RawEventJpaRepository rawEventJpaRepository,
                                      RawEventJpaMapper rawEventJpaMapper) {
        this.rawEventJpaRepository = rawEventJpaRepository;
        this.rawEventJpaMapper = rawEventJpaMapper;
    }

    @Override
    public UUID save(RawEvent rawEvent) {
        return rawEventJpaRepository.save(rawEventJpaMapper.toJpaEntity(rawEvent)).getId();
    }

    @Override
    public RawEventBatch findPage(UUID sourceId, String severity, Boolean processed, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RawEventJpaEntity> result =
                rawEventJpaRepository.findAll(buildSpec(sourceId, severity, processed), pageable);
        return new RawEventBatch(
                result.getContent().stream().map(rawEventJpaMapper::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    private Specification<RawEventJpaEntity> buildSpec(UUID sourceId, String severity, Boolean processed) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sourceId != null) {
                predicates.add(cb.equal(root.get("sourceId"), sourceId));
            }
            if (severity != null && !severity.isBlank()) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (processed != null) {
                predicates.add(cb.equal(root.get("processed"), processed));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
