package team.inreok.poppyserver.domain.ai.infrastructure

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import team.inreok.poppyserver.domain.ai.application.AiHintCommand
import team.inreok.poppyserver.domain.ai.application.AiInferenceClient
import team.inreok.poppyserver.domain.ai.application.AiInferenceDraft
import team.inreok.poppyserver.domain.ai.application.AiInferenceUnavailableException
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

@Component
class HttpAiInferenceClient(
    private val objectMapper: ObjectMapper,
    @Value("\${poppy.ai.inference-url:}") inferenceUrl: String,
    @Value("\${poppy.ai.connect-timeout:PT1S}") connectTimeout: Duration,
    @Value("\${poppy.ai.request-timeout:PT4S}") private val requestTimeout: Duration,
) : AiInferenceClient {
    private val baseUrl = inferenceUrl.trim().trimEnd('/')
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(connectTimeout)
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    override fun requestProgramDraft(utterance: String): AiInferenceDraft {
        val body = post("/v1/program-drafts", mapOf("utterance" to utterance))
        return AiInferenceDraft(steps = body.get("steps"), explanation = body.get("explanation")?.takeIf { it.isString }?.asString())
    }

    override fun requestHint(request: AiHintCommand): String {
        val body = post(
            "/v1/hints",
            mapOf(
                "programLines" to request.programLines,
                "violationCode" to request.violationCode,
                "violationMessage" to request.violationMessage,
                "violationBlockNumber" to request.violationBlockNumber,
            ),
        )
        return body.get("message")?.takeIf { it.isString }?.asString()
            ?: throw AiInferenceUnavailableException("hint message missing")
    }

    private fun post(path: String, payload: Any): JsonNode {
        if (baseUrl.isEmpty()) throw AiInferenceUnavailableException("inference url is not configured")
        val request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build()
        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (exception: HttpTimeoutException) {
            throw AiInferenceUnavailableException("inference timed out", exception)
        } catch (exception: IOException) {
            throw AiInferenceUnavailableException("inference connection failed", exception)
        } catch (exception: IllegalArgumentException) {
            throw AiInferenceUnavailableException("inference url is invalid", exception)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw AiInferenceUnavailableException("inference interrupted", exception)
        }
        if (response.statusCode() != 200) {
            throw AiInferenceUnavailableException("inference returned ${response.statusCode()}")
        }
        return try {
            objectMapper.readTree(response.body())
        } catch (exception: JacksonException) {
            throw AiInferenceUnavailableException("inference returned invalid json", exception)
        }
    }
}
