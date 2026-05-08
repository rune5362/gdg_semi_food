package com.semi.domain.rpa.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.semi.domain.rpa.parser.response.RpaSupplierProductParseResult;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RpaParsingLogFileService {

    private static final Path RPA_LOG_DIRECTORY = Path.of("src/main/resources/static/test/rpa/log");
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd_HHmmss");
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String writeParsingLog(
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        List<RpaSupplierProductParseResult> results
    ) {
        String fileName = "rpa_parsing_" + startedAt.format(FILE_NAME_FORMATTER) + ".log";
        Path logPath = RPA_LOG_DIRECTORY.resolve(fileName);

        try {
            Files.createDirectories(RPA_LOG_DIRECTORY);
            Files.write(logPath, buildLogLines(startedAt, endedAt, results), StandardCharsets.UTF_8);
            return logPath.toString();
        } catch (IOException exception) {
            log.warn("RPA 파일 로그 저장 실패: {}", logPath, exception);
            return "FILE_LOG_WRITE_FAILED: " + logPath;
        }
    }

    private List<String> buildLogLines(
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        List<RpaSupplierProductParseResult> results
    ) {
        List<String> lines = new java.util.ArrayList<>();
        LocalDateTime safeEndedAt = endedAt == null ? LocalDateTime.now() : endedAt;
        lines.add("RPA parsing log");
        lines.add("started_at=" + startedAt.format(LOG_TIME_FORMATTER));
        lines.add("ended_at=" + safeEndedAt.format(LOG_TIME_FORMATTER));
        lines.add("target_count=" + results.size());
        lines.add("");

        for (RpaSupplierProductParseResult result : results) {
            lines.add("keywordId=" + result.keywordId()
                + ", rankId=" + result.rankId()
                + ", syncDate=" + result.syncDate()
                + ", trendRank=" + result.trendRank()
                + ", status=" + result.status()
                + ", productCount=" + result.productCount()
                + ", message=" + result.message());
        }

        lines.add("");
        lines.add("--- 세부 실행 로그 ---");
        lines.addAll(RpaLogContext.getLogs());

        return lines;
    }
}
