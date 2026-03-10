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

package org.springframework.jms.annotation;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.jms.MessageListener;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.annotation.AliasFor;
import org.springframework.jms.config.JmsListenerContainerTestFactory;
import org.springframework.jms.config.JmsListenerEndpointRegistrar;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.config.MessageListenerTestContainer;
import org.springframework.jms.config.MethodJmsListenerEndpoint;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.listener.adapter.ListenerExecutionFailedException;
import org.springframework.jms.listener.adapter.MessageListenerAdapter;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author Stephane Nicoll
 * @author Sam Brannen
 */
class EnableJmsTests extends AbstractJmsAnnotationDrivenTests {

			void sampleConfiguration() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				EnableJmsSampleConfig.class, SampleBean.class);
		testSampleConfiguration(context);
	}

			void fullConfiguration() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				EnableJmsFullConfig.class, FullBean.class);
		testFullConfiguration(context);
	}

			void fullConfigurableConfiguration() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				EnableJmsFullConfigurableConfig.class, FullConfigurableBean.class);
		testFullConfiguration(context);
	}

			void customConfiguration() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				EnableJmsCustomConfig.class, CustomBean.class);
		testCustomConfiguration(context);
	}

			void explicitContainerFactory() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				EnableJmsCustomContainerFactoryConfig.class, DefaultBean.class);
		testExplicitContainerFactoryConfiguration(context);
	}

			void defaultContainerFactory() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				EnableJmsDefaultContainerFactoryConfig.class, DefaultBean.class);
		testDefaultContainerFactoryConfiguration(context);
	}

		void containerAreStartedByDefault() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				EnableJmsDefaultContainerFactoryConfig.class, DefaultBean.class);
		JmsListenerContainerTestFactory factory = context.getBean(JmsListenerContainerTestFactory.class);
		MessageListenerTestContainer container = factory.getListenerContainers().get(0);
		assertThat(container.isAutoStartup()).isTrue();
		assertThat(container.isStarted()).isTrue();
	}

		void containerCanBeStarterViaTheRegistry() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				EnableJmsAutoStartupFalseConfig.class, DefaultBean.class);
		JmsListenerContainerTestFactory factory = context.getBean(JmsListenerContainerTestFactory.class);
		MessageListenerTestContainer container = factory.getListenerContainers().get(0);
		assertThat(container.isAutoStartup()).isFalse();
		assertThat(container.isStarted()).isFalse();
		JmsListenerEndpointRegistry registry = context.getBean(JmsListenerEndpointRegistry.class);
		registry.start();
		assertThat(container.isStarted()).isTrue();
	}

			void jmsHandlerMethodFactoryConfiguration() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				EnableJmsHandlerMethodFactoryConfig.class, ValidationBean.class);

		assertThatExceptionOfType(ListenerExecutionFailedException.class)
				.isThrownBy(() -> testJmsHandlerMethodFactoryConfiguration(context))
				.withCauseInstanceOf(MethodArgumentNotValidException.class);
	}

			void jmsListenerIsRepeatable() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				EnableJmsDefaultContainerFactoryConfig.class, JmsListenerRepeatableBean.class);
		testJmsListenerRepeatable(context);
	}

			void jmsListeners() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				EnableJmsDefaultContainerFactoryConfig.class, JmsListenersBean.class);
		testJmsListenerRepeatable(context);
	}

		void composedJmsListeners() {
		try (ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				EnableJmsDefaultContainerFactoryConfig.class, ComposedJmsListenersBean.class)) {

			JmsListenerContainerTestFactory simpleFactory =
					context.getBean("jmsListenerContainerFactory", JmsListenerContainerTestFactory.class);
			assertThat(simpleFactory.getListenerContainers()).hasSize(2);

			MethodJmsListenerEndpoint first = (MethodJmsListenerEndpoint)
					simpleFactory.getListenerContainer("first").getEndpoint();
			assertThat(first.getId()).isEqualTo("first");
			assertThat(first.getDestination()).isEqualTo("orderQueue");
			assertThat(first.getConcurrency()).isNull();

			MethodJmsListenerEndpoint second = (MethodJmsListenerEndpoint)
					simpleFactory.getListenerContainer("second").getEndpoint();
			assertThat(second.getId()).isEqualTo("second");
			assertThat(second.getDestination()).isEqualTo("billingQueue");
			assertThat(second.getConcurrency()).isEqualTo("2-10");
		}
	}

		void unknownFactory() {
		 // not found
		assertThatExceptionOfType(BeanCreationException.class)
				.isThrownBy(() -> new AnnotationConfigApplicationContext(EnableJmsSampleConfig.class, CustomBean.class))
				.withMessageContaining("customFactory");
	}

		void lazyComponent() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				EnableJmsDefaultContainerFactoryConfig.class, LazyBean.class);
		JmsListenerContainerTestFactory defaultFactory =
				context.getBean("jmsListenerContainerFactory", JmsListenerContainerTestFactory.class);
		assertThat(defaultFactory.getListenerContainers()).isEmpty();

		context.getBean(LazyBean.class);  // trigger lazy resolution
		assertThat(defaultFactory.getListenerContainers()).hasSize(1);
		MessageListenerTestContainer container = defaultFactory.getListenerContainers().get(0);
		assertThat(container.isStarted()).as("Should have been started " + container).isTrue();
		context.close();  // close and stop the listeners
		assertThat(container.isStopped()).as("Should have been stopped " + container).isTrue();
	}


			static class EnableJmsSampleConfig {

				public JmsListenerContainerTestFactory jmsListenerContainerFactory() {
			return new JmsListenerContainerTestFactory();
		}

				public JmsListenerContainerTestFactory simpleFactory() {
			return new JmsListenerContainerTestFactory();
		}
	}


			static class EnableJmsFullConfig {

				public JmsListenerContainerTestFactory simpleFactory() {
			return new JmsListenerContainerTestFactory();
		}
	}


				static class EnableJmsFullConfigurableConfig {

				public JmsListenerContainerTestFactory simpleFactory() {
			return new JmsListenerContainerTestFactory();
		}

				public PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
			return new PropertySourcesPlaceholderConfigurer();
		}
	}


			static class EnableJmsCustomConfig implements JmsListenerConfigurer {

				public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
			registrar.setEndpointRegistry(customRegistry());

			// Also register a custom endpoint
			SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
			endpoint.setId("myCustomEndpointId");
			endpoint.setDestination("myQueue");
			endpoint.setMessageListener(simpleMessageListener());
			registrar.registerEndpoint(endpoint);
		}

				public JmsListenerContainerTestFactory jmsListenerContainerFactory() {
			return new JmsListenerContainerTestFactory();
		}

				public JmsListenerEndpointRegistry customRegistry() {
			return new JmsListenerEndpointRegistry();
		}

				public JmsListenerContainerTestFactory customFactory() {
			return new JmsListenerContainerTestFactory();
		}

				public MessageListener simpleMessageListener() {
			return new MessageListenerAdapter();
		}
	}


			static class EnableJmsCustomContainerFactoryConfig implements JmsListenerConfigurer {

				public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
			registrar.setContainerFactory(simpleFactory());
		}

				public JmsListenerContainerTestFactory simpleFactory() {
			return new JmsListenerContainerTestFactory();
		}
	}


			static class EnableJmsDefaultContainerFactoryConfig {

				public JmsListenerContainerTestFactory jmsListenerContainerFactory() {
			return new JmsListenerContainerTestFactory();
		}
	}


			static class EnableJmsHandlerMethodFactoryConfig implements JmsListenerConfigurer {

				public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
			registrar.setMessageHandlerMethodFactory(customMessageHandlerMethodFactory());
		}

				public MessageHandlerMethodFactory customMessageHandlerMethodFactory() {
			DefaultMessageHandlerMethodFactory factory = new DefaultMessageHandlerMethodFactory();
			factory.setValidator(new TestValidator());
			return factory;
		}

				public JmsListenerContainerTestFactory defaultFactory() {
			return new JmsListenerContainerTestFactory();
		}
	}


			static class EnableJmsAutoStartupFalseConfig implements JmsListenerConfigurer {

				public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
			registrar.setContainerFactory(simpleFactory());
		}

				public JmsListenerContainerTestFactory simpleFactory() {
			JmsListenerContainerTestFactory factory = new JmsListenerContainerTestFactory();
			factory.setAutoStartup(false);
			return factory;
		}
	}


			static class LazyBean {

				public void handle(String msg) {
		}
	}


			private @interface OrderQueueListener {

				String id() default "";

				String concurrency() default "";
	}


			private @interface BillingQueueListener {

				String id() default "";

				String concurrency() default "";
	}


		static class ComposedJmsListenersBean {

						public void repeatableHandle(String msg) {
		}
	}

}
