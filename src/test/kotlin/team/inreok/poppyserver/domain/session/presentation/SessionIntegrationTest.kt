package team.inreok.poppyserver.domain.session.presentation

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.dao.DataAccessException
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.inreok.poppyserver.domain.session.application.BlockRevisionRepository
import team.inreok.poppyserver.domain.session.application.SessionRepository
import team.inreok.poppyserver.domain.session.application.SessionService
import team.inreok.poppyserver.domain.session.model.BlockRevision
import team.inreok.poppyserver.infrastructure.PostgresIntegrationTest
import team.inreok.poppyserver.support.validBlockProgram
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class SessionIntegrationTest : PostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sessionService: SessionService

    @Autowired
    lateinit var sessionRepository: SessionRepository

    @Autowired
    lateinit var blockRevisionRepository: BlockRevisionRepository

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `Session 생성은 currentBlockVersion 0을 반환한다`() {
        mockMvc.perform(
            post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.sessionId").isNotEmpty)
            .andExpect(jsonPath("$.data.sessionToken").isNotEmpty)
            .andExpect(jsonPath("$.data.currentBlockVersion").value(0))
            .andExpect(jsonPath("$.error").value(null))
    }

    @Test
    fun `Block Revision은 Session별로 1부터 증가하고 JSON snapshot을 보존한다`() {
        val session = sessionService.createSession()
        val firstDocument = validBlockProgram("first")
        val secondDocument = validBlockProgram("second")

        val first = sessionService.appendBlockRevision(session.sessionId, firstDocument)
        val second = sessionService.appendBlockRevision(session.sessionId, secondDocument)

        assertEquals(1, first.blockVersion)
        assertEquals(2, second.blockVersion)
        assertEquals(2, sessionRepository.findById(session.sessionId)?.currentBlockVersion)
        assertEquals(firstDocument, blockRevisionRepository.findById(session.sessionId, 1)?.document)
        assertEquals(secondDocument, blockRevisionRepository.findById(session.sessionId, 2)?.document)
    }

    @Test
    fun `이전 Revision은 변경되지 않고 Session별 version은 독립적이다`() {
        val firstSession = sessionService.createSession()
        val secondSession = sessionService.createSession()

        val firstDocument = validBlockProgram("first")
        val secondDocument = validBlockProgram("second")
        val thirdDocument = validBlockProgram("third")
        sessionService.appendBlockRevision(firstSession.sessionId, firstDocument)
        sessionService.appendBlockRevision(firstSession.sessionId, secondDocument)
        sessionService.appendBlockRevision(secondSession.sessionId, thirdDocument)

        assertEquals(firstDocument, blockRevisionRepository.findById(firstSession.sessionId, 1)?.document)
        assertEquals(2, sessionRepository.findById(firstSession.sessionId)?.currentBlockVersion)
        assertEquals(1, sessionRepository.findById(secondSession.sessionId)?.currentBlockVersion)
        assertEquals(thirdDocument, blockRevisionRepository.findById(secondSession.sessionId, 1)?.document)
    }

    @Test
    fun `존재하지 않는 Session의 Revision 요청은 SESSION_NOT_FOUND를 반환한다`() {
        mockMvc.perform(
            post("/api/v1/sessions/${UUID.randomUUID()}/block-revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"document\":{\"blocks\":[]}}"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"))
    }

    @Test
    fun `잘못된 sessionId 형식은 COMMON_400으로 응답한다`() {
        mockMvc.perform(
            post("/api/v1/sessions/not-a-uuid/block-revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").value(null))
            .andExpect(jsonPath("$.error.code").value("COMMON_400"))
            .andExpect(jsonPath("$.error.fieldErrors[0].field").value("sessionId"))
    }

    @Test
    fun `Block Revision HTTP API는 opaque JSON을 저장하고 version을 반환한다`() {
        val session = sessionService.createSession()
        val document = validBlockProgram("http")

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/block-revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Session-Token", session.sessionToken)
                .content("{\"document\":$document}"),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.sessionId").value(session.sessionId.toString()))
            .andExpect(jsonPath("$.data.blockVersion").value(1))
            .andExpect(jsonPath("$.error").value(null))

        assertEquals(document, blockRevisionRepository.findById(session.sessionId, 1)?.document)
    }

    @Test
    fun `malformed 또는 document 누락 요청은 기존 validation error를 사용한다`() {
        val session = sessionService.createSession()

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/block-revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"document\":}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_400"))

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/block-revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Session-Token", session.sessionToken)
                .content("{\"document\":{\"schemaVersion\":1,\"blocks\":[{\"id\":\"start\",\"type\":\"START\",\"parameters\":{}},{\"id\":\"start-2\",\"type\":\"START\",\"parameters\":{}},{\"id\":\"end\",\"type\":\"END\",\"parameters\":{}}]}}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("BLOCK_PROGRAM_INVALID"))
            .andExpect(jsonPath("$.error.fieldErrors[0].field").value("blocks[1]"))
        assertEquals(0, sessionRepository.findById(session.sessionId)?.currentBlockVersion)
        assertNull(blockRevisionRepository.findById(session.sessionId, 1))

        mockMvc.perform(
            post("/api/v1/sessions/${session.sessionId}/block-revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("COMMON_400"))
    }

    @Test
    fun `동시 Revision 추가는 Session lock으로 1과 2를 중복 없이 할당한다`() {
        val session = sessionService.createSession()
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val futures = (1..2).map { index ->
                executor.submit<Long> {
                    ready.countDown()
                    assertTrue(start.await(10, TimeUnit.SECONDS))
                    sessionService.appendBlockRevision(session.sessionId, validBlockProgram(index.toString())).blockVersion
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            val versions = futures.map { it.get(30, TimeUnit.SECONDS) }.toSet()

            assertEquals(setOf(1L, 2L), versions)
            assertEquals(2, sessionRepository.findById(session.sessionId)?.currentBlockVersion)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `Revision 저장 실패 시 Session version도 함께 rollback된다`() {
        val session = sessionService.createSession()
        val transactionTemplate = TransactionTemplate(transactionManager)

        assertFailsWith<IllegalStateException> {
            transactionTemplate.execute {
                val locked = sessionRepository.findByIdForUpdate(session.sessionId)!!
                val version = locked.advanceBlockVersion()
                blockRevisionRepository.save(BlockRevision.create(session.sessionId, version, "{\"rollback\":true}"))
                sessionRepository.save(locked)
                throw IllegalStateException("rollback")
            }
        }

        assertEquals(0, sessionRepository.findById(session.sessionId)?.currentBlockVersion)
        assertNull(blockRevisionRepository.findById(session.sessionId, 1))
    }

    @Test
    fun `같은 Session과 version의 Revision은 immutable하게 거부된다`() {
        val session = sessionService.createSession()
        val document = validBlockProgram("immutable")
        sessionService.appendBlockRevision(session.sessionId, document)

        assertFailsWith<DataAccessException> {
            blockRevisionRepository.save(
                BlockRevision.create(session.sessionId, 1, "{\"value\":2}"),
            )
        }
        assertEquals(document, blockRevisionRepository.findById(session.sessionId, 1)?.document)
    }

    @Test
    fun `DB는 Session과 version 조합을 unique하게 보장한다`() {
        val session = sessionService.createSession()
        val document = validBlockProgram("unique")
        sessionService.appendBlockRevision(session.sessionId, document)
        val transactionTemplate = TransactionTemplate(transactionManager)

        assertFailsWith<DataAccessException> {
            transactionTemplate.executeWithoutResult {
                jdbcTemplate.update(
                    """
                    INSERT INTO block_revisions (session_id, version, document, created_at)
                    VALUES (?, ?, ?::jsonb, ?)
                    """.trimIndent(),
                    session.sessionId,
                    1L,
                    "{\"value\":2}",
                    java.time.Instant.now(),
                )
            }
        }
        assertEquals(document, blockRevisionRepository.findById(session.sessionId, 1)?.document)
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.jpa.properties.hibernate.jdbc.time_zone") { "UTC" }
        }
    }
}
