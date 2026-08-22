package team.inreok.poppyserver.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import org.junit.jupiter.api.Test

class ArchitectureTest {

    private val basePackage = "team.inreok.poppyserver"
    private val importedClasses = ClassFileImporter().importPackages(basePackage)

    @Test
    fun `global 패키지는 domain 패키지를 참조하지 않는다`() {
        noClasses()
            .that().resideInAPackage("$basePackage.global..")
            .should().dependOnClassesThat().resideInAPackage("$basePackage.domain..")
            .allowEmptyShould(true)
            .check(importedClasses)
    }

    @Test
    fun `domain 계층은 정의된 의존 방향을 지킨다`() {
        layeredArchitecture()
            .consideringAllDependencies()
            .layer("Presentation").definedBy("$basePackage.domain.*.presentation..")
            .layer("Application").definedBy("$basePackage.domain.*.application..")
            .layer("Model").definedBy("$basePackage.domain.*.model..")
            .layer("Infrastructure").definedBy("$basePackage.domain.*.infrastructure..")
            .withOptionalLayers(true)
            .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Presentation", "Infrastructure")
            .whereLayer("Model").mayOnlyBeAccessedByLayers("Presentation", "Application", "Infrastructure")
            .check(importedClasses)
    }
}
