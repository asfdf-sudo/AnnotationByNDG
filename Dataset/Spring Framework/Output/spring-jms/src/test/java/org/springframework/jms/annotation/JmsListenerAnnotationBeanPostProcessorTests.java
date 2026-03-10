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


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.AbstractJmsListenerEndpoint;
import org.springframework.jms.config.JmsListenerContainerTestFactory;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.config.MessageListenerTestContainer;
import org.springframework.jms.config.MethodJmsListenerEndpoint;
import org.springframework.jms.listener.SimpleMessageListenerContainer;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

/**
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 */
class JmsListenerAnnotationBeanPostProcessorTests {

		void simpleMessageListener() throws Exception {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				Config.class, SimpleMessageListenerTestBean.class);

		JmsListenerContainerTestFactory factory = context.getBean(JmsListenerContainerTestFactory.class);
		assertThat(factory.getListenerContainers()).as("One container should have been registered").hasSize(1);
		MessageListenerTestContainer container = factory.getListenerContainers().get(0);

		JmsListenerEndpoint endpoint = container.getEndpoint();
		assertThat(endpoint.getClass()).as("Wrong endpoint type").isEqualTo(MethodJmsListenerEndpoint.class);
		MethodJmsListenerEndpoint methodEndpoint = (MethodJmsListenerEndpoint) endpoint;
		assertThat(methodEndpoint.getBean().getClass()).isEqualTo(SimpleMessageListenerTestBean.class);
		assertThat(methodEndpoint.getMethod()).isEqualTo(SimpleMessageListenerTestBean.class.getMethod("handleIt", String.class));
		assertThat(methodEndpoint.getMostSpecificMethod()).isEqualTo(SimpleMessageListenerTestBean.class.getMethod("handleIt", String.class));

		SimpleMessageListenerContainer listenerContainer = new SimpleMessageListenerContainer();
		methodEndpoint.setupListenerContainer(listenerContainer);
		assertThat(listenerContainer.getMessageListener()).isNotNull();

