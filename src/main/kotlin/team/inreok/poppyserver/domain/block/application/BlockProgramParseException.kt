package team.inreok.poppyserver.domain.block.application

enum class BlockProgramParseErrorCode {
    MALFORMED_JSON,
    UNSUPPORTED_SCHEMA_VERSION,
    UNKNOWN_BLOCK_TYPE,
    INVALID_FIELD,
    INVALID_PARAMETER,
}

class BlockProgramParseException(
    val code: BlockProgramParseErrorCode,
    detail: String,
    cause: Throwable? = null,
) : IllegalArgumentException("${code.name}: $detail", cause)
