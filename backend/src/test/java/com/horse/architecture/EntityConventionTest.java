package com.horse.architecture;

import static com.horse.architecture.ConventionTestSupport.PRODUCTION_CLASSES;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;

class EntityConventionTest {

	@Test
	void 엔티티는_public_setter와_public_생성자를_노출하지_않는다() {
		noMethods().that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
			.and().haveNameMatching("set[A-Z].*")
			.should().bePublic()
			.allowEmptyShould(true)
			.check(PRODUCTION_CLASSES);

		constructors().that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
			.should().notBePublic()
			.check(PRODUCTION_CLASSES);
	}

	@Test
	void 엔티티_팩토리_메서드는_static이다() {
		methods().that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
			.and().haveNameMatching("create|of|from")
			.should().beStatic()
			.check(PRODUCTION_CLASSES);
	}

}