		assertThat(container.isStarted()).as("Should have been started " + container).isTrue();
		context.close(); // Close and stop the listeners
		assertThat(container.isStopped()).as("Should have been stopped " + container).isTrue();
	}

		void metaAnnotationIsDiscovered() throws Exception {
		try (ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(Config.class, MetaAnnotationTestBean.class)) {
			JmsListenerContainerTestFactory factory = context.getBean(JmsListenerContainerTestFactory.class);
			assertThat(factory.getListenerContainers()).as("one container should have been registered").hasSize(1);

			JmsListenerEndpoint endpoint = factory.getListenerContainers().get(0).getEndpoint();
			assertThat(endpoint.getClass()).as("Wrong endpoint type").isEqualTo(MethodJmsListenerEndpoint.class);
			MethodJmsListenerEndpoint methodEndpoint = (MethodJmsListenerEndpoint) endpoint;
			assertThat(methodEndpoint.getBean().getClass()).isEqualTo(MetaAnnotationTestBean.class);
			assertThat(methodEndpoint.getMethod()).isEqualTo(MetaAnnotationTestBean.class.getMethod("handleIt", String.class));
			assertThat(methodEndpoint.getMostSpecificMethod()).isEqualTo(MetaAnnotationTestBean.class.getMethod("handleIt", String.class));
			assertThat(((AbstractJmsListenerEndpoint) endpoint).getDestination()).isEqualTo("metaTestQueue");
		}
	}

		void sendToAnnotationFoundOnInterfaceProxy() throws Exception {
		try (ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(Config.class, ProxyConfig.class, InterfaceProxyTestBean.class)) {
			JmsListenerContainerTestFactory factory = context.getBean(JmsListenerContainerTestFactory.class);
			assertThat(factory.getListenerContainers()).as("one container should have been registered").hasSize(1);

			JmsListenerEndpoint endpoint = factory.getListenerContainers().get(0).getEndpoint();
			assertThat(endpoint.getClass()).as("Wrong endpoint type").isEqualTo(MethodJmsListenerEndpoint.class);
			MethodJmsListenerEndpoint methodEndpoint = (MethodJmsListenerEndpoint) endpoint;
			assertThat(AopUtils.isJdkDynamicProxy(methodEndpoint.getBean())).isTrue();
			boolean condition = methodEndpoint.getBean() instanceof SimpleService;
			assertThat(condition).isTrue();
			assertThat(methodEndpoint.getMethod()).isEqualTo(SimpleService.class.getMethod("handleIt", String.class, String.class));
			assertThat(methodEndpoint.getMostSpecificMethod()).isEqualTo(InterfaceProxyTestBean.class.getMethod("handleIt", String.class, String.class));

			Method method = ReflectionUtils.findMethod(endpoint.getClass(), "getDefaultResponseDestination");
			ReflectionUtils.makeAccessible(method);
			Object destination = ReflectionUtils.invokeMethod(method, endpoint);
			assertThat(destination).as("SendTo annotation not found on proxy").isEqualTo("foobar");
		}
	}

		void sendToAnnotationFoundOnCglibProxy() throws Exception {
		try (ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(Config.class, ProxyConfig.class, ClassProxyTestBean.class)) {
			JmsListenerContainerTestFactory factory = context.getBean(JmsListenerContainerTestFactory.class);
			assertThat(factory.getListenerContainers()).as("one container should have been registered").hasSize(1);

			JmsListenerEndpoint endpoint = factory.getListenerContainers().get(0).getEndpoint();
			assertThat(endpoint.getClass()).as("Wrong endpoint type").isEqualTo(MethodJmsListenerEndpoint.class);
			MethodJmsListenerEndpoint methodEndpoint = (MethodJmsListenerEndpoint) endpoint;
			assertThat(AopUtils.isCglibProxy(methodEndpoint.getBean())).isTrue();
			boolean condition = methodEndpoint.getBean() instanceof ClassProxyTestBean;
			assertThat(condition).isTrue();
			assertThat(methodEndpoint.getMethod()).isEqualTo(ClassProxyTestBean.class.getMethod("handleIt", String.class, String.class));
			assertThat(methodEndpoint.getMostSpecificMethod()).isEqualTo(ClassProxyTestBean.class.getMethod("handleIt", String.class, String.class));

			Method method = ReflectionUtils.findMethod(endpoint.getClass(), "getDefaultResponseDestination");
			ReflectionUtils.makeAccessible(method);
			Object destination = ReflectionUtils.invokeMethod(method, endpoint);
			assertThat(destination).as("SendTo annotation not found on proxy").isEqualTo("foobar");
		}
	}

		void invalidProxy() {
		assertThatExceptionOfType(BeanCreationException.class).isThrownBy(() ->
				new AnnotationConfigApplicationContext(Config.class, ProxyConfig.class, InvalidProxyTestBean.class))
			.withCauseInstanceOf(IllegalStateException.class)
			.withMessageContaining("handleIt2");
	}


		static class SimpleMessageListenerTestBean {

				public void handleIt(String body) {
		}
	}


		static class MetaAnnotationTestBean {

				public void handleIt(String body) {
		}
	}


				@interface FooListener {
	}


		static class Config {

				public JmsListenerAnnotationBeanPostProcessor postProcessor() {
			JmsListenerAnnotationBeanPostProcessor postProcessor = new JmsListenerAnnotationBeanPostProcessor();
			postProcessor.setContainerFactoryBeanName("testFactory");
			postProcessor.setEndpointRegistry(jmsListenerEndpointRegistry());
			return postProcessor;
		}

				public JmsListenerEndpointRegistry jmsListenerEndpointRegistry() {
			return new JmsListenerEndpointRegistry();
		}

				public JmsListenerContainerTestFactory testFactory() {
			return new JmsListenerContainerTestFactory();
		}
	}


			static class ProxyConfig {

				public PlatformTransactionManager transactionManager() {
			return mock();
		}
	}


	interface SimpleService {

		void handleIt(String value, String body);
	}


		static class InterfaceProxyTestBean implements SimpleService {

										public void handleIt(String value, String body) {
		}
	}


		static class ClassProxyTestBean {

								public void handleIt(String value, String body) {
		}
	}


		static class InvalidProxyTestBean implements SimpleService {

				public void handleIt(String value, String body) {
		}

								public void handleIt2(String body) {
		}
	}

}
