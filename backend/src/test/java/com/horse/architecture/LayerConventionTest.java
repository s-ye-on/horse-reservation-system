package com.horse.architecture;

import static com.horse.architecture.ConventionTestSupport.PRODUCTION_CLASSES;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

class LayerConventionTest {

	@Test
	void 컨트롤러는_도메인과_리포지토리에_직접_의존하지_않는다() {
		noClasses().that().haveSimpleNameEndingWith("Controller")
			.should().dependOnClassesThat().resideInAnyPackage("..domain..", "..infrastructure..")
			.allowEmptyShould(true)
			.check(PRODUCTION_CLASSES);
	}

	@Test
	void 도메인은_상위_계층과_인프라스트럭처에_의존하지_않는다() {
		noClasses().that().resideInAPackage("..domain..")
			.should().dependOnClassesThat()
			.resideInAnyPackage("..presentation..", "..application..", "..infrastructure..")
			.check(PRODUCTION_CLASSES);
	}

	@Test
	void 계층별_타입은_정해진_패키지에_위치한다() {
		classes().that().haveSimpleNameEndingWith("Controller")
			.should().resideInAPackage("..presentation..")
			.allowEmptyShould(true)
			.check(PRODUCTION_CLASSES);

		classes().that().haveSimpleNameEndingWith("Service")
			.should().resideInAPackage("..application..")
			.allowEmptyShould(true)
			.check(PRODUCTION_CLASSES);

		classes().that().haveSimpleNameEndingWith("Repository")
			.should().resideInAPackage("..infrastructure..")
			.check(PRODUCTION_CLASSES);
	}

}
