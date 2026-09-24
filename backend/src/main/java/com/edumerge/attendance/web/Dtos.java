package com.edumerge.attendance.web;

import java.util.List;
import java.util.Map;

/** Request/response DTOs (Java records). */
public final class Dtos {
    private Dtos() {}

    public record LoginRequest(String username, String password) {}

    public record MeResponse(Long id, String username, String role, Long refId) {}

    /** studentId -> "P" | "A" | "L" */
    public record MarkRequest(Map<String, String> statuses) {}

    public record CorrectionRequest(Long sessionId, Long studentId, String newStatus,
                                    String category, String reason) {}

    public record StudentCorrectionRequest(Long sessionId, String category, String reason) {}

    public record DecisionRequest(String decision) {}

    public record NotifyRequest(List<Long> studentIds) {}

    public record SimpleMessage(String message) {}
}
