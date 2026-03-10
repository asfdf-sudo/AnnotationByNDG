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

package org.springframework.transaction.testfixture;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;


import org.jspecify.annotations.Nullable;

import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/**
 * @author Juergen Hoeller
 * @since 6.1
 */
public class TestTransactionExecutionListener implements TransactionExecutionListener {

	public boolean beforeBeginCalled;

	public boolean afterBeginCalled;

	public @Nullable Throwable beginFailure;

	public boolean beforeCommitCalled;

	public boolean afterCommitCalled;

	public @Nullable Throwable commitFailure;

	public boolean beforeRollbackCalled;

	public boolean afterRollbackCalled;

	public @Nullable Throwable rollbackFailure;


		public void beforeBegin( @Nullable TransactionExecution transaction) {
		this.beforeBeginCalled = true;
	}

		public void afterBegin(TransactionExecution transaction, Throwable beginFailure) {
		this.afterBeginCalled = true;
		this.beginFailure = beginFailure;
	}

		public void beforeCommit( @Nullable TransactionExecution transaction) {
		this.beforeCommitCalled = true;
	}

		public void afterCommit(TransactionExecution transaction, Throwable commitFailure) {
		this.afterCommitCalled = true;
		this.commitFailure = commitFailure;
	}

		public void beforeRollback( @Nullable TransactionExecution transaction) {
		this.beforeRollbackCalled = true;
	}

		public void afterRollback(TransactionExecution transaction, Throwable rollbackFailure) {
		this.afterRollbackCalled = true;
		this.rollbackFailure = rollbackFailure;
	}

}
