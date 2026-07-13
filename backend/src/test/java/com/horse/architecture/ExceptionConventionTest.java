package com.horse.architecture;

import static com.horse.architecture.ConventionTestSupport.PRODUCTION_CLASSES;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.horse.global.exception.BusinessException;

class ExceptionConventionTest {

	@Test
	void 업무_예외는_BusinessException을_상속한다() {
		classes().that().haveSimpleNameEndingWith("Exception")
			.and().doNotHaveSimpleName("BusinessException")
			.should().beAssignableTo(BusinessException.class)
			.allowEmptyShould(true)
			.check(PRODUCTION_CLASSES);

		assertThat(BusinessException.class.getSuperclass()).isEqualTo(RuntimeException.class);
	}

	@Test
	void 컨트롤러는_오류_응답에_직접_의존하지_않는다() {
		noClasses().that().haveSimpleNameEndingWith("Controller")
			.should().dependOnClassesThat().haveSimpleName("ErrorResponse")
			.allowEmptyShould(true)
			.check(PRODUCTION_CLASSES);
	}

}
