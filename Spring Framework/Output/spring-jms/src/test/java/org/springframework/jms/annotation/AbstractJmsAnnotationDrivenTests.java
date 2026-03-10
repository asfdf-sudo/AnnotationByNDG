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


import java.lang.reflect.Method;

import jakarta.jms.JMSException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.context.ApplicationContext;
import org.springframework.jms.StubTextMessage;
import org.springframework.jms.config.JmsListenerContainerTestFactory;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.config.MethodJmsListenerEndpoint;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.listener.SimpleMessageListenerContainer;
import org.springframework.jms.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author Stephane Nicoll
 */
abstract class AbstractJmsAnnotationDrivenTests {

		abstract void sampleConfiguration();

		abstract void fullConfiguration();

		abstract void fullConfigurableConfiguration();

		abstract void customConfiguration();

		abstract void explicitContainerFactory();

		abstract void defaultContainerFactory();

		abstract void jmsHandlerMethodFactoryConfiguration();

		abstract void jmsListenerIsRepeatable();

		abstract void jmsListeners();


	/**
	 * Test for {@link SampleBean} discovery. If a factory with the default name
	 * is set, an endpoint will use it automatically
	 */
	protected void testSampleConfiguration(ApplicationContext context) {
		JmsListenerContainerTestFactory defaultFactory =
				context.getBean("jmsListenerContainerFactory", JmsListenerContainerTestFactory.class);
		JmsListenerContainerTestFactory simpleFactory =
				context.getBean("simpleFactory", JmsListenerContainerTestFactory.class);
		assertThat(defaultFactory.getListenerContainers()).hasSize(1);
		assertThat(simpleFactory.getListenerContainers()).hasSize(1);
	}

	/**
	 * Test for {@link FullBean} discovery. In this case, no default is set because
	 * all endpoints provide a default registry. This shows that the default factory
	 * is only retrieved if it needs to be.
	 */
	protected void testFullConfiguration( @Nullable ApplicationContext context) {
		JmsListenerContainerTestFactory simpleFactory =
				context.getBean("simpleFactory", JmsListenerContainerTestFactory.class);
		assertThat(simpleFactory.getListenerContainers()).hasSize(1);
		MethodJmsListenerEndpoint endpoint = (MethodJmsListenerEndpoint)
				simpleFactory.getListenerContainers().get(0).getEndpoint();
		assertThat(endpoint.getId()).isEqualTo("listener1");
		assertThat(endpoint.getDestination()).isEqualTo("queueIn");
		assertThat(endpoint.getSelector()).isEqualTo("mySelector");
		assertThat(endpoint.getSubscription()).isEqualTo("mySubscription");
		assertThat(endpoint.getConcurrency()).isEqualTo("1-10");

		Method m = ReflectionUtils.findMethod(endpoint.getClass(), "getDefaultResponseDestination");
		ReflectionUtils.makeAccessible(m);
		Object destination = ReflectionUtils.invokeMethod(m, endpoint);
		assertThat(destination).isEqualTo("queueOut");
	}

	/**
	 * Test for {@link CustomBean} and an endpoint manually registered
	 * with "myCustomEndpointId". The custom endpoint does not provide
	 * any factory, so it's registered with the default one
	 */
	protected void testCustomConfiguration(ApplicationContext context) {
		JmsListenerContainerTestFactory defaultFactory =
				context.getBean("jmsListenerContainerFactory", JmsListenerContainerTestFactory.class);
		JmsListenerContainerTestFactory customFactory =
				context.getBean("customFactory", JmsListenerContainerTestFactory.class);
		assertThat(defaultFactory.getListenerContainers()).hasSize(1);
		assertThat(customFactory.getListenerContainers()).hasSize(1);
		JmsListenerEndpoint endpoint = defaultFactory.getListenerContainers().get(0).getEndpoint();
		assertThat(endpoint.getClass()).as("Wrong endpoint type").isEqualTo(SimpleJmsListenerEndpoint.class);
		assertThat(((SimpleJmsListenerEndpoint) endpoint).getMessageListener()).as("Wrong listener set in custom endpoint").isEqualTo(context.getBean("simpleMessageListener"));

		JmsListenerEndpointRegistry customRegistry =
				context.getBean("customRegistry", JmsListenerEndpointRegistry.class);
		assertThat(customRegistry.getListenerContainerIds()).as("Wrong number of containers in the registry")
				.hasSize(2);
		assertThat(customRegistry.getListenerContainers()).as("Wrong number of containers in the registry").hasSize(2);
		assertThat(customRegistry.getListenerContainer("listenerId")).as("Container with custom id on the annotation should be found").isNotNull();
		assertThat(customRegistry.getListenerContainer("myCustomEndpointId")).as("Container created with custom id should be found").isNotNull();
	}

