package ru.wisla.fm.processing.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventJpaRepository extends JpaRepository<EventJpaEntity, UUID>, JpaSpecificationExecutor<EventJpaEntity> {

    Page<EventJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<EventJpaEntity> findFirstBySourceIdAndTitleAndCiIdAndStatusIn(
            UUID sourceId, String title, UUID ciId, List<String> statuses
    );

    Optional<EventJpaEntity> findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn(
            UUID sourceId, String title, List<String> statuses
    );

    @Query("SELECT e.severity, COUNT(e) FROM EventJpaEntity e WHERE e.status NOT IN ('closed', 'archived') GROUP BY e.severity")
    List<Object[]> countActiveBySeverity();

    long countByStatusNotIn(List<String> statuses);

    long countBySourceIdAndCiIdAndSeverityAndCreatedAtAfterAndStatusIn(
            UUID sourceId, UUID ciId, String severity, Instant since, List<String> statuses
    );

    long countBySourceIdAndCiIdIsNullAndSeverityAndCreatedAtAfterAndStatusIn(
            UUID sourceId, String severity, Instant since, List<String> statuses
    );

    boolean existsBySourceIdAndCiIdAndTitleAndCreatedAtAfter(
            UUID sourceId, UUID ciId, String title, Instant since
    );

    boolean existsBySourceIdAndCiIdIsNullAndTitleAndCreatedAtAfter(
            UUID sourceId, String title, Instant since
    );

    boolean existsBySourceId(UUID sourceId);

    @Query("SELECT e FROM EventJpaEntity e WHERE e.ciId IN :ciIds AND e.status NOT IN ('closed', 'archived')")
    List<EventJpaEntity> findActiveByCiIds(@Param("ciIds") List<UUID> ciIds);

    boolean existsByCiId(UUID ciId);

    List<EventJpaEntity> findByRootEventId(UUID rootEventId);

    List<EventJpaEntity> findBySourceIdAndCiIdAndTitleAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
            UUID sourceId, UUID ciId, String title, Instant since, List<String> statuses
    );

    List<EventJpaEntity> findBySourceIdAndCiIdIsNullAndTitleAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
            UUID sourceId, String title, Instant since, List<String> statuses
    );

    List<EventJpaEntity> findBySourceIdAndCiIdAndSeverityAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
            UUID sourceId, UUID ciId, String severity, Instant since, List<String> statuses
    );

    List<EventJpaEntity> findBySourceIdAndCiIdIsNullAndSeverityAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
            UUID sourceId, String severity, Instant since, List<String> statuses
    );

    List<EventJpaEntity> findBySourceIdAndCiIdAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
            UUID sourceId, UUID ciId, Instant since, List<String> statuses
    );

    List<EventJpaEntity> findBySourceIdAndCiIdIsNullAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
            UUID sourceId, Instant since, List<String> statuses
    );
}
