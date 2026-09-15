package team.inreok.poppyserver.domain.session.application

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class RecoveryCodeIssuer(
    private val sessionRepository: SessionRepository,
) {
    fun issue(): IssuedRecoveryCode {
        repeat(MAX_RECOVERY_CODE_ISSUE_ATTEMPTS) {
            val candidate = RecoveryCodeGenerator.issue()
            if (sessionRepository.findByRecoveryCodeDigest(candidate.digest) == null) {
                return candidate
            }
        }
        error("Unable to issue a unique recovery code")
    }
}

private const val MAX_RECOVERY_CODE_ISSUE_ATTEMPTS = 5
