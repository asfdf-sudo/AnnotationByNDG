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

package org.springframework.transaction.reactive;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;


import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/**
 * Test implementation of a {@link ReactiveTransactionManager}.
 *
 * @author Mark Paluch
 * @author Juergen Hoeller
 */
class ReactiveTestTransactionManager extends AbstractReactiveTransactionManager {

	private static final Object TRANSACTION = "transaction";

	private final boolean existingTransaction;

	private final boolean canCreateTransaction;

	private @Nullable Function<String, RuntimeException> forceFailOnCommit;

	private @Nullable Function<String, RuntimeException> forceFailOnRollback;

	protected boolean begin = false;

	protected boolean commit = false;

	protected boolean rollback = false;

	protected boolean rollbackOnly = false;

	protected boolean cleanup = false;


	ReactiveTestTransactionManager(boolean existingTransaction, boolean canCreateTransaction) {
		this.existingTransaction = existingTransaction;
		this.canCreateTransaction = canCreateTransaction;
	}

	ReactiveTestTransactionManager(boolean existingTransaction, Function<String, RuntimeException> forceFailOnCommit, Function<String, RuntimeException> forceFailOnRollback) {
		this.existingTransaction = existingTransaction;
		this.canCreateTransaction = true;
		this.forceFailOnCommit = forceFailOnCommit;
		this.forceFailOnRollback = forceFailOnRollback;
	}


		protected Object doGetTransaction(TransactionSynchronizationManager synchronizationManager) {
		return TRANSACTION;
	}

		protected boolean isExistingTransaction( @Nullable Object transaction) {
		return this.existingTransaction;
	}

		protected Mono<Void> doBegin( @Nullable TransactionSynchronizationManager synchronizationManager,  @Nullable Object transaction,  @Nullable TransactionDefinition definition) {
		if (!TRANSACTION.equals(transaction)) {
			return Mono.error(new IllegalArgumentException("Not the same transaction object"));
		}
		if (!this.canCreateTransaction) {
			return Mono.error(new CannotCreateTransactionException("Cannot create transaction"));
		}
		return Mono.fromRunnable(() -> this.begin = true);
	}

		protected Mono<Void> doCommit( @Nullable TransactionSynchronizationManager synchronizationManager, GenericReactiveTransaction status) {
		if (!TRANSACTION.equals(status.getTransaction())) {
			return Mono.error(new IllegalArgumentException("Not the same transaction object"));
		}
		return Mono.fromRunnable(() -> {
			this.commit = true;
			if (this.forceFailOnCommit != null) {
				throw this.forceFailOnCommit.apply("Forced failure on commit");
			}
		});
	}

		protected Mono<Void> doRollback( @Nullable TransactionSynchronizationManager synchronizationManager, GenericReactiveTransaction status) {
		if (!TRANSACTION.equals(status.getTransaction())) {
			return Mono.error(new IllegalArgumentException("Not the same transaction object"));
		}
		return Mono.fromRunnable(() -> {
			this.rollback = true;
			if (this.forceFailOnRollback != null) {
				throw this.forceFailOnRollback.apply("Forced failure on rollback");
			}
		});
	}

		protected Mono<Void> doSetRollbackOnly( @Nullable TransactionSynchronizationManager synchronizationManager, GenericReactiveTransaction status) {
		if (!TRANSACTION.equals(status.getTransaction())) {
			return Mono.error(new IllegalArgumentException("Not the same transaction object"));
		}
		return Mono.fromRunnable(() -> this.rollbackOnly = true);
	}

		protected Mono<Void> doCleanupAfterCompletion(TransactionSynchronizationManager synchronizationManager, Object transaction) {
		return Mono.fromRunnable(() -> this.cleanup = true);
	}
}
