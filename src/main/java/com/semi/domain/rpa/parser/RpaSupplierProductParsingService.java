package com.semi.domain.rpa.parser;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.semi.domain.keyword.TrendKeyword;
import com.semi.domain.keyword.TrendKeywordRepository;
import com.semi.domain.product.Product;
import com.semi.domain.rpa.RpaLog;
import com.semi.domain.rpa.RpaLogRepository;
import com.semi.domain.rpa.RpaStatus;
import com.semi.domain.rpa.parser.response.RpaSupplierProductParseResult;
import com.semi.domain.rpa.parser.response.RpaTrendKeywordParseTarget;
import com.semi.domain.rpa.parser.response.SupplierAndProductResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RpaSupplierProductParsingService {

    private final TrendKeywordParserService trendKeywordParserService;
    private final TrendKeywordRepository trendKeywordRepository;
    private final SupplierAndProductService supplierAndProductService;
    private final RpaLogRepository rpaLogRepository;
    private final RpaParsingLogFileService rpaParsingLogFileService;

    public List<RpaSupplierProductParseResult> parseTodaySupplierAndProducts(int requestedSize) {
        LocalDateTime rpaStartedAt = LocalDateTime.now();
        RpaLog rpaLog = startRpaLog(rpaStartedAt);
        List<RpaSupplierProductParseResult> rpaResults = new ArrayList<>();

        try {
            List<RpaTrendKeywordParseTarget> rpaTargets = trendKeywordParserService.getTodayRpaParseTargets(requestedSize);

            for (RpaTrendKeywordParseTarget rpaTarget : rpaTargets) {
                rpaResults.add(parseSupplierAndProducts(rpaTarget));
            }

            finishRpaLog(rpaLog, rpaStartedAt, rpaResults);
        } catch (RuntimeException exception) {
            failRpaLog(rpaLog, rpaStartedAt, rpaResults, exception);
            throw exception;
        } finally {
            RpaLogContext.clear();
        }
        return rpaResults;
    }

    private RpaLog startRpaLog(LocalDateTime startedAt) {
        Long maxId = rpaLogRepository.findMaxId();
        RpaLog rpaLog = RpaLog.builder()
            .startedAt(startedAt)
            .build();
        rpaLog.setId(maxId == null ? 1L : maxId + 1L);
        rpaLog.setMessage("RPA 공급자/상품 파싱 시작");
        return rpaLogRepository.saveAndFlush(rpaLog);
    }

    private void finishRpaLog(
        RpaLog rpaLog,
        LocalDateTime startedAt,
        List<RpaSupplierProductParseResult> results
    ) {
        int productCount = results.stream()
            .mapToInt(result -> result.productCount() == null ? 0 : result.productCount())
            .sum();
        long failedCount = results.stream()
            .filter(result -> RpaStatus.FAILED.name().equals(result.status()))
            .count();

        if (failedCount > 0) {
            rpaLog.fail("RPA 공급자/상품 파싱 실패 " + failedCount + "건 / 전체 " + results.size() + "건");
            rpaLog.setKeywordCount(results.size());
            rpaLog.setProductCount(productCount);
        } else {
            rpaLog.complete(results.size(), productCount);
            rpaLog.setMessage("RPA 공급자/상품 파싱 완료");
        }

        String fileLogPath = rpaParsingLogFileService.writeParsingLog(startedAt, rpaLog.getEndedAt(), results);
        rpaLog.setMessage(rpaLog.getMessage() + " / fileLog=" + fileLogPath);
        rpaLogRepository.saveAndFlush(rpaLog);
    }

    private void failRpaLog(
        RpaLog rpaLog,
        LocalDateTime startedAt,
        List<RpaSupplierProductParseResult> results,
        RuntimeException exception
    ) {
        int productCount = results.stream()
            .mapToInt(result -> result.productCount() == null ? 0 : result.productCount())
            .sum();
        rpaLog.fail("RPA 공급자/상품 파싱 중단: " + exception.getMessage());
        rpaLog.setKeywordCount(results.size());
        rpaLog.setProductCount(productCount);

        String fileLogPath = rpaParsingLogFileService.writeParsingLog(startedAt, rpaLog.getEndedAt(), results);
        rpaLog.setMessage(rpaLog.getMessage() + " / fileLog=" + fileLogPath);
        rpaLogRepository.saveAndFlush(rpaLog);
    }

    private RpaSupplierProductParseResult parseSupplierAndProducts(RpaTrendKeywordParseTarget rpaTarget) {
        try {
            TrendKeyword rpaKeyword = trendKeywordRepository.findById(rpaTarget.keywordId())
                .orElseThrow(() -> new IllegalArgumentException("TrendKeyword가 없습니다. keywordId=" + rpaTarget.keywordId()));

            if (supplierAndProductService.hasTodayProductsForKeywordAndSyncDate(rpaKeyword, rpaTarget.syncDate())) {
                return new RpaSupplierProductParseResult(
                    rpaTarget.keywordId(),
                    rpaTarget.rankId(),
                    rpaTarget.syncDate(),
                    rpaTarget.trendRank(),
                    "SKIPPED",
                    0,
                    "오늘 이미 처리한 keyword/rank/syncDate 분류이므로 공급자/상품 API 호출을 건너뜁니다."
                );
            }

            SupplierAndProductResponse rpaResponse = supplierAndProductService.getSupplierAndProducts(
                rpaTarget.rankId(),
                rpaTarget.syncDate()
            );
            List<Product> rpaProducts = supplierAndProductService.saveSupplierAndProductsWithSequentialId(rpaKeyword, rpaResponse);

            return new RpaSupplierProductParseResult(
                rpaTarget.keywordId(),
                rpaTarget.rankId(),
                rpaTarget.syncDate(),
                rpaTarget.trendRank(),
                RpaStatus.COMPLETED.name(),
                rpaProducts.size(),
                "공급자/상품 저장 완료"
            );
        } catch (RuntimeException rpaException) {
            return new RpaSupplierProductParseResult(
                rpaTarget.keywordId(),
                rpaTarget.rankId(),
                rpaTarget.syncDate(),
                rpaTarget.trendRank(),
                RpaStatus.FAILED.name(),
                0,
                rpaException.getMessage()
            );
        }
    }
}
