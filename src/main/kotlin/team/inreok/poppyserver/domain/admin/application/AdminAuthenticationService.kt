package team.inreok.poppyserver.domain.admin.application

import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class AdminAuthenticationService(
    private val adminCredentialVerifier: AdminCredentialVerifier,
    private val adminSessionService: AdminSessionService,
    private val adminLoginAttemptRateLimiter: AdminLoginAttemptRateLimiter,
) {
    fun login(username: String, password: String, clientAddress: String): OpenedAdminSession {
        try {
            val attemptKey = attemptKey(clientAddress, username)
            adminLoginAttemptRateLimiter.checkAndRecord(attemptKey)
            adminCredentialVerifier.verify(username, password)
            val opened = adminSessionService.open()
            adminLoginAttemptRateLimiter.reset(attemptKey)
            return opened
        } catch (exception: ApplicationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error("Admin login failed: {}", exception.javaClass.name)
            throw ApplicationException(ErrorCode.ADMIN_LOGIN_FAILED)
        }
    }

    fun logout(adminSessionId: UUID) {
        try {
            adminSessionService.revoke(adminSessionId)
        } catch (exception: ApplicationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error("Admin logout failed: {}", exception.javaClass.name)
            throw ApplicationException(ErrorCode.ADMIN_LOGOUT_FAILED)
        }
    }

    private fun attemptKey(clientAddress: String, username: String): String =
        "$clientAddress:${username.trim().lowercase()}"

    companion object {
        private val logger = LoggerFactory.getLogger(AdminAuthenticationService::class.java)
    }
}