	/**
	 * Test for {@link DefaultBean} that does not define the container
	 * factory to use as a default is registered with an explicit
	 * default.
	 */
	protected void testExplicitContainerFactoryConfiguration(ApplicationContext context) {
		JmsListenerContainerTestFactory defaultFactory =
				context.getBean("simpleFactory", JmsListenerContainerTestFactory.class);
		assertThat(defaultFactory.getListenerContainers()).hasSize(1);
	}

	/**
	 * Test for {@link DefaultBean} that does not define the container
	 * factory to use as a default is registered with the default name.
	 */
	protected void testDefaultContainerFactoryConfiguration(ApplicationContext context) {
		JmsListenerContainerTestFactory defaultFactory =
				context.getBean("jmsListenerContainerFactory", JmsListenerContainerTestFactory.class);
		assertThat(defaultFactory.getListenerContainers()).hasSize(1);
	}

	/**
	 * Test for {@link ValidationBean} with a validator ({@link TestValidator}) specified
	 * in a custom {@link org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory}.
	 *
	 * The test should throw a {@link org.springframework.jms.listener.adapter.ListenerExecutionFailedException}
	 */
	protected void testJmsHandlerMethodFactoryConfiguration(ApplicationContext context) throws JMSException {
		JmsListenerContainerTestFactory simpleFactory =
				context.getBean("defaultFactory", JmsListenerContainerTestFactory.class);
		assertThat(simpleFactory.getListenerContainers()).hasSize(1);
		MethodJmsListenerEndpoint endpoint = (MethodJmsListenerEndpoint)
				simpleFactory.getListenerContainers().get(0).getEndpoint();

		SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
		endpoint.setupListenerContainer(container);
		MessagingMessageListenerAdapter listener = (MessagingMessageListenerAdapter) container.getMessageListener();
		listener.onMessage(new StubTextMessage("failValidation"), mock());
	}

	/**
	 * Test for {@link JmsListenerRepeatableBean} and {@link JmsListenersBean} that validates that the
	 * {@code @JmsListener} annotation is repeatable and generate one specific container per annotation.
	 */
	protected void testJmsListenerRepeatable(ApplicationContext context) {
		JmsListenerContainerTestFactory simpleFactory =
				context.getBean("jmsListenerContainerFactory", JmsListenerContainerTestFactory.class);
		assertThat(simpleFactory.getListenerContainers()).hasSize(2);

		MethodJmsListenerEndpoint first = (MethodJmsListenerEndpoint)
				simpleFactory.getListenerContainer("first").getEndpoint();
		assertThat(first.getId()).isEqualTo("first");
		assertThat(first.getDestination()).isEqualTo("myQueue");
		assertThat(first.getConcurrency()).isNull();

		MethodJmsListenerEndpoint second = (MethodJmsListenerEndpoint)
				simpleFactory.getListenerContainer("second").getEndpoint();
		assertThat(second.getId()).isEqualTo("second");
		assertThat(second.getDestination()).isEqualTo("anotherQueue");
		assertThat(second.getConcurrency()).isEqualTo("2-10");
	}


		static class SampleBean {

				public void defaultHandle(String msg) {
		}

				public void simpleHandle(String msg) {
		}
	}


		static class FullBean {

						public String fullHandle(String msg) {
			return "reply";
		}
	}


		static class FullConfigurableBean {

						public String fullHandle(String msg) {
			return "reply";
		}
	}


		static class CustomBean {

				public void customHandle(String msg) {
		}
	}


	static class DefaultBean {

				public void handleIt(String msg) {
		}
	}


		static class ValidationBean {

				public void defaultHandle(String msg) {
		}
	}


		static class JmsListenerRepeatableBean {

						public void repeatableHandle(String msg) {
		}
	}


		static class JmsListenersBean {

		rts(Class<?> clazz) {
			return String.class.isAssignableFrom(clazz);
		}

				public void validate(Object target, Errors errors) {
			String value = (String) target;
			if ("failValidation".equals(value)) {
				errors.reject("TEST: expected invalid value");
			}
		}
	}

}
