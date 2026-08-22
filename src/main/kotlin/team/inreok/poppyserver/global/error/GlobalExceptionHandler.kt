package team.inreok.poppyserver.global.error

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import team.inreok.poppyserver.global.response.ApiErrorBody
import team.inreok.poppyserver.global.response.ApiResponse
import team.inreok.poppyserver.global.response.FieldErrorItem

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException::class)
    fun handleApplicationException(exception: ApplicationException): ResponseEntity<ApiResponse<Nothing>> {
        val errorCode = exception.errorCode
        return ResponseEntity.status(errorCode.status)
            .body(ApiResponse.failure(ApiErrorBody(code = errorCode.code, message = exception.message ?: errorCode.message)))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(exception: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val fieldErrors = exception.bindingResult.fieldErrors.map {
            FieldErrorItem(field = it.field, reason = it.defaultMessage ?: ErrorCode.INVALID_INPUT.message)
        }
        val errorCode = ErrorCode.INVALID_INPUT
        return ResponseEntity.status(errorCode.status)
            .body(ApiResponse.failure(ApiErrorBody(code = errorCode.code, message = errorCode.message, fieldErrors = fieldErrors)))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(exception: Exception): ResponseEntity<ApiResponse<Nothing>> {
        val errorCode = ErrorCode.INTERNAL_SERVER_ERROR
        return ResponseEntity.status(errorCode.status)
            .body(ApiResponse.failure(ApiErrorBody(code = errorCode.code, message = errorCode.message)))
    }
}
