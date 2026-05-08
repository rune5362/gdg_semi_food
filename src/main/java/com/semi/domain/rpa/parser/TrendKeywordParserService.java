package com.semi.domain.rpa.parser;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.semi.domain.keyword.TrendKeyword;
import com.semi.domain.keyword.TrendKeywordRepository;
import com.semi.domain.rpa.parser.mapper.TrendKeywordMapper;
import com.semi.domain.rpa.parser.response.RpaTrendKeywordParseTarget;
import com.semi.domain.rpa.parser.response.TrendKeywordResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j 
public class TrendKeywordParserService {

    private final RestClient restClient = RestClient.create();
    private final TrendKeywordRepository repository;
    private final TrendKeywordMapper trendKeywordMapper; // 매퍼 주입

    public List<TrendKeywordResponse.TrendKeywordItem> getNaverKeywords() {

        // final String targetSiteUrl = "https://snxbest.naver.com/keyword/best?categoryId=50000006&sortType=KEYWORD_POPULAR&periodType=DAILY&ageType=ALL&activeRankId=2165824835&syncDate=20260423";
        final String targetSiteUrl = "https://snxbest.naver.com/keyword/best?categoryId=50000006&sortType=KEYWORD_POPULAR&periodType=DAILY&ageType=ALL";
        final String targetDataUrl = "https://snxbest.naver.com/api/v1/snxbest/keyword/rank?ageType=ALL&categoryId=50000006&sortType=KEYWORD_POPULAR&periodType=DAILY";

        final String rawJson = rpaRetry("네이버 키워드 원문 조회", () -> restClient.get()
            .uri(targetDataUrl)
            .header("User-Agent", Constants.Http.USER_AGENT)
            .header("Accept", Constants.Http.CONTENT_TYPE_JSON)
            .retrieve()
            .body(String.class));

        RpaLogContext.info(log, "네이버 응답 원문: " + rawJson);
        
        // data가 json/xml 둘 다 올 수 있기 때문에, Jackson이 자동으로 파싱하도록 설정
        final List<TrendKeywordResponse.TrendKeywordItem> response = rpaRetry("네이버 키워드 목록 조회", () -> restClient.get()
            .uri(targetDataUrl)
            .header("User-Agent", Constants.Http.USER_AGENT)
            .header("Accept", MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE)
            .header("Accept-Language", Constants.Http.ACCEPT_LANGUAGE)
            .header("Referer", targetSiteUrl)
            // .header("Referer", "https://snxbest.naver.com/")
            // .header("Referer", "https://naver.com")
            .retrieve()
            .body(new ParameterizedTypeReference<List<TrendKeywordResponse.TrendKeywordItem>>() {}));  // 여기서 Jackson MessageConverter가 자동 동작함
            // .body(TrendKeywordResponse.class); // 여기서 Jackson MessageConverter가 자동 동작함

        RpaLogContext.info(log, "수신된 데이터: " + response);
        return response;
    }

    @Transactional
    public List<TrendKeyword> saveWithSequentialId(List<TrendKeywordResponse.TrendKeywordItem> items) {
        // 1. 현재 DB에서 가장 큰 ID 가져오기 (데이터 없으면 0으로 시작)
        final Long maxId = repository.findMaxId();
        long nextId = (maxId == null) ? 0L : maxId;

        // 2. DTO -> VO 변환 (MapStruct 사용)
        final List<TrendKeyword> parsedKeywords = trendKeywordMapper.toVoList(items); // 파싱된 데이터
        if (parsedKeywords == null || parsedKeywords.isEmpty()) {
            return List.of();
        }

        final TrendKeyword latestParsedKeyword = parsedKeywords.get(0);
        final TrendKeyword latestSavedRecord = repository.findFirstByOrderByIdDesc();

        // 파싱한 데이터가 기존 데이터보다 최신이 아니라면 안내용 리스트 반환
        if (latestSavedRecord != null 
            && !latestParsedKeyword.getCollectedAt().toLocalDate().isAfter(latestSavedRecord.getCollectedAt().toLocalDate())) {
            
            final String message = "동일하거나 이전 날짜의 데이터입니다. 저장하지 않습니다. "
                + "파싱 날짜: " + latestParsedKeyword.getCollectedAt().toLocalDate()
                + ", DB 최신 날짜: " + latestSavedRecord.getCollectedAt().toLocalDate();
            
            return List.of(
                new TrendKeyword(
                    0L,
                    message,
                    0,
                    0,
                    LocalDateTime.now(),
                    false,
                    null,
                    null
                )
            );
        }

        // 3. 중복 확인 후 새 ID 부여하여 리스트 구성
        final List<TrendKeyword> keywordsToSave = new ArrayList<>();

        for (TrendKeyword parsedKeyword : parsedKeywords) {
            // 이미 저장된 날짜와 키워드인지 확인
            if (repository.existsByCollectedAtAndKeyword(parsedKeyword.getCollectedAt(), parsedKeyword.getKeyword())) {
                continue;
            }

            // 수동으로 ID를 부여하기 위해 새로운 객체 생성 (기존 필드는 유지)
            // @Builder
            TrendKeyword keywordToSave = TrendKeyword.builder()
                .id(++nextId) // 1 증가시킨 값을 ID로 부여
                .keyword(parsedKeyword.getKeyword())
                .rank(parsedKeyword.getRank())
                .frequency(1)
                .collectedAt(parsedKeyword.getCollectedAt())
                .isActive(true)
                .rankingId(parsedKeyword.getRankingId())
                .syncDate(parsedKeyword.getSyncDate())
                .build();
            keywordsToSave.add(keywordToSave);
        }

        // 4. 최종 저장
        if (!keywordsToSave.isEmpty()) {
            repository.saveAllAndFlush(keywordsToSave);
            RpaLogContext.info(log, "{}건 저장 완료 (마지막 ID: {})", keywordsToSave.size(), nextId);
        }
        return keywordsToSave;
    }

    @Transactional
    public List<TrendKeyword> saveTrendKeywords(List<TrendKeywordResponse.TrendKeywordItem> items) {
        // 매퍼로 리스트 전체 변환
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        final List<TrendKeyword> keywords = trendKeywordMapper.toVoList(items);
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }

        repository.saveAll(keywords);
        return keywords;
    }

    @Transactional(readOnly = true)
    public List<RpaTrendKeywordParseTarget> getTodayRpaParseTargets(int requestedSize) {
        int targetSize = requestedSize <= 0 ? 20 : requestedSize;
        LocalDateTime start = LocalDate.now().atStartOfDay();

        return repository.findRecentRpaReadyKeywords(start, PageRequest.of(0, targetSize))
            .stream()
            .sorted(Comparator
                .comparing(TrendKeyword::getRank, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(TrendKeyword::getId))
            .map(this::toRpaTrendKeywordParseTarget)
            .toList();
    }

    private RpaTrendKeywordParseTarget toRpaTrendKeywordParseTarget(TrendKeyword keyword) {
        return new RpaTrendKeywordParseTarget(
            keyword.getId(),
            keyword.getRankingId(),
            keyword.getSyncDate().format(DateTimeFormatter.BASIC_ISO_DATE),
            keyword.getRank(),
            keyword.getCollectedAt()
        );
    }

    private <T> T rpaRetry(String actionName, java.util.function.Supplier<T> action) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException exception) {
                lastException = exception;
                RpaLogContext.warn(log, "{} 실패. attempt={}/3", actionName, attempt, exception);
            }
        }
        throw lastException;
    }
}
