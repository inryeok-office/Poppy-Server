package team.inreok.poppyserver.domain.admin.presentation

import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.poppyserver.global.response.ApiResponse
import team.inreok.poppyserver.global.security.AdminSessionCredentials

@RestController
@RequestMapping("/api/v1/admin/boundary-probe")
class AdminBoundaryProbeController {
    @GetMapping
    fun probe(
        @RequestAttribute(AdminSessionCredentials.SESSION_ID_ATTRIBUTE) adminSessionId: UUID,
    ): ApiResponse<AdminBoundaryProbeResponse> = ApiResponse.success(AdminBoundaryProbeResponse(adminSessionId))
}

data class AdminBoundaryProbeResponse(
    val adminSessionId: UUID,
)
