package com.langxi.babydiary;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.langxi.babydiary", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule API_MUST_NOT_DEPEND_ON_INFRASTRUCTURE = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "org.mybatis..", "jakarta.servlet..");

    @ArchTest
    static final ArchRule CONTROLLERS_STAY_IN_API_PACKAGES = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule MAPPERS_STAY_IN_INFRASTRUCTURE = classes()
            .that().haveSimpleNameEndingWith("Mapper")
            .should().resideInAPackage("..infrastructure..");
}
