/*
 * Copyright 2002-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.transaction.annotation;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;


import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.aot.test.generate.TestGenerationContext;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link TransactionBeanRegistrationAotProcessor}.
 *
 * @author Sebastien Deleuze
 */
class TransactionBeanRegistrationAotProcessorTests {

	private final TransactionBeanRegistrationAotProcessor processor = new TransactionBeanRegistrationAotProcessor();

	private final GenerationContext generationContext = new TestGenerationContext();

		void shouldSkipNonAnnotatedType() {
		process(NonAnnotatedBean.class);
		assertThat(this.generationContext.getRuntimeHints().reflection().typeHints()).isEmpty();
	}

		void shouldSkipAnnotatedTypeWithNoInterface() {
		process(NoInterfaceBean.class);
		assertThat(this.generationContext.getRuntimeHints().reflection().typeHints()).isEmpty();
	}

		void shouldProcessTransactionalOnClass() {
		process(TransactionalOnTypeBean.class);
		assertThat(RuntimeHintsPredicates.reflection().onType(NonAnnotatedTransactionalInterface.class)
				.withMemberCategory(MemberCategory.INVOKE_DECLARED_METHODS)).accepts(this.generationContext.getRuntimeHints());
	}

		void shouldProcessJakartaTransactionalOnClass() {
		process(JakartaTransactionalOnTypeBean.class);
		assertThat(RuntimeHintsPredicates.reflection().onType(NonAnnotatedTransactionalInterface.class)
				.withMemberCategory(MemberCategory.INVOKE_DECLARED_METHODS)).accepts(this.generationContext.getRuntimeHints());
	}

		void shouldProcessTransactionalOnInterface() {
		process(TransactionalOnTypeInterface.class);
		assertThat(RuntimeHintsPredicates.reflection().onType(TransactionalOnTypeInterface.class)
				.withMemberCategory(MemberCategory.INVOKE_DECLARED_METHODS)).accepts(this.generationContext.getRuntimeHints());
	}

		void shouldProcessTransactionalOnClassMethod() {
		process(TransactionalOnClassMethodBean.class);
		assertThat(RuntimeHintsPredicates.reflection().onType(NonAnnotatedTransactionalInterface.class)
				.withMemberCategory(MemberCategory.INVOKE_DECLARED_METHODS)).accepts(this.generationContext.getRuntimeHints());
	}

		void shouldProcessTransactionalOnInterfaceMethod() {
		process(TransactionalOnInterfaceMethodBean.class);
		assertThat(RuntimeHintsPredicates.reflection().onType(TransactionalOnMethodInterface.class)
				.withMemberCategory(MemberCategory.INVOKE_DECLARED_METHODS)).accepts(this.generationContext.getRuntimeHints());
	}

	private void process(Class<?> beanClass) {
		BeanRegistrationAotContribution contribution = createContribution(beanClass);
		if (contribution != null) {
			contribution.applyTo(this.generationContext, mock());
		}
	}

	private BeanRegistrationAotContribution createContribution(Class<?> beanClass) {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition(beanClass.getName(), new RootBeanDefinition(beanClass));
		return this.processor.processAheadOfTime(RegisteredBean.of(beanFactory, beanClass.getName()));
	}


		static class NonAnnotatedBean {

		public void notTransactional() {
		}
	}

			static class NoInterfaceBean {

		public void notTransactional() {
		}
	}

		static class TransactionalOnTypeBean implements NonAnnotatedTransactionalInterface {

				public void transactional() {
		}
	}

		static class JakartaTransactionalOnTypeBean implements NonAnnotatedTransactionalInterface {

				public void transactional() {
		}
	}

	interface NonAnnotatedTransactionalInterface {

		void transactional();
	}

		interface TransactionalOnTypeInterface {

		void transactional();
	}

	static class TransactionalOnClassMethodBean implements NonAnnotatedTransactionalInterface {

						public void transactional() {
		}
	}

	interface TransactionalOnMethodInterface {

				void transactional();
	}

	static class TransactionalOnInterfaceMethodBean implements TransactionalOnMethodInterface {

				public void transactional() {
		}
	}
}
