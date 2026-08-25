package team.inreok.poppyserver.global.error

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val message: String,
) {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "요청 값이 올바르지 않습니다"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다"),
    ROBOT_REGISTRATION_INVALID(HttpStatus.BAD_REQUEST, "ROBOT_REGISTRATION_INVALID", "Robot registration data is invalid"),
    ROBOT_ALREADY_REGISTERED(HttpStatus.CONFLICT, "ROBOT_ALREADY_REGISTERED", "The Robot is already registered"),
    ROBOT_COMPATIBILITY_NOT_VERIFIED(HttpStatus.UNPROCESSABLE_ENTITY, "ROBOT_COMPATIBILITY_NOT_VERIFIED", "Robot compatibility is not verified"),
    ROBOT_FILTER_INVALID(HttpStatus.BAD_REQUEST, "ROBOT_FILTER_INVALID", "The Robot filter is invalid"),
    ROBOT_NOT_FOUND(HttpStatus.NOT_FOUND, "ROBOT_NOT_FOUND", "The Robot was not found"),
    ROBOT_UPDATE_CONFLICT(HttpStatus.CONFLICT, "ROBOT_UPDATE_CONFLICT", "The Robot cannot be changed while it is executing"),
    ROBOT_CAPABILITY_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "ROBOT_CAPABILITY_INVALID", "The Robot capability is invalid"),
}
