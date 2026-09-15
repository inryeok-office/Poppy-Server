package team.inreok.poppyserver.global.error

import team.inreok.poppyserver.global.response.FieldErrorItem

class ApplicationException(
    val errorCode: ErrorCode,
    message: String = errorCode.message,
    val fieldErrors: List<FieldErrorItem> = emptyList(),
) : RuntimeException(message)
