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

package org.springframework.transaction.interceptor;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;


import java.io.Serializable;
import java.lang.reflect.Method;

import org.jspecify.annotations.Nullable;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.transaction.TransactionManager;
import org.springframework.util.ObjectUtils;

/**
 * Internal class that implements a {@code Pointcut} that matches if the underlying
 * {@link TransactionAttributeSource} has an attribute for a given method.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 2.5.5
 */
final class TransactionAttributeSourcePointcut extends StaticMethodMatcherPointcut implements Serializable {

	private @Nullable TransactionAttributeSource transactionAttributeSource;


	public TransactionAttributeSourcePointcut() {
		setClassFilter(new TransactionAttributeSourceClassFilter());
	}


	public void setTransactionAttributeSource(TransactionAttributeSource transactionAttributeSource) {
		this.transactionAttributeSource = transactionAttributeSource;
	}

		public boolean matches(Method method, Class<?> targetClass) {
		return (this.transactionAttributeSource == null ||
				this.transactionAttributeSource.hasTransactionAttribute(method, targetClass));
	}

		public boolean equals( @Nullable Object other) {
		return (this == other || (other instanceof TransactionAttributeSourcePointcut that &&
				ObjectUtils.nullSafeEquals(this.transactionAttributeSource, that.transactionAttributeSource)));
	}

		public int hashCode() {
		return TransactionAttributeSourcePointcut.class.hashCode();
	}

		public String toString() {
		return getClass().getName() + ": " + this.transactionAttributeSource;
	}


	/**
	 * {@link ClassFilter} that delegates to {@link TransactionAttributeSource#isCandidateClass}
	 * for filtering classes whose methods are not worth searching to begin with.
	 */
	private final class TransactionAttributeSourceClassFilter implements ClassFilter {

				public boolean matches(Class<?> clazz) {
			if (TransactionalProxy.class.isAssignableFrom(clazz) ||
					TransactionManager.class.isAssignableFrom(clazz) ||
					PersistenceExceptionTranslator.class.isAssignableFrom(clazz)) {
				return false;
			}
			return (transactionAttributeSource == null || transactionAttributeSource.isCandidateClass(clazz));
		}

		private TransactionAttributeSource getTransactionAttributeSource() {
			return transactionAttributeSource;
		}

				public boolean equals(Object other) {
			return (this == other || (other instanceof TransactionAttributeSourceClassFilter that &&
					ObjectUtils.nullSafeEquals(getTransactionAttributeSource(), that.getTransactionAttributeSource())));
		}

				public int hashCode() {
			return TransactionAttributeSourceClassFilter.class.hashCode();
		}

				public String toString() {
			return TransactionAttributeSourceClassFilter.class.getName() + ": " + getTransactionAttributeSource();
		}
	}

}
