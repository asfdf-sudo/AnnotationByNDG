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


import reactor.core.publisher.Mono;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.reactive.AbstractReactiveTransactionManager;
import org.springframework.transaction.reactive.GenericReactiveTransaction;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;

/**
 * @author Mark Paluch
 */
public class ReactiveCallCountingTransactionManager extends AbstractReactiveTransactionManager {

	public TransactionDefinition lastDefinition;
	public int begun;
	public int commits;
	public int rollbacks;
	public int inflight;

		protected Object doGetTransaction(TransactionSynchronizationManager synchronizationManager) throws TransactionException {
		return new Object();
	}

		protected Mono<Void> doBegin( @Nullable TransactionSynchronizationManager synchronizationManager,  @Nullable Object transaction, TransactionDefinition definition) throws TransactionException {
		this.lastDefinition = definition;
		++begun;
		++inflight;
		return Mono.empty();
	}

		protected Mono<Void> doCommit(TransactionSynchronizationManager synchronizationManager, GenericReactiveTransaction status) throws TransactionException {
		++commits;
		--inflight;
		return Mono.empty();
	}

		protected Mono<Void> doRollback( @Nullable TransactionSynchronizationManager synchronizationManager,  @Nullable GenericReactiveTransaction status) throws TransactionException {
		++rollbacks;
		--inflight;
		return Mono.empty();
	}


	public void clear() {
		begun = commits = rollbacks = inflight = 0;
	}

}
