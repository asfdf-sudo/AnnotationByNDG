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

package org.springframework.jdbc.datasource;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;


import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link DelegatingDataSource}.
 *
 * @author Phillip Webb
 */
class DelegatingDataSourceTests {

	private final DataSource delegate = mock();

	private DelegatingDataSource dataSource = new DelegatingDataSource(delegate);


		void shouldDelegateGetConnection() throws Exception {
		Connection connection = mock();
		given(delegate.getConnection()).willReturn(connection);
		assertThat(dataSource.getConnection()).isEqualTo(connection);
	}

		void shouldDelegateGetConnectionWithUsernameAndPassword() throws Exception {
		Connection connection = mock();
		String username = "username";
		String password = "password";
		given(delegate.getConnection(username, password)).willReturn(connection);
		assertThat(dataSource.getConnection(username, password)).isEqualTo(connection);
	}

		void shouldDelegateGetLogWriter() throws Exception {
		PrintWriter writer = new PrintWriter(new ByteArrayOutputStream());
		given(delegate.getLogWriter()).willReturn(writer);
		assertThat(dataSource.getLogWriter()).isEqualTo(writer);
	}

		void shouldDelegateSetLogWriter() throws Exception {
		PrintWriter writer = new PrintWriter(new ByteArrayOutputStream());
		dataSource.setLogWriter(writer);
		verify(delegate).setLogWriter(writer);
	}

		void shouldDelegateGetLoginTimeout() throws Exception {
		int timeout = 123;
		given(delegate.getLoginTimeout()).willReturn(timeout);
		assertThat(dataSource.getLoginTimeout()).isEqualTo(timeout);
	}

		void shouldDelegateSetLoginTimeoutWithSeconds() throws Exception {
		int timeout = 123;
		dataSource.setLoginTimeout(timeout);
		verify(delegate).setLoginTimeout(timeout);
	}

		void shouldDelegateUnwrapWithoutImplementing() throws Exception {
		ExampleWrapper wrapper = mock();
		given(delegate.unwrap(ExampleWrapper.class)).willReturn(wrapper);
		assertThat(dataSource.unwrap(ExampleWrapper.class)).isEqualTo(wrapper);
	}

		void shouldDelegateUnwrapImplementing() throws Exception {
		dataSource = new DelegatingDataSourceWithWrapper();
		assertThat(dataSource.unwrap(ExampleWrapper.class)).isSameAs(dataSource);
	}

		void shouldDelegateIsWrapperForWithoutImplementing() throws Exception {
		given(delegate.isWrapperFor(ExampleWrapper.class)).willReturn(true);
		assertThat(dataSource.isWrapperFor(ExampleWrapper.class)).isTrue();
	}

		void shouldDelegateIsWrapperForImplementing() throws Exception {
		dataSource = new DelegatingDataSourceWithWrapper();
		assertThat(dataSource.isWrapperFor(ExampleWrapper.class)).isTrue();
	}


	public interface ExampleWrapper {
	}

	private static class DelegatingDataSourceWithWrapper extends DelegatingDataSource
			implements ExampleWrapper {
	}

}
