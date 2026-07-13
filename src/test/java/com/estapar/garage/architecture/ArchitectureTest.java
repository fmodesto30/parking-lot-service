package com.estapar.garage.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Enforces the hexagonal boundaries (ADR-001): the domain is framework-free, dependencies point
 * inward (application never reaches into infrastructure), and controllers go through use cases
 * rather than touching persistence directly.
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.estapar.garage");

    @Test
    void domainIsFreeOfFrameworkDependencies() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "com.fasterxml.jackson..",
                        "io.micrometer..",
                        "org.hibernate..")
                .because("the domain must not know about frameworks, ORM or serialization (ADR-001)");

        rule.check(CLASSES);
    }

    @Test
    void applicationDoesNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .because("use cases depend on ports, not on adapters (dependency inversion)");

        rule.check(CLASSES);
    }

    @Test
    void domainDoesNotDependOnApplicationOrInfrastructure() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..application..", "..infrastructure..")
                .because("dependencies point inward: the domain is the innermost layer");

        rule.check(CLASSES);
    }

    @Test
    void controllersDoNotAccessRepositoriesDirectly() {
        ArchRule rule = noClasses()
                .that()
                .haveSimpleNameEndingWith("Controller")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Repository")
                .because("controllers must go through use cases, not persistence");

        rule.check(CLASSES);
    }

    @Test
    void inboundAdaptersDoNotDependOnOutboundAdapters() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..infrastructure.inbound..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..infrastructure.outbound..")
                .because("the web layer talks to use cases, not to persistence/simulator adapters");

        rule.check(CLASSES);
    }

    @Test
    void jpaEntitiesStayInPersistenceAdapters() {
        ArchRule rule = classes()
                .that()
                .areAnnotatedWith(jakarta.persistence.Entity.class)
                .should()
                .resideInAPackage("..infrastructure.outbound.persistence..")
                .because("JPA entities are a persistence detail, never a domain or API type");

        rule.check(CLASSES);
    }
}
