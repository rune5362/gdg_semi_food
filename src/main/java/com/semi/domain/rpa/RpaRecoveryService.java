package com.semi.domain.rpa;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.semi.domain.rpa.response.RpaRecoveryResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RpaRecoveryService {

    private static final long DEFAULT_STALE_MINUTES = 60L;

    private final RpaLogRepository rpaLogRepository;

    @Scheduled(fixedDelayString = "${rpa.recovery.fixed-delay-ms:300000}")
    public void recoverStaleRunningLogsOnSchedule() {
        RpaRecoveryResponse response = recoverStaleRunningLogs(DEFAULT_STALE_MINUTES);
        if (response.recoveredCount() > 0) {
            log.warn("장시간 RUNNING RPA 로그 자동 복구: {}건", response.recoveredCount());
        }
    }

    @Transactional
    public RpaRecoveryResponse recoverStaleRunningLogs(long staleMinutes) {
        long safeStaleMinutes = staleMinutes <= 0 ? DEFAULT_STALE_MINUTES : staleMinutes;
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(safeStaleMinutes);
        List<RpaLog> staleLogs = rpaLogRepository.findAllByStatusAndStartedAtBefore(RpaStatus.RUNNING, threshold);

        for (RpaLog staleLog : staleLogs) {
            staleLog.fail("장시간 RUNNING 상태 자동 복구 처리. 기준 분=" + safeStaleMinutes);
        }

        if (!staleLogs.isEmpty()) {
            rpaLogRepository.saveAllAndFlush(staleLogs);
        }

        return new RpaRecoveryResponse(
            threshold,
            staleLogs.size(),
            "장시간 RUNNING RPA 로그 복구 완료"
        );
    }
}
