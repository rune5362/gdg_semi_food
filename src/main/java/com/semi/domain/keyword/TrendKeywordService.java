package com.semi.domain.keyword;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TrendKeywordService { 

    private final TrendKeywordRepository trendKeywordRepository;

    @Transactional
    public List<TrendKeyword> getKeywords(){
        return trendKeywordRepository.findByIsActiveTrueOrderByRankAsc();
    }

    @Transactional(readOnly = true)
    public List<TrendKeyword> getKeywordsOrderById() {
        return trendKeywordRepository.findByIsActiveTrueOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<TrendKeyword> getKeywordsCollectedOnOrderById(LocalDate collectedDate) {
        return trendKeywordRepository.findTop20KeywordsCollectedBetweenOrderById(
                collectedDate.atStartOfDay(),
                collectedDate.plusDays(1).atStartOfDay()
        );
    }
    
}
