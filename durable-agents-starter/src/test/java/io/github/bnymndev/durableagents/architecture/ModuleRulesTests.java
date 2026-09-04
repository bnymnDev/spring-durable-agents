package io.github.bnymndev.durableagents.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Module rules, run with {@code mvn -pl durable-agents-starter test -Dtest=ModuleRulesTests} (or the
 * whole build). The classpath of the starter contains every module, so one place checks them all.
 */
@AnalyzeClasses(packages = "io.github.bnymndev.durableagents", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleRulesTests {

	private static final String ROOT = "io.github.bnymndev.durableagents";

	private static final String[] CORE = { ROOT, ROOT + ".spi", ROOT + ".event", ROOT + ".internal", ROOT + ".internal.store" };

	@ArchTest
	static final ArchRule publicApiDoesNotDependOnInternals = noClasses()
		.that().resideInAPackage(ROOT)
		.should().dependOnClassesThat().resideInAnyPackage(ROOT + ".internal..", ROOT + ".autoconfigure..", ROOT + ".jdbc..",
				ROOT + ".springai..", ROOT + ".approval..", ROOT + ".actuator..");

	@ArchTest
	static final ArchRule coreHasNoSpringAiJacksonOrMicrometerDependency = noClasses()
		.that().resideInAnyPackage(CORE)
		.should().dependOnClassesThat().resideInAnyPackage("org.springframework.ai..", "tools.jackson..",
				"com.fasterxml.jackson..", "io.micrometer.core..", "io.micrometer.observation..", "org.springframework.boot..",
				"org.springframework.jdbc..", "org.springframework.web..", "org.springframework.security..");

	@ArchTest
	static final ArchRule onlyTheAccessorTouchesContextPropagation = noClasses()
		.that().resideInAnyPackage(CORE).and().doNotHaveSimpleName("StepsThreadLocalAccessor")
		.should().dependOnClassesThat().resideInAPackage("io.micrometer.context..");

	@ArchTest
	static final ArchRule coreDoesNotDependOnOtherModules = noClasses()
		.that().resideInAnyPackage(CORE)
		.should().dependOnClassesThat().resideInAnyPackage(ROOT + ".jdbc..", ROOT + ".springai..", ROOT + ".approval..",
				ROOT + ".actuator..", ROOT + ".autoconfigure..", ROOT + ".test..");

	@ArchTest
	static final ArchRule modulesDoNotDependOnAutoconfiguration = noClasses()
		.that().resideInAnyPackage(ROOT + ".jdbc..", ROOT + ".springai..", ROOT + ".approval..", ROOT + ".actuator..")
		.should().dependOnClassesThat().resideInAnyPackage(ROOT + ".autoconfigure..", ROOT + ".test..");

	@ArchTest
	static final ArchRule springAiModuleIsIsolated = noClasses()
		.that().resideInAPackage(ROOT + ".springai..")
		.should().dependOnClassesThat().resideInAnyPackage(ROOT + ".jdbc..", ROOT + ".approval..", ROOT + ".actuator..");

	@ArchTest
	static final ArchRule approvalModuleIsIsolated = noClasses()
		.that().resideInAPackage(ROOT + ".approval..")
		.should().dependOnClassesThat().resideInAnyPackage(ROOT + ".jdbc..", ROOT + ".springai..", ROOT + ".actuator..");

	@ArchTest
	static final ArchRule actuatorModuleIsIsolated = noClasses()
		.that().resideInAPackage(ROOT + ".actuator..")
		.should().dependOnClassesThat().resideInAnyPackage(ROOT + ".jdbc..", ROOT + ".springai..", ROOT + ".approval..");

	@ArchTest
	static final ArchRule storeModuleDoesNotDependOnSpringAiOrWeb = noClasses()
		.that().resideInAPackage(ROOT + ".jdbc..")
		.should().dependOnClassesThat().resideInAnyPackage(ROOT + ".springai..", ROOT + ".approval..", ROOT + ".actuator..",
				"org.springframework.ai..", "org.springframework.web..");

	@ArchTest
	static final ArchRule noFieldInjection = noClasses()
		.that().resideInAnyPackage(ROOT + "..")
		.should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.beans.factory.annotation.Autowired");

}
