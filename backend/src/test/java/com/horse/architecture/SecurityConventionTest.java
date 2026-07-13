package com.horse.architecture;

import static com.horse.architecture.ConventionTestSupport.PRODUCTION_CLASSES;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

class SecurityConventionTest {

	@Test
	void 서비스는_보안_컨텍스트에_직접_의존하지_않는다() {
		noClasses().that().haveSimpleNameEndingWith("Service")
			.should().dependOnClassesThat().resideInAPackage("org.springframework.security.core.context..")
			.allowEmptyShould(true)
			.check(PRODUCTION_CLASSES);
	}

}
