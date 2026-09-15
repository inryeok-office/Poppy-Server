package team.inreok.poppyserver.domain.session.application

import java.time.Instant
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.poppyserver.domain.block.application.BlockProgramParseException
import team.inreok.poppyserver.domain.block.application.BlockProgramParser
import team.inreok.poppyserver.domain.block.application.BlockProgramValidator
import team.inreok.poppyserver.domain.session.model.BlockRevision
import team.inreok.poppyserver.domain.session.model.Session
import team.inreok.poppyserver.global.error.ApplicationException
import team.inreok.poppyserver.global.error.ErrorCode
import team.inreok.poppyserver.global.response.FieldErrorItem

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = ["url"])
class SessionService(
    private val sessionRepository: SessionRepository,
    private val blockRevisionRepository: BlockRevisionRepository,
    private val sessionAccessVerifier: SessionAccessVerifier,
    private val recoveryCodeIssuer: RecoveryCodeIssuer,
    private val blockProgramParser: BlockProgramParser,
    private val blockProgramValidator: BlockProgramValidator,
) {
    @Transactional
    fun createSession(): SessionCreationResult {
        val issuedToken = sessionAccessVerifier.issue()
        val issuedRecoveryCode = recoveryCodeIssuer.issue()
        val session = sessionRepository.save(
            Session.createWithToken(
                sessionTokenDigest = issuedToken.digest,
                recoveryCodeDigest = issuedRecoveryCode.digest,
            ),
        )
        return SessionCreationResult(
            sessionId = session.id,
            currentBlockVersion = session.currentBlockVersion,
            sessionToken = issuedToken.raw,
            recoveryCode = issuedRecoveryCode.raw,
        )
    }

    @Transactional
    fun appendBlockRevision(sessionId: UUID, document: String): BlockRevisionAppendResult {
        val session = sessionRepository.findByIdForUpdate(sessionId)
            ?: throw ApplicationException(ErrorCode.SESSION_NOT_FOUND)
        validateBlockProgram(document)
        val version = session.advanceBlockVersion()
        val revision = BlockRevision.create(
            sessionId = session.id,
            version = version,
            document = document,
            createdAt = Instant.now(),
        )
        blockRevisionRepository.save(revision)
        sessionRepository.save(session)
        return BlockRevisionAppendResult(
            sessionId = revision.sessionId,
            blockVersion = revision.version,
        )
    }

    private fun validateBlockProgram(document: String) {
        val program = try {
            blockProgramParser.parse(document)
        } catch (exception: BlockProgramParseException) {
            throw ApplicationException(
                errorCode = ErrorCode.BLOCK_PROGRAM_INVALID,
                fieldErrors = listOf(FieldErrorItem(field = "document", reason = exception.code.name)),
            )
        }
        val result = blockProgramValidator.validate(program)
        if (!result.isValid) {
            throw ApplicationException(
                errorCode = ErrorCode.BLOCK_PROGRAM_INVALID,
                fieldErrors = result.errors.map { error ->
                    FieldErrorItem(
                        field = error.path ?: "document",
                        reason = "${error.code.name}: ${error.message}",
                    )
                },
            )
        }
    }
}

data class SessionCreationResult(
    val sessionId: UUID,
    val currentBlockVersion: Long,
    val sessionToken: String,
    val recoveryCode: String,
)

data class BlockRevisionAppendResult(
    val sessionId: UUID,
    val blockVersion: Long,
)
