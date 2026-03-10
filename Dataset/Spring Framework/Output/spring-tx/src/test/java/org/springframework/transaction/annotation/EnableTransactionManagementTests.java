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


import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.config.TransactionManagementConfigUtils;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.interceptor.MethodRollbackEvent;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.testfixture.CallCountingTransactionManager;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.transaction.annotation.RollbackOn.ALL_EXCEPTIONS;

/**
 * Tests demonstrating use of @EnableTransactionManagement @Configuration classes.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @author Sam Brannen
 * @author Yanming Zhou
 * @since 3.1
 */
class EnableTransactionManagementTests {

		void transactionProxyIsCreated() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
				EnableTxConfig.class, TxManagerConfig.class);
		TransactionalTestBean bean = ctx.getBean(TransactionalTestBean.class);
		assertThat(AopUtils.isAopProxy(bean)).as("testBean is not a proxy").isTrue();
		Map<?,?> services = ctx.getBeansWithAnnotation(Service.class);
		assertThat(services.containsKey("testBean")).as("Stereotype annotation not visible").isTrue();
		ctx.close();
	}

	// gh-31238
	public void cglibProxyClassIsCachedAcrossApplicationContexts() {
		ConfigurableApplicationContext ctx;

		// Round #1
		ctx = new AnnotationConfigApplicationContext(EnableTxConfig.class, TxManagerConfig.class);
		TransactionalTestBean bean1 = ctx.getBean(TransactionalTestBean.class);
		assertThat(AopUtils.isCglibProxy(bean1)).as("testBean #1 is not a CGLIB proxy").isTrue();
		ctx.close();

		// Round #2
		ctx = new AnnotationConfigApplicationContext(EnableTxConfig.class, TxManagerConfig.class);
		TransactionalTestBean bean2 = ctx.getBean(TransactionalTestBean.class);
		assertThat(AopUtils.isCglibProxy(bean2)).as("testBean #2 is not a CGLIB proxy").isTrue();
		ctx.close();

		assertThat(bean1.getClass()).isSameAs(bean2.getClass());
	}

		void transactionProxyIsCreatedWithEnableOnSuperclass() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
				InheritedEnableTxConfig.class, TxManagerConfig.class);
		TransactionalTestBean bean = ctx.getBean(TransactionalTestBean.class);
		assertThat(AopUtils.isAopProxy(bean)).as("testBean is not a proxy").isTrue();
		Map<?,?> services = ctx.getBeansWithAnnotation(Service.class);
		assertThat(services.containsKey("testBean")).as("Stereotype annotation not visible").isTrue();
		ctx.close();
	}

		void transactionProxyIsCreatedWithEnableOnExcludedSuperclass() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
				ParentEnableTxConfig.class, ChildEnableTxConfig.class, TxManagerConfig.class);
		TransactionalTestBean bean = ctx.getBean(TransactionalTestBean.class);
		assertThat(AopUtils.isAopProxy(bean)).as("testBean is not a proxy").isTrue();
		Map<?,?> services = ctx.getBeansWithAnnotation(Service.class);
		assertThat(services.containsKey("testBean")).as("Stereotype annotation not visible").isTrue();
		ctx.close();
	}

		void txManagerIsResolvedOnInvocationOfTransactionalMethod() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
				EnableTxConfig.class, TxManagerConfig.class);
		TransactionalTestBean bean = ctx.getBean(TransactionalTestBean.class);
		CallCountingTransactionManager txManager = ctx.getBean("txManager", CallCountingTransactionManager.class);

		// invoke a transactional method, causing the PlatformTransactionManager bean to be resolved.
		bean.findAllFoos();
		assertThat(txManager.begun).isEqualTo(1);
		assertThat(txManager.commits).isEqualTo(1);
		assertThat(txManager.rollbacks).isEqualTo(0);
		assertThat(txManager.lastDefinition.isReadOnly()).isTrue();
		assertThat(txManager.lastDefinition.getTimeout()).isEqualTo(5);
		assertThat(((TransactionAttribute) txManager.lastDefinition).getLabels()).contains("LABEL");

		ctx.close();
	}

		void txManagerIsResolvedCorrectlyWhenMultipleManagersArePresent() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
				EnableTxConfig.class, MultiTxManagerConfig.class);
		assertThat(ctx.getBeansOfType(TransactionManager.class)).hasSize(2);
		TransactionalTestBean bean = ctx.getBean(TransactionalTestBean.class);
		CallCountingTransactionManager txManager = ctx.getBean("txManager", CallCountingTransactionManager.class);
		CallCountingTransactionManager txManager2 = ctx.getBean("txManager2", CallCountingTransactionManager.class);

		// invoke a transactional method, causing the PlatformTransactionManager bean to be resolved.
		bean.findAllFoos();
		assertThat(txManager.begun).isEqualTo(0);
		assertThat(txManager.commits).isEqualTo(0);
		assertThat(txManager.rollbacks).isEqualTo(0);
		assertThat(txManager2.begun).isEqualTo(1);
		assertThat(txManager2.commits).isEqualTo(1);
		assertThat(txManager2.rollbacks).isEqualTo(0);

		ctx.close();
	}

		void txManagerIsResolvedCorrectlyWhenMultipleManagersArePresentAndOneIsPrimary() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
				EnableTxConfig.class, PrimaryMultiTxManagerConfig.class);
		assertThat(ctx.getBeansOfType(TransactionManager.class)).hasSize(2);
		TransactionalTestBean bean = ctx.getBean(TransactionalTestBean.class);
		CallCountingTransactionManager primary = ctx.getBean("primary", CallCountingTransactionManager.class);
		CallCountingTransactionManager txManager2 = ctx.getBean("txManager2", CallCountingTransactionManager.class);

		// invoke a transactional method, causing the PlatformTransactionManager bean to be resolved.
		bean.findAllFoos();

		assertThat(primary.begun).isEqualTo(1);
		assertThat(primary.commits).isEqualTo(1);
		assertThat(primary.rollbacks).isEqualTo(0);
		assertThat(txManager2.begun).isEqualTo(0);
		assertThat(txManager2.commits).isEqualTo(0);
		assertThat(txManager2.rollbacks).isEqualTo(0);

		ctx.close();
	}

		void txManagerIsResolvedCorrectlyWithTxMgmtConfigurerAndPrimaryPresent() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
				EnableTxConfig.class, PrimaryTxManagerAndTxMgmtConfigurerConfig.class);
		assertThat(ctx.getBeansOfType(TransactionManager.class)).hasSize(2);
		TransactionalTestBean bean = ctx.getBean(TransactionalTestBean.class);
		CallCountingTransactionManager primary = ctx.getBean("primary", CallCountingTransactionManager.class);
		CallCountingTransactionManager annotationDriven = ctx.getBean("annotationDrivenTransactionManager", CallCountingTransactionManager.class);

		// invoke a transactional method, causing the PlatformTransactionManager bean to be resolved.
		bean.findAllFoos();

		assertThat(primary.begun).isEqualTo(0);
		assertThat(primary.commits).isEqualTo(0);
		assertThat(primary.rollbacks).isEqualTo(0);
		assertThat(annotationDriven.begun).isEqualTo(1);
		assertThat(annotationDriven.commits).isEqualTo(1);
		assertThat(annotationDriven.rollbacks).isEqualTo(0);

		ctx.close();
	}

		void txManagerIsResolvedCorrectlyWithSingleTxManagerBeanAndTxMgmtConfigurer() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
				EnableTxConfig.class, SingleTxManagerBeanAndTxMgmtConfigurerConfig.class);
		assertThat(ctx.getBeansOfType(TransactionManager.class)).hasSize(1);
		TransactionalTestBean bean = ctx.getBean(TransactionalTestBean.class);
		CallCountingTransactionManager txManager = ctx.getBean(CallCountingTransactionManager.class);
		SingleTxManagerBeanAndTxMgmtConfigurerConfig config = ctx.getBean(SingleTxManagerBeanAndTxMgmtConfigurerConfig.class);
		CallCountingTransactionManager annotationDriven = config.annotationDriven;

		// invoke a transactional method, causing the PlatformTransactionManager bean to be resolved.
		bean.findAllFoos();

		assertThat(txManager.begun).isEqualTo(0);
		assertThat(txManager.commits).isEqualTo(0);
		assertThat(txManager.rollbacks).isEqualTo(0);
		assertThat(annotationDriven.begun).isEqualTo(1);
		assertThat(annotationDriven.commits).isEqualTo(1);
		assertThat(annotationDriven.rollbacks).isEqualTo(0);

		ctx.close();
	}

	/**
	 * A cheap test just to prove that in ASPECTJ mode, the AnnotationTransactionAspect does indeed
	 * get loaded -- or in this case, attempted to be loaded at which point the test fails.
	 */
		void proxyTypeAspectJCausesRegistrationOfAnnotationTransactionAspect() {
		// should throw CNFE when trying to load AnnotationTransactionAspect.
		// Do you actually have org.springframework.aspects on the classpath?
		assertThatException()
				.isThrownBy(() -> new AnnotationConfigApplicationContext(EnableAspectjTxConfig.class, TxManagerConfig.class))
				.withMessageContaining("AspectJJtaTransactionManagementConfiguration");
	}

		void transactionalEventListenerRegisteredProperly() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(EnableTxConfig.class);
		assertThat(ctx.containsBean(TransactionManagementConfigUtils.TRANSACTIONAL_EVENT_LISTENER_FACTORY_BEAN_NAME)).isTrue();
		assertThat(ctx.getBeansOfType(TransactionalEventListenerFactory.class)).hasSize(1);
		ctx.close();
	}

		void transactionManagerAsManualSingleton() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(ManualSingletonConfig.class);
		TransactionalTestBean bean = ctx.getBean(TransactionalTestBean.class);
		CallCountingTransactionManager txManager = ctx.getBean("qualifiedTransactionManager", CallCountingTransactionManager.class);

		bean.saveQualifiedFoo();
		assertThat(txManager.begun).isEqualTo(1);
		assertThat(txManager.commits).isEqualTo(1);
		assertThat(txManager.rollbacks).isEqualTo(0);

		bean.saveQualifiedFooWithAttributeAlias();
		assertThat(txManager.begun).isEqualTo(2);
		assertThat(txManager.commits).isEqualTo(2);
		assertThat(txManager.rollbacks).isEqualTo(0);

		assertThatExceptionOfType(NoUniqueBeanDefinitionException.class).isThrownBy(bean::findAllFoos);

		ctx.close();
	}

		void transactionManagerViaQualifierAnnotation() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(QualifiedTransactionConfig.class);

		TransactionalTestBean bean = ctx.getBean("testBean", TransactionalTestBean.class);
		TransactionalTestBeanWithNonExistentQualifier beanWithNonExistentQualifier = ctx.getBean(
				"testBeanWithNonExistentQualifier", TransactionalTestBeanWithNonExistentQualifier.class);
		TransactionalTestBeanWithInvalidQualifier beanWithInvalidQualifier = ctx.getBean(
				"testBeanWithInvalidQualifier", TransactionalTestBeanWithInvalidQualifier.class);

		CallCountingTransactionManager qualified = ctx.getBean("qualifiedTransactionManager",
				CallCountingTransactionManager.class);
		CallCountingTransactionManager primary = ctx.getBean("primaryTransactionManager",
				CallCountingTransactionManager.class);

		bean.saveQualifiedFoo();
		assertThat(qualified.begun).isEqualTo(1);
		assertThat(qualified.commits).isEqualTo(1);
		assertThat(qualified.rollbacks).isEqualTo(0);

		bean.saveQualifiedFooWithAttributeAlias();
		assertThat(qualified.begun).isEqualTo(2);
		assertThat(qualified.commits).isEqualTo(2);
		assertThat(qualified.rollbacks).isEqualTo(0);

		bean.findAllFoos();
		assertThat(qualified.begun).isEqualTo(3);
		assertThat(qualified.commits).isEqualTo(3);
		assertThat(qualified.rollbacks).isEqualTo(0);

		beanWithNonExistentQualifier.findAllFoos();
		assertThat(primary.begun).isEqualTo(1);
		assertThat(primary.commits).isEqualTo(1);
		assertThat(primary.rollbacks).isEqualTo(0);

		beanWithInvalidQualifier.findAllFoos();
		assertThat(primary.begun).isEqualTo(2);
		assertThat(primary.commits).isEqualTo(2);
		assertThat(primary.rollbacks).isEqualTo(0);

		// no further access to qualified transaction manager
		assertThat(qualified.begun).isEqualTo(3);
		assertThat(qualified.commits).isEqualTo(3);
		assertThat(qualified.rollbacks).isEqualTo(0);

		ctx.close();
	}

		void spr14322AnnotationOnInterfaceWithInterfaceProxy() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Spr14322ConfigA.class);
		TransactionalTestInterface bean = ctx.getBean(TransactionalTestInterface.class);
		CallCountingTransactionManager txManager = ctx.getBean(CallCountingTransactionManager.class);

		bean.saveFoo();
		bean.saveBar();
		assertThat(txManager.begun).isEqualTo(2);
		assertThat(txManager.commits).isEqualTo(2);
		assertThat(txManager.rollbacks).isEqualTo(0);

		ctx.close();
	}

		void spr14322AnnotationOnInterfaceWithCglibProxy() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Spr14322ConfigB.class);
		TransactionalTestInterface bean = ctx.getBean(TransactionalTestInterface.class);
		CallCountingTransactionManager txManager = ctx.getBean(CallCountingTransactionManager.class);

		bean.saveFoo();
		bean.saveBar();
		assertThat(txManager.begun).isEqualTo(2);
		assertThat(txManager.commits).isEqualTo(2);
		assertThat(txManager.rollbacks).isEqualTo(0);

		ctx.close();
	}

		void gh24502AppliesTransactionFromAnnotatedInterface() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Gh24502Config.class);
		Object bean = ctx.getBean("testBean");
		CallCountingTransactionManager txManager = ctx.getBean(CallCountingTransactionManager.class);

		((TransactionalInterface) bean).methodOne();
		((NonTransactionalInterface) bean).methodTwo();
		assertThat(txManager.begun).isEqualTo(2);
		assertThat(txManager.commits).isEqualTo(2);
		assertThat(txManager.rollbacks).isEqualTo(0);

		ctx.close();
	}

		void gh23473AppliesToRuntimeExceptionOnly() throws Exception {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(Gh23473ConfigA.class);
		MethodRollbackEventListener listener = new MethodRollbackEventListener();
		ctx.addApplicationListener(listener);
		ctx.refresh();
		TestServiceWithRollback bean = ctx.getBean("testBean", TestServiceWithRollback.class);
		CallCountingTransactionManager txManager = ctx.getBean(CallCountingTransactionManager.class);

		assertThatException().isThrownBy(bean::methodOne);
		assertThatException().isThrownBy(bean::methodTwo);
		assertThat(txManager.begun).isEqualTo(2);
		assertThat(txManager.commits).isEqualTo(2);
		assertThat(txManager.rollbacks).isEqualTo(0);
		assertThat(listener.events).isEmpty();

		ctx.close();
	}

		void gh23473AppliesRollbackOnAnyException() throws Exception {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(Gh23473ConfigB.class);
		MethodRollbackEventListener listener = new MethodRollbackEventListener();
		ctx.addApplicationListener(listener);
		ctx.refresh();
		TestServiceWithRollback bean = ctx.getBean("testBean", TestServiceWithRollback.class);
		CallCountingTransactionManager txManager = ctx.getBean(CallCountingTransactionManager.class);

		Method method1 = TestServiceWithRollback.class.getMethod("methodOne");
		Method method2 = TestServiceWithRollback.class.getMethod("methodTwo");
		assertThatException().isThrownBy(bean::methodOne);
		assertThatException().isThrownBy(bean::methodTwo);
		assertThat(txManager.begun).isEqualTo(2);
		assertThat(txManager.commits).isEqualTo(0);
		assertThat(txManager.rollbacks).isEqualTo(2);
		assertThat(listener.events).hasSize(2);
		assertThat(listener.events.get(0))
				.satisfies(event -> assertThat(event.getMethod()).isEqualTo(method1))
				.satisfies(event -> assertThat(event.getFailure()).isExactlyInstanceOf(Exception.class))
				.satisfies(event -> assertThat(event.getTransaction().getTransactionName())
						.isEqualTo(ClassUtils.getQualifiedMethodName(method1)));
		assertThat(listener.events.get(1))
				.satisfies(event -> assertThat(event.getMethod()).isEqualTo(method2))
				.satisfies(event -> assertThat(event.getFailure()).isExactlyInstanceOf(Exception.class))
				.satisfies(event -> assertThat(event.getTransaction().getTransactionName())
						.isEqualTo(ClassUtils.getQualifiedMethodName(method2)));

		ctx.close();
	}


		public static class TransactionalTestBean {

				public Collection<?> findAllFoos() {
			return null;
		}

				public void saveQualifiedFoo() {
		}

				public void saveQualifiedFooWithAttributeAlias() {
		}
	}


			public static class TransactionalTestBeanSubclass extends TransactionalTestBean {
	}

			public static class TransactionalTestBeanWithNonExistentQualifier extends TransactionalTestBean {
	}

			public static class TransactionalTestBeanWithInvalidQualifier extends TransactionalTestBean {
	}


		static class PlaceholderConfig {

				public PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
			PropertySourcesPlaceholderConfigurer pspc = new PropertySourcesPlaceholderConfigurer();
			Properties props = new Properties();
			props.setProperty("myLabel", "LABEL");
			props.setProperty("myTimeout", "5");
			props.setProperty("myTransactionManager", "qualifiedTransactionManager");
			pspc.setProperties(props);
			return pspc;
		}
	}


				static class EnableTxConfig {
	}


		static class InheritedEnableTxConfig extends EnableTxConfig {
	}


					static class ParentEnableTxConfig {

				Object someBean() {
			return new Object();
		}
	}


		static class ChildEnableTxConfig extends ParentEnableTxConfig {

				Object someBean() {
			return "X";
		}
	}


	private static class NeverCondition implements ConfigurationCondition {

				public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return false;
		}

				public ConfigurationPhase getConfigurationPhase() {
			return ConfigurationPhase.REGISTER_BEAN;
		}
	}


			static class EnableAspectjTxConfig {
	}


		static class TxManagerConfig {

				public TransactionalTestBean testBean() {
			return new TransactionalTestBean();
		}

				public PlatformTransactionManager txManager() {
			return new CallCountingTransactionManager();
		}
	}


		static class MultiTxManagerConfig extends TxManagerConfig implements TransactionManagementConfigurer {

				public PlatformTransactionManager txManager2() {
			return new CallCountingTransactionManager();
		}

				public PlatformTransactionManager annotationDrivenTransactionManager() {
			return txManager2();
		}
	}


		static class PrimaryMultiTxManagerConfig {

				public TransactionalTestBean testBean() {
			return new TransactionalTestBean();
		}

						public PlatformTransactionManager primary() {
			return new CallCountingTransactionManager();
		}

				public PlatformTransactionManager txManager2() {
			return new CallCountingTransactionManager();
		}
	}


		static class PrimaryTxManagerAndTxMgmtConfigurerConfig implements TransactionManagementConfigurer {

				public TransactionalTestBean testBean() {
			return new TransactionalTestBean();
		}

						public PlatformTransactionManager primary() {
			return new CallCountingTransactionManager();
		}

						public PlatformTransactionManager annotationDrivenTransactionManager() {
			return new CallCountingTransactionManager();
		}
	}


		static class SingleTxManagerBeanAndTxMgmtConfigurerConfig implements TransactionManagementConfigurer {

		final CallCountingTransactionManager annotationDriven = new CallCountingTransactionManager();

				public TransactionalTestBean testBean() {
			return new TransactionalTestBean();
		}

				public PlatformTransactionManager txManager() {
			return new CallCountingTransactionManager();
		}

		// The transaction manager returned from this method is intentionally not
		// registered as a bean in the ApplicationContext.
				public PlatformTransactionManager annotationDrivenTransactionManager() {
			return annotationDriven;
		}
	}


				static class ManualSingletonConfig {

				public void initializeApp(ConfigurableApplicationContext applicationContext) {
			applicationContext.getBeanFactory().registerSingleton(
					"qualifiedTransactionManager", new CallCountingTransactionManager());
		}

				public TransactionalTestBean testBean() {
			return new TransactionalTestBean();
		}

				public CallCountingTransactionManager otherTxManager() {
			return new CallCountingTransactionManager();
		}
	}


				static class QualifiedTransactionConfig {

				public void initializeApp(ConfigurableApplicationContext applicationContext) {
			applicationContext.getBeanFactory().registerSingleton(
					"qualifiedTransactionManager", new CallCountingTransactionManager());
			applicationContext.getBeanFactory().registerAlias("qualifiedTransactionManager", "qualified");
		}

				public TransactionalTestBeanSubclass testBean() {
			return new TransactionalTestBeanSubclass();
		}

				public TransactionalTestBeanWithNonExistentQualifier testBeanWithNonExistentQualifier() {
			return new TransactionalTestBeanWithNonExistentQualifier();
		}

				public TransactionalTestBeanWithInvalidQualifier testBeanWithInvalidQualifier() {
			return new TransactionalTestBeanWithInvalidQualifier();
		}

						public CallCountingTransactionManager primaryTransactionManager() {
			return new CallCountingTransactionManager();
		}
	}


	public interface BaseTransactionalInterface {

				default void saveBar() {
		}
	}


	public interface TransactionalTestInterface extends BaseTransactionalInterface {

				void saveFoo();
	}


		public static class TransactionalTestService implements TransactionalTestInterface {

				public void saveFoo() {
		}
	}


			static class Spr14322ConfigA {

				public TransactionalTestInterface testBean() {
			return new TransactionalTestService();
		}

				public PlatformTransactionManager txManager() {
			return new CallCountingTransactionManager();
		}
	}


			static class Spr14322ConfigB {

				public TransactionalTestInterface testBean() {
			return new TransactionalTestService();
		}

				public PlatformTransactionManager txManager() {
			return new CallCountingTransactionManager();
		}
	}


		interface TransactionalInterface {

		void methodOne();
	}


	interface NonTransactionalInterface {

		void methodTwo();
	}


	static class MixedTransactionalTestService implements TransactionalInterface, NonTransactionalInterface {

				public void methodOne() {
		}

				public void methodTwo() {
		}
	}


			static class Gh24502Config {

				public MixedTransactionalTestService testBean() {
			return new MixedTransactionalTestService();
		}

				public PlatformTransactionManager txManager() {
			return new CallCountingTransactionManager();
		}
	}


	static class TestServiceWithRollback {

				public void methodOne() throws Exception {
			throw new Exception();
		}

				public void methodTwo() throws Exception {
			throw new Exception();
		}
	}


	static class MethodRollbackEventListener implements ApplicationListener<MethodRollbackEvent> {

		public final List<MethodRollbackEvent> events = new ArrayList<>();

				public void onApplicationEvent(MethodRollbackEvent event) {
			this.events.add(event);
		}
	}


			static class Gh23473ConfigA {

				public TestServiceWithRollback testBean() {
			return new TestServiceWithRollback();
		}

				public PlatformTransactionManager txManager() {
			return new CallCountingTransactionManager();
		}
	}


			static class Gh23473ConfigB {

				public TestServiceWithRollback testBean() {
			return new TestServiceWithRollback();
		}

				public PlatformTransactionManager txManager() {
			return new CallCountingTransactionManager();
		}
	}

}
