package com.semi.domain.rpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RpaLogRepository extends JpaRepository<RpaLog, Long> {

    /** 가장 최근 로그 조회 (대시보드 가동 여부 판별용) */
    Optional<RpaLog> findTopByOrderByStartedAtDesc();

    /** TiDB 수동 ID 부여를 위한 최신 ID 조회 */
    @Query("SELECT MAX(r.id) FROM RpaLog r")
    Long findMaxId();

    List<RpaLog> findAllByStartedAtGreaterThanEqualOrderByStartedAtDesc(LocalDateTime start);

    List<RpaLog> findAllByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(
        LocalDateTime start,
        LocalDateTime end
    );

    List<RpaLog> findAllByStartedAtGreaterThanEqualAndStartedAtLessThanAndStatusNotOrderByStartedAtDesc(
        LocalDateTime start,
        LocalDateTime end,
        RpaStatus status
    );

    List<RpaLog> findAllByStatusAndStartedAtBefore(RpaStatus status, LocalDateTime startedAt);

}
