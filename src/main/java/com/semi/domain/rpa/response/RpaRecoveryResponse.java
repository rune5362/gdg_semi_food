package com.semi.domain.rpa.response;

import java.time.LocalDateTime;

public record RpaRecoveryResponse(
    LocalDateTime thresholdStartedBefore,
    Integer recoveredCount,
    String message
) {
}
