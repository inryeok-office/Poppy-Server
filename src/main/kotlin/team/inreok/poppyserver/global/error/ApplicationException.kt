package team.inreok.poppyserver.global.error

class ApplicationException(
    val errorCode: ErrorCode,
    message: String = errorCode.message,
) : RuntimeException(message)
