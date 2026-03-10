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

package org.springframework.orm.jpa;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;


import java.util.Map;
import java.util.Properties;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceConfiguration;
import jakarta.persistence.spi.PersistenceProvider;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.ProviderUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.orm.jpa.persistenceunit.DefaultPersistenceUnitManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Phillip Webb
 */
class LocalEntityManagerFactoryBeanTests extends AbstractEntityManagerFactoryBeanTests {

		void verifyClosed() {
		verify(mockEmf).close();
	}


		void withDefault() {
		DummyPersistenceProvider persistenceProvider = new DummyPersistenceProvider();
		LocalEntityManagerFactoryBean emfb = new LocalEntityManagerFactoryBean();

		emfb.setPersistenceProvider(persistenceProvider);
		emfb.afterPropertiesSet();

		assertThat(persistenceProvider.actualName).isSameAs(
				DefaultPersistenceUnitManager.ORIGINAL_DEFAULT_PERSISTENCE_UNIT_NAME);
		assertThat(persistenceProvider.actualProps).isEqualTo(emfb.getJpaPropertyMap());
		checkInvariants(emfb);

		emfb.destroy();
	}

		void withName() {
		DummyPersistenceProvider persistenceProvider = new DummyPersistenceProvider();
		LocalEntityManagerFactoryBean emfb = new LocalEntityManagerFactoryBean();
		String name = "call me Bob";

		emfb.setPersistenceUnitName(name);
		emfb.setPersistenceProvider(persistenceProvider);
		emfb.afterPropertiesSet();

		assertThat(persistenceProvider.actualName).isSameAs(name);
		assertThat(persistenceProvider.actualProps).isEqualTo(emfb.getJpaPropertyMap());
		checkInvariants(emfb);

		emfb.destroy();
	}

		void withNameAndExplicitProperties() {
		DummyPersistenceProvider persistenceProvider = new DummyPersistenceProvider();
		LocalEntityManagerFactoryBean emfb = new LocalEntityManagerFactoryBean();
		String name = "call me Bob";
		Properties props = new Properties();
		props.setProperty("myProp", "myVal");

		emfb.setPersistenceUnitName(name);
		emfb.setPersistenceProvider(persistenceProvider);
		emfb.setJpaProperties(props);
		emfb.afterPropertiesSet();

		assertThat(persistenceProvider.actualName).isSameAs(name);
		assertThat(persistenceProvider.actualProps).isEqualTo(props);
		checkInvariants(emfb);

		emfb.destroy();
	}

		void withDefaultPersistenceConfiguration() {
		DummyPersistenceProvider persistenceProvider = new DummyPersistenceProvider();
		LocalEntityManagerFactoryBean emfb = new LocalEntityManagerFactoryBean();

		emfb.getPersistenceConfiguration().property("myProp", "myVal");
		emfb.setPersistenceProvider(persistenceProvider);
		emfb.afterPropertiesSet();

		assertThat(persistenceProvider.actualName).isSameAs(
				DefaultPersistenceUnitManager.ORIGINAL_DEFAULT_PERSISTENCE_UNIT_NAME);
		assertThat(persistenceProvider.actualProps).containsEntry("myProp", "myVal");
		checkInvariants(emfb);

		emfb.destroy();
	}

		void withNameAndDefaultPersistenceConfiguration() {
		DummyPersistenceProvider persistenceProvider = new DummyPersistenceProvider();
		LocalEntityManagerFactoryBean emfb = new LocalEntityManagerFactoryBean();
		String name = "call me Bob";

		emfb.setPersistenceUnitName(name);
		emfb.getPersistenceConfiguration().property("myProp", "myVal");
		emfb.setPersistenceProvider(persistenceProvider);
		emfb.afterPropertiesSet();

		assertThat(persistenceProvider.actualName).isSameAs(name);
		assertThat(persistenceProvider.actualProps).containsEntry("myProp", "myVal");
		checkInvariants(emfb);

		emfb.destroy();
	}

		void withExplicitPersistenceConfiguration() {
		DummyPersistenceProvider persistenceProvider = new DummyPersistenceProvider();
		LocalEntityManagerFactoryBean emfb = new LocalEntityManagerFactoryBean();
		String name = "call me Bob";
		PersistenceConfiguration config = new PersistenceConfiguration(name);
		config.property("myProp", "myVal");

		emfb.setPersistenceConfiguration(config);
		emfb.setPersistenceProvider(persistenceProvider);
		emfb.afterPropertiesSet();

		assertThat(persistenceProvider.actualName).isSameAs(name);
		assertThat(persistenceProvider.actualProps).containsEntry("myProp", "myVal");
		checkInvariants(emfb);

		emfb.destroy();
	}


	private static class DummyPersistenceProvider implements PersistenceProvider {

		String actualName;

		Map actualProps;

				public EntityManagerFactory createContainerEntityManagerFactory(PersistenceUnitInfo pui, Map map) {
			throw new UnsupportedOperationException();
		}

				public EntityManagerFactory createEntityManagerFactory(String emfName, Map properties) {
			actualName = emfName;
			actualProps = properties;
			return mockEmf;
		}

				public EntityManagerFactory createEntityManagerFactory(PersistenceConfiguration config) {
			actualName = config.name();
			actualProps = config.properties();
			return mockEmf;
		}

				public ProviderUtil getProviderUtil() {
			throw new UnsupportedOperationException();
		}

				public void generateSchema(PersistenceUnitInfo persistenceUnitInfo, Map map) {
			throw new UnsupportedOperationException();
		}

				public boolean generateSchema(String persistenceUnitName, Map map) {
			throw new UnsupportedOperationException();
		}
	}

}
