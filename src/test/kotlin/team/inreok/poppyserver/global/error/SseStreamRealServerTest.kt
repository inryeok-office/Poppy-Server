package team.inreok.poppyserver.global.error

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 실제 내장 Tomcat 으로 확인한다. SSE 응답은 시작할 때 헤더가 이미 내보내져(committed) Content-Type 이
 * text/event-stream 으로 굳는다. 그 뒤(타임아웃·연결 끊김) 공통 에러 핸들러가 JSON 을 쓰려 하면
 * `No converter for ApiResponse with preset Content-Type 'text/event-stream'` WARN 이 남는다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration",
    ],
)
class SseStreamRealServerTest {

    @LocalServerPort
    var port: Int = 0

    private val resolverLogger =
        LoggerFactory.getLogger("org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver") as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun attachAppender() {
        appender.start()
        resolverLogger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        resolverLogger.detachAppender(appender)
        appender.stop()
    }

    private fun resolverWarnings(): List<String> =
        appender.list.filter { it.level.isGreaterOrEqual(Level.WARN) }.map { it.formattedMessage }

    @Test
    fun `sse timeout on a real server does not log a handler failure`() {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("http://localhost:$port/test/sse-real/timeout"))
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE).build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(200, response.statusCode())
        assertTrue(
            response.headers().firstValue("Content-Type").orElse("").startsWith(MediaType.TEXT_EVENT_STREAM_VALUE),
        )
        assertTrue(!response.body().contains("INTERNAL_SERVER_ERROR"), "body: ${response.body()}")
        assertTrue(resolverWarnings().isEmpty(), "resolver warnings: ${resolverWarnings()}")
    }

    @Test
    fun `error before the stream starts is still a json error even when the client accepts event-stream`() {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("http://localhost:$port/test/sse-real/denied"))
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE).build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(400, response.statusCode())
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith(MediaType.APPLICATION_JSON_VALUE))
        assertTrue(response.body().contains(ErrorCode.INVALID_INPUT.code), "body: ${response.body()}")
        assertTrue(resolverWarnings().isEmpty(), "resolver warnings: ${resolverWarnings()}")
    }

    @Test
    fun `client disconnect on a real server does not log a handler failure`() {
        SseStreamRealServerTestController.finished = false
        val client = HttpClient.newHttpClient()
        val response = client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/test/sse-real/ping"))
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE).build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
        assertEquals(200, response.statusCode())
        response.body().read() // 첫 이벤트를 받은 뒤
        response.body().close() // 연결을 끊는다

        // 서버가 끊긴 연결에 계속 쓰다 실패할 때까지 기다린다.
        val deadline = System.currentTimeMillis() + 5_000
        while (!SseStreamRealServerTestController.finished && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        Thread.sleep(300)

        assertTrue(SseStreamRealServerTestController.finished, "server never noticed the disconnect")
        assertTrue(resolverWarnings().isEmpty(), "resolver warnings: ${resolverWarnings()}")
    }
}

@RestController
@RequestMapping("/test/sse-real")
class SseStreamRealServerTestController {

    /** 헤더가 내보내진 뒤 300 ms 만에 SSE 타임아웃이 난다. */
    @GetMapping("/timeout", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun timeout(): SseEmitter = SseEmitter(300L).also { it.send(SseEmitter.event().name("hello").data("hi")) }

    /** 스트림이 시작되기 전의 오류: 공통 JSON 에러 포맷이어야 한다. */
    @GetMapping("/denied", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun denied(): SseEmitter = throw ApplicationException(ErrorCode.INVALID_INPUT)

    /** 연결이 끊길 때까지 주기적으로 이벤트를 보낸다. */
    @GetMapping("/ping", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun ping(): SseEmitter {
        val emitter = SseEmitter(60_000L)
        emitter.onCompletion { finished = true }
        emitter.onError { finished = true }
        Thread {
            try {
                repeat(400) {
                    emitter.send(SseEmitter.event().name("ping").data("x"))
                    Thread.sleep(25)
                }
            } catch (_: Exception) {
                finished = true
            }
        }.also { it.isDaemon = true }.start()
        return emitter
    }

    companion object {
        @Volatile
        var finished = false
    }
}
