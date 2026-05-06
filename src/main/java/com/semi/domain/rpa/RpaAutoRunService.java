package com.semi.domain.rpa;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.semi.domain.rpa.parser.TrendKeywordParserService;
import com.semi.domain.rpa.parser.response.TrendKeywordResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RpaAutoRunService {

    private final RpaConfigRepository rpaConfigRepository;
    private final TrendKeywordParserService trendKeywordParserService;
    private final RpaAsyncExecutionService rpaAsyncExecutionService;
    private final RpaLogRepository rpaLogRepository;

    private LocalDateTime lastRunDayTime = null;

    @Scheduled(cron = "0 * * * * *")
    public void checkAndRunRpaSchedule() {
        RpaConfig config = rpaConfigRepository.findById(1L).orElse(null);
        if (config == null || !config.isAutoRunEnabled()) {
            return;
        }

        LocalTime now = LocalTime.now();
        String currentHm = now.format(DateTimeFormatter.ofPattern("HH:mm"));

        List<String> scheduledTimes = Arrays.stream(config.getRunTimes().split(","))
                .map(String::trim)
                .toList();

        if (scheduledTimes.contains(currentHm)) {
            // 방어 로직: 같은 분에 중복 실행되는 것 방지
            LocalDateTime currentTruncated = LocalDateTime.now().withSecond(0).withNano(0);
            if (lastRunDayTime != null && lastRunDayTime.equals(currentTruncated)) {
                return;
            }

            // 추가 방어 로직: DB 로그상 1시간 내에 실행된 기록이 있으면 스킵
            boolean recentlyRun = rpaLogRepository.findTopByOrderByStartedAtDesc()
                    .map(log -> log.getStartedAt().isAfter(LocalDateTime.now().minusMinutes(50)))
                    .orElse(false);

            if (recentlyRun) {
                return;
            }

            lastRunDayTime = currentTruncated;
            log.info("RPA 스케줄러 자동 실행 시작 (설정된 시간: {})", currentHm);
            executeRpaPipeline();
        }
    }

    private void executeRpaPipeline() {
        try {
            // 1. 키워드 저장
            List<TrendKeywordResponse.TrendKeywordItem> trendList = trendKeywordParserService.getNaverKeywords();
            trendKeywordParserService.saveWithSequentialId(trendList);

            // 2. 공급자 및 상품 파싱 비동기 실행 (기본 20개 사이즈)
            if (!rpaAsyncExecutionService.tryStartSupplierProductParsing(20)) {
                log.warn("자동 실행 중 RPA 비동기 실행 요청에 실패했습니다. (이미 실행 중일 수 있습니다.)");
            }
        } catch (Exception e) {
            log.error("자동 실행 RPA 파이프라인 수행 중 오류 발생", e);
        }
    }

    @Transactional(readOnly = true)
    public RpaConfig getConfig() {
        return rpaConfigRepository.findById(1L).orElseGet(() -> RpaConfig.builder()
                .id(1L)
                .autoRunEnabled(false)
                .runTimes("07:00,19:00")
                .updatedAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public RpaConfig updateConfig(boolean enabled, String runTimes) {
        RpaConfig config = rpaConfigRepository.findById(1L).orElseGet(() -> RpaConfig.builder()
                .id(1L)
                .autoRunEnabled(false)
                .runTimes("07:00,19:00")
                .updatedAt(LocalDateTime.now())
                .build());

        config.updateConfig(enabled, runTimes);
        return rpaConfigRepository.save(config);
    }
}
