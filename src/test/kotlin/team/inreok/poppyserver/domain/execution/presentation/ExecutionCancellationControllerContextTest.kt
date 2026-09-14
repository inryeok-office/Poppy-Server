package team.inreok.poppyserver.domain.execution.presentation

import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import team.inreok.poppyserver.domain.execution.application.ExecutionCancellationService

class ExecutionCancellationControllerContextTest {
    @Test
    fun `cancellation service가 없으면 controller를 등록하지 않는다`() {
        ApplicationContextRunner()
            .withPropertyValues("spring.datasource.url=jdbc:test")
            .withUserConfiguration(ExecutionCancellationController::class.java)
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(ExecutionCancellationController::class.java)
            }
    }

    @Test
    fun `cancellation service가 있으면 controller를 등록한다`() {
        ApplicationContextRunner()
            .withPropertyValues("spring.datasource.url=jdbc:test")
            .withUserConfiguration(RequiredBeansConfiguration::class.java, ExecutionCancellationController::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(ExecutionCancellationController::class.java)
            }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class RequiredBeansConfiguration {
        @Bean
        fun dataSource(): DataSource = mock(DataSource::class.java)

        @Bean
        fun executionCancellationService(): ExecutionCancellationService = mock(ExecutionCancellationService::class.java)
    }
}
