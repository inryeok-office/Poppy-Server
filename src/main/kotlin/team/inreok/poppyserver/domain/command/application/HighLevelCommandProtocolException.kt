package team.inreok.poppyserver.domain.command.application

enum class HighLevelCommandProtocolErrorCode {
    MALFORMED_JSON,
    UNSUPPORTED_PROTOCOL_VERSION,
    UNKNOWN_COMMAND_TYPE,
    INVALID_FIELD,
    INVALID_PARAMETER,
    INVALID_SEQUENCE,
}

class HighLevelCommandProtocolException(
    val code: HighLevelCommandProtocolErrorCode,
    detail: String,
    cause: Throwable? = null,
) : RuntimeException(detail, cause)
