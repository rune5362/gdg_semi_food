package com.semi.exception;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    /** 유효성 검사 실패 (400) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (a, b) -> a
                ));
        return ResponseEntity.badRequest()
                .body(Map.of("message", "입력값을 확인해주세요.", "errors", errors));
    }

    /** 아이디/이메일 중복 (409) */    
    @ExceptionHandler(DuplicateMemberException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateMemberException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage()));
    }

    /** 회원 없음 (404) */
    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(MemberNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    /** 관리자 계정 삭제 불가 (400) */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "요청한 리소스를 찾을 수 없습니다."));
    }

    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbort(ClientAbortException ex) {
        // Client disconnected - don't log as error, just debug
        log.debug("Client disconnected during request processing: {}", ex.getMessage());
    }

    @ExceptionHandler({AsyncRequestNotUsableException.class, HttpMessageNotWritableException.class})
    public ResponseEntity<Void> handleResponseWriteFailure(Exception ex) throws Exception {
        if (isClientDisconnect(ex)) {
            log.debug("Client disconnected while writing response: {}", ex.getMessage());
            return ResponseEntity.noContent().build();
        }
        throw ex;
    }

    /** 예상치 못한 서버 오류 (500) — 내부 오류 내용 노출 금지 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        if (isClientDisconnect(ex)) {
            log.debug("Client disconnected during request processing: {}", ex.getMessage());
            return ResponseEntity.noContent().build();
        }

        log.error("[500] 예상치 못한 오류: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

    static boolean isClientDisconnect(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof ClientAbortException || current instanceof AsyncRequestNotUsableException) {
                return true;
            }

            String message = current.getMessage();
            if (message == null) {
                continue;
            }

            String lowerMessage = message.toLowerCase();
            if (lowerMessage.contains("broken pipe") || lowerMessage.contains("connection reset")) {
                return true;
            }
        }
        return false;
    }
}
