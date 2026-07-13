package com.horse.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.horse.global.exception.BusinessException;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import jakarta.persistence.Entity;

class ArchitectureConventionTest {

	private static final JavaClasses CLASSES = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("com.horse");

	@Test
	void 컨트롤러는_도메인과_리포지토리에_직접_의존하지_않는다() {
		noClasses().that().haveSimpleNameEndingWith("Controller")
			.should().dependOnClassesThat().resideInAnyPackage("..domain..", "..infrastructure..")
			.allowEmptyShould(true)
			.check(CLASSES);
	}

	@Test
	void 서비스는_보안_컨텍스트에_직접_의존하지_않는다() {
		noClasses().that().haveSimpleNameEndingWith("Service")
			.should().dependOnClassesThat().resideInAPackage("org.springframework.security.core.context..")
			.allowEmptyShould(true)
			.check(CLASSES);
	}

	@Test
	void 도메인은_상위_계층과_인프라스트럭처에_의존하지_않는다() {
		noClasses().that().resideInAPackage("..domain..")
			.should().dependOnClassesThat()
			.resideInAnyPackage("..presentation..", "..application..", "..infrastructure..")
			.check(CLASSES);
	}

	@Test
	void 엔티티는_public_setter와_public_생성자를_노출하지_않는다() {
		noMethods().that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
			.and().haveNameMatching("set[A-Z].*")
			.should().bePublic()
			.allowEmptyShould(true)
			.check(CLASSES);

		constructors().that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
			.should().notBePublic()
			.check(CLASSES);
	}

	@Test
	void 엔티티_팩토리_메서드는_static이다() {
		methods().that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
			.and().haveNameMatching("create|of|from")
			.should().beStatic()
			.check(CLASSES);
	}

	@Test
	void 업무_예외는_BusinessException을_상속한다() {
		classes().that().haveSimpleNameEndingWith("Exception")
			.and().doNotHaveSimpleName("BusinessException")
			.should().beAssignableTo(BusinessException.class)
			.allowEmptyShould(true)
			.check(CLASSES);

		assertThat(BusinessException.class.getSuperclass()).isEqualTo(RuntimeException.class);
	}

	@Test
	void 계층별_타입은_정해진_패키지에_위치한다() {
		classes().that().haveSimpleNameEndingWith("Controller")
			.should().resideInAPackage("..presentation..")
			.allowEmptyShould(true)
			.check(CLASSES);

		classes().that().haveSimpleNameEndingWith("Service")
			.should().resideInAPackage("..application..")
			.allowEmptyShould(true)
			.check(CLASSES);

		classes().that().haveSimpleNameEndingWith("Repository")
			.should().resideInAPackage("..infrastructure..")
			.check(CLASSES);
	}

	@Test
	void 오류_응답은_전역_예외_처리기만_생성한다() {
		noClasses().that().doNotHaveSimpleName("ErrorResponse")
			.and().doNotHaveSimpleName("GlobalExceptionHandler")
			.should().dependOnClassesThat().haveSimpleName("ErrorResponse")
			.check(CLASSES);
	}

	@Test
	void 요청과_응답_DTO는_record이다() {
		classes().that().haveSimpleNameEndingWith("Request")
			.or().haveSimpleNameEndingWith("Response")
			.should().beRecords()
			.allowEmptyShould(true)
			.check(CLASSES);
	}

}
