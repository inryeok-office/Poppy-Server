package team.inreok.poppyserver.domain.execution.application

import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import team.inreok.poppyserver.domain.robot.application.RobotRepository

class ExecutionAllocationServiceContextTest {
    @Test
    fun `spring datasource url 없이 필요한 bean이 있으면 allocation service를 등록한다`() {
        ApplicationContextRunner()
            .withUserConfiguration(RequiredBeansConfiguration::class.java, ExecutionAllocationService::class.java)
            .run { context ->
                assertThat(context.environment.containsProperty("spring.datasource.url")).isFalse()
                assertThat(context).hasSingleBean(ExecutionAllocationService::class.java)
            }
    }

    @Test
    fun `필요한 repository bean이 없으면 allocation service를 등록하지 않는다`() {
        ApplicationContextRunner()
            .withUserConfiguration(ExecutionAllocationService::class.java)
            .run { context ->
                assertThat(context).doesNotHaveBean(ExecutionAllocationService::class.java)
            }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class RequiredBeansConfiguration {
        @Bean
        fun dataSource(): DataSource = mock(DataSource::class.java)

        @Bean
        fun executionRepository(): ExecutionRepository = mock(ExecutionRepository::class.java)

        @Bean
        fun robotRepository(): RobotRepository = mock(RobotRepository::class.java)
    }
}
