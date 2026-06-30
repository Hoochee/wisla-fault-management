package ru.wisla.fm.processing.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wisla.fm.processing.domain.EventEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<EventEntity, UUID>, JpaSpecificationExecutor<EventEntity> {

    Page<EventEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<EventEntity> findFirstBySourceIdAndTitleAndCiIdAndStatusIn(
            UUID sourceId, String title, UUID ciId, List<String> statuses
    );

    Optional<EventEntity> findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn(
            UUID sourceId, String title, List<String> statuses
    );

    @Query("SELECT e.severity, COUNT(e) FROM EventEntity e WHERE e.status NOT IN ('closed', 'archived') GROUP BY e.severity")
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

    @Query("SELECT e FROM EventEntity e WHERE e.ciId IN :ciIds AND e.status NOT IN ('closed', 'archived')")
    List<EventEntity> findActiveByCiIds(@Param("ciIds") List<UUID> ciIds);

    boolean existsByCiId(UUID ciId);

    List<EventEntity> findByRootEventId(UUID rootEventId);

    List<EventEntity> findBySourceIdAndCiIdAndTitleAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
            UUID sourceId, UUID ciId, String title, Instant since, List<String> statuses
    );

    List<EventEntity> findBySourceIdAndCiIdIsNullAndTitleAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
            UUID sourceId, String title, Instant since, List<String> statuses
    );

    List<EventEntity> findBySourceIdAndCiIdAndSeverityAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
            UUID sourceId, UUID ciId, String severity, Instant since, List<String> statuses
    );

    List<EventEntity> findBySourceIdAndCiIdIsNullAndSeverityAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
            UUID sourceId, String severity, Instant since, List<String> statuses
    );

    List<EventEntity> findBySourceIdAndCiIdAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
            UUID sourceId, UUID ciId, Instant since, List<String> statuses
    );

    List<EventEntity> findBySourceIdAndCiIdIsNullAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
            UUID sourceId, Instant since, List<String> statuses
    );
}
