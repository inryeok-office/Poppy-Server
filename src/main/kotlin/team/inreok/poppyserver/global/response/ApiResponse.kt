package team.inreok.poppyserver.global.response

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: ApiErrorBody?,
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(success = true, data = data, error = null)

        fun success(): ApiResponse<Unit> = ApiResponse(success = true, data = Unit, error = null)

        fun failure(error: ApiErrorBody): ApiResponse<Nothing> = ApiResponse(success = false, data = null, error = error)
    }
}

data class ApiErrorBody(
    val code: String,
    val message: String,
    val fieldErrors: List<FieldErrorItem> = emptyList(),
)

data class FieldErrorItem(
    val field: String,
    val reason: String,
)
