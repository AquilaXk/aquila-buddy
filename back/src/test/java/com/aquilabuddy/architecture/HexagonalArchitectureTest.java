package com.aquilabuddy.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HexagonalArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.aquilabuddy");

    @Test
    void inboundWebAdaptersDependOnlyOnUseCasePortsDomainAndResponseTypes() {
        classes()
            .that().resideInAPackage("..adapter.in.web..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                "java..",
                "org.springframework..",
                "com.aquilabuddy.adapter.in.web..",
                "com.aquilabuddy.application.port.in..",
                "com.aquilabuddy.domain..",
                "com.aquilabuddy.global.response.."
            )
            .check(classes);
    }

    @Test
    void applicationServicesDependOnlyOnPortsAndDomain() {
        classes()
            .that().resideInAPackage("..application.service..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                "java..",
                "org.springframework..",
                "com.aquilabuddy.application.service..",
                "com.aquilabuddy.application.port.in..",
                "com.aquilabuddy.domain.."
            )
            .check(classes);
    }

    @Test
    void movedDomainPackagesDoNotDependOnSpring() {
        noClasses()
            .that().resideInAnyPackage(
                "..domain.analysis..",
                "..domain.health..",
                "..domain.issue.."
            )
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.."
            )
            .check(classes);
    }

    @Test
    void legacyGlobalFeaturePackagesStayEmptyAfterMove() {
        Set<String> legacyPackages = classes.stream()
            .map(javaClass -> javaClass.getPackage().getName())
            .filter(packageName -> packageName.startsWith("com.aquilabuddy.global.analysis")
                || packageName.startsWith("com.aquilabuddy.global.health")
                || packageName.startsWith("com.aquilabuddy.global.issue"))
            .collect(Collectors.toSet());

        assertThat(legacyPackages).isEmpty();
    }
}
