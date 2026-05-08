package com.semi.domain.rpa.parser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * RPA 파싱 작업 중 발생하는 로그를 스레드 로컬에 수집하여
 * 최종적으로 파일 로그에 기록하기 위한 컨텍스트입니다.
 */
public class RpaLogContext {

    private static final ThreadLocal<List<String>> LOGS = ThreadLocal.withInitial(ArrayList::new);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void addLog(String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        LOGS.get().add("[" + timestamp + "] " + message);
    }

    public static List<String> getLogs() {
        return new ArrayList<>(LOGS.get());
    }

    public static void info(org.slf4j.Logger logger, String message) {
        logger.info(message);
        addLog("INFO - " + message);
    }

    public static void info(org.slf4j.Logger logger, String format, Object... arguments) {
        logger.info(format, arguments);
        addLog("INFO - " + org.slf4j.helpers.MessageFormatter.arrayFormat(format, arguments).getMessage());
    }

    public static void warn(org.slf4j.Logger logger, String message) {
        logger.warn(message);
        addLog("WARN - " + message);
    }

    public static void warn(org.slf4j.Logger logger, String format, Object... arguments) {
        logger.warn(format, arguments);
        addLog("WARN - " + org.slf4j.helpers.MessageFormatter.arrayFormat(format, arguments).getMessage());
    }

    public static void error(org.slf4j.Logger logger, String message) {
        logger.error(message);
        addLog("ERROR - " + message);
    }

    public static void error(org.slf4j.Logger logger, String format, Object... arguments) {
        logger.error(format, arguments);
        addLog("ERROR - " + org.slf4j.helpers.MessageFormatter.arrayFormat(format, arguments).getMessage());
    }

    public static void clear() {
        LOGS.remove();
    }
}
