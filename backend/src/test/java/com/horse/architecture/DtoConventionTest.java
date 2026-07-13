package com.horse.architecture;

import static com.horse.architecture.ConventionTestSupport.PRODUCTION_CLASSES;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import org.junit.jupiter.api.Test;

class DtoConventionTest {

	@Test
	void 요청과_응답_DTO는_record이다() {
		classes().that().haveSimpleNameEndingWith("Request")
			.or().haveSimpleNameEndingWith("Response")
			.should().beRecords()
			.allowEmptyShould(true)
			.check(PRODUCTION_CLASSES);
	}

}
