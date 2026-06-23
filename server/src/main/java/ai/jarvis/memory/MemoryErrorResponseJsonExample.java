package ai.jarvis.memory;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MemoryErrorResponseJsonExample {

    public static final String VALIDATION_ERROR_BLANK_STRING_EXAMPLE = """
            {
                "errorCode": "VALIDATION_ERROR",
                "message": "Request validation failed",
                "details": [
                    "must not be blank"
                ],
                "path": "/api/v1/memories",
                "timestamp": "2026-06-01T10:05:01.123456Z"
            }
            """;

    public static final String VALIDATION_ERROR_INVALID_MEMORY_ID = """
            {
                "errorCode": "BAD_REQUEST",
                "message": "Type mismatch.",
                "path": "/api/v1/memories/12345",
                "timestamp": "2026-06-01T10:05:01.123456Z"
            }
            """;

    public static final String NOT_FOUND_BY_MEMORY_ID = """
            {
                "errorCode": "NOT_FOUND",
                "message": "Memory not found",
                "path": "/api/v1/memories/5eff485b-1ca6-4d4f-b94c-c30c010de82b",
                "timestamp": "2026-06-01T10:05:01.123456Z"
            }
            """;

    public static final String CONFLICTING_MEMORY = """
            {
                "errorCode": "CONFLICT",
                "message": "Memory with the specified content already exists",
                "path": "/api/v1/memories",
                "timestamp": "2026-06-01T10:05:01.123456Z"
            }
            """;
}
