package team.inreok.poppyserver.global.error

import jakarta.servlet.http.HttpServletResponse
import java.io.IOException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.context.request.async.AsyncRequestTimeoutException
import team.inreok.poppyserver.global.response.ApiErrorBody
import team.inreok.poppyserver.global.response.ApiResponse
import team.inreok.poppyserver.global.response.FieldErrorItem

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException::class)
    fun handleApplicationException(exception: ApplicationException): ResponseEntity<ApiResponse<Nothing>> {
        val errorCode = exception.errorCode
        return ResponseEntity.status(errorCode.status).contentType(MediaType.APPLICATION_JSON)
            .body(
                ApiResponse.failure(
                    ApiErrorBody(
                        code = errorCode.code,
                        message = exception.message ?: errorCode.message,
                        fieldErrors = exception.fieldErrors,
                    ),
                ),
            )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(exception: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val fieldErrors = exception.bindingResult.fieldErrors.map {
            FieldErrorItem(field = it.field, reason = it.defaultMessage ?: ErrorCode.INVALID_INPUT.message)
        }
        val errorCode = ErrorCode.INVALID_INPUT
        return ResponseEntity.status(errorCode.status).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.failure(ApiErrorBody(code = errorCode.code, message = errorCode.message, fieldErrors = fieldErrors)))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatch(
        exception: MethodArgumentTypeMismatchException,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val errorCode = ErrorCode.INVALID_INPUT
        return ResponseEntity.status(errorCode.status).contentType(MediaType.APPLICATION_JSON)
            .body(
                ApiResponse.failure(
                    ApiErrorBody(
                        code = errorCode.code,
                        message = errorCode.message,
                        fieldErrors = listOf(FieldErrorItem(field = exception.name, reason = errorCode.message)),
                    ),
                ),
            )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(): ResponseEntity<ApiResponse<Nothing>> {
        val errorCode = ErrorCode.INVALID_INPUT
        return ResponseEntity.status(errorCode.status).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.failure(ApiErrorBody(code = errorCode.code, message = errorCode.message)))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(exception: Exception, response: HttpServletResponse): ResponseEntity<ApiResponse<Nothing>>? {
        // SSE 응답은 시작할 때 헤더가 이미 내보내져 Content-Type 이 text/event-stream 으로 굳는다.
        // 그 뒤의 예외(타임아웃·연결 끊김)에는 JSON 에러 본문을 쓸 수 없다. 쓰려 하면
        // "No converter for ApiResponse with preset Content-Type 'text/event-stream'" 로 다시 실패한다.
        if (response.contentType?.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE) == true) {
            logClosedEventStream(exception)
            return null
        }
        val errorCode = ErrorCode.INTERNAL_SERVER_ERROR
        return ResponseEntity.status(errorCode.status).contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.failure(ApiErrorBody(code = errorCode.code, message = errorCode.message)))
    }

    private fun logClosedEventStream(exception: Exception) {
        if (exception is AsyncRequestTimeoutException ||
            exception is AsyncRequestNotUsableException ||
            exception is IOException
        ) {
            // 정상 운영에서 흔한 종료(SSE 만료, 브라우저·프록시가 연결을 닫음)라 스택을 남기지 않는다.
            logger.debug("SSE stream closed: {}", exception.javaClass.simpleName)
        } else {
            logger.warn("Unexpected error on an event stream that already started", exception)
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
