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


import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.datasource.lookup.BeanFactoryDataSourceLookup;
import org.springframework.jdbc.datasource.lookup.IsolationLevelDataSourceRouter;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.jta.JtaTransactionObject;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Juergen Hoeller
 * @since 17.10.2005
 */
class DataSourceJtaTransactionTests {

	private DataSource dataSource = mock();

	private Connection connection = mock();

	private UserTransaction userTransaction = mock();

	private TransactionManager transactionManager = mock();

	private Transaction transaction = mock();


		void setup() throws Exception {
		given(dataSource.getConnection()).willReturn(connection);
	}

		void verifyTransactionSynchronizationManagerState() {
		assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
		assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
		assertThat(TransactionSynchronizationManager.getCurrentTransactionName()).isNull();
		assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
		assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isNull();
		assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
	}


		void jtaTransactionCommit() throws Exception {
		doTestJtaTransaction(false);
	}

		void jtaTransactionRollback() throws Exception {
		doTestJtaTransaction(true);
	}

		private void doTestJtaTransaction(final boolean rollback) throws Exception {
		if (rollback) {
			given(userTransaction.getStatus()).willReturn(
					Status.STATUS_NO_TRANSACTION,Status.STATUS_ACTIVE);
		}
		else {
			given(userTransaction.getStatus()).willReturn(
					Status.STATUS_NO_TRANSACTION, Status.STATUS_ACTIVE, Status.STATUS_ACTIVE);
		}

		JtaTransactionManager ptm = new JtaTransactionManager(userTransaction);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
		assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();

		tt.execute(new TransactionCallbackWithoutResult() {
						protected void doInTransactionWithoutResult(TransactionStatus status) throws RuntimeException {
				assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
				assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
				assertThat(status.isNewTransaction()).isTrue();

				Connection con = DataSourceUtils.getConnection(dataSource);
				assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isTrue();
				DataSourceUtils.releaseConnection(con, dataSource);

				con = DataSourceUtils.getConnection(dataSource);
				assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isTrue();
				DataSourceUtils.releaseConnection(con, dataSource);

				if (rollback) {
					status.setRollbackOnly();
				}
			}
		});

		assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
		assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
		verify(userTransaction).begin();
		if (rollback) {
			verify(userTransaction).rollback();
		}
		verify(connection).close();
	}

		void jtaTransactionCommitWithPropagationRequiresNew() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNew(false, false, false, false);
	}

		void jtaTransactionCommitWithPropagationRequiresNewWithAccessAfterResume() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNew(false, false, true, false);
	}

		void jtaTransactionCommitWithPropagationRequiresNewWithOpenOuterConnection() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNew(false, true, false, false);
	}

		void jtaTransactionCommitWithPropagationRequiresNewWithOpenOuterConnectionAccessed() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNew(false, true, true, false);
	}

		void jtaTransactionCommitWithPropagationRequiresNewWithTransactionAwareDataSource() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNew(false, false, true, true);
	}

		void jtaTransactionRollbackWithPropagationRequiresNew() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNew(true, false, false, false);
	}

		void jtaTransactionRollbackWithPropagationRequiresNewWithAccessAfterResume() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNew(true, false, true, false);
	}

		void jtaTransactionRollbackWithPropagationRequiresNewWithOpenOuterConnection() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNew(true, true, false, false);
	}

		void jtaTransactionRollbackWithPropagationRequiresNewWithOpenOuterConnectionAccessed() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNew(true, true, true, false);
	}

		void jtaTransactionRollbackWithPropagationRequiresNewWithTransactionAwareDataSource() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNew(true, false, true, true);
	}

		private void doTestJtaTransactionWithPropagationRequiresNew(
			final boolean rollback, final boolean openOuterConnection, final boolean accessAfterResume,
			final boolean useTransactionAwareDataSource) throws Exception {

		given(transactionManager.suspend()).willReturn(transaction);
		if (rollback) {
			given(userTransaction.getStatus()).willReturn(Status.STATUS_NO_TRANSACTION,
					Status.STATUS_ACTIVE);
		}
		else {
			given(userTransaction.getStatus()).willReturn(Status.STATUS_NO_TRANSACTION,
					Status.STATUS_ACTIVE, Status.STATUS_ACTIVE);
		}

		given(connection.isReadOnly()).willReturn(true);

		final DataSource dsToUse = useTransactionAwareDataSource ?
				new TransactionAwareDataSourceProxy(dataSource) : dataSource;

		JtaTransactionManager ptm = new JtaTransactionManager(userTransaction, transactionManager);
		final TransactionTemplate tt = new TransactionTemplate(ptm);
		tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isFalse();
		assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();

		tt.execute(new TransactionCallbackWithoutResult() {
						protected void doInTransactionWithoutResult(TransactionStatus status) throws RuntimeException {
				assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isFalse();
				assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
				assertThat(status.isNewTransaction()).isTrue();

				Connection con = DataSourceUtils.getConnection(dsToUse);
				try {
					assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isTrue();
					con.isReadOnly();
					DataSourceUtils.releaseConnection(con, dsToUse);

					con = DataSourceUtils.getConnection(dsToUse);
					assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isTrue();
					if (!openOuterConnection) {
						DataSourceUtils.releaseConnection(con, dsToUse);
					}
				}
				catch (SQLException ex) {
				}

				for (int i = 0; i < 5; i++) {

					tt.execute(new TransactionCallbackWithoutResult() {
												protected void doInTransactionWithoutResult(TransactionStatus status) throws RuntimeException {
							assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isFalse();
							assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
							assertThat(status.isNewTransaction()).isTrue();

							try {
								Connection con = DataSourceUtils.getConnection(dsToUse);
								con.isReadOnly();
								assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isTrue();
								DataSourceUtils.releaseConnection(con, dsToUse);

								con = DataSourceUtils.getConnection(dsToUse);
								assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isTrue();
								DataSourceUtils.releaseConnection(con, dsToUse);
							}
							catch (SQLException ex) {
							}
						}
					});

				}

				if (rollback) {
					status.setRollbackOnly();
				}

				if (accessAfterResume) {
					try {
						if (!openOuterConnection) {
							con = DataSourceUtils.getConnection(dsToUse);
						}
						assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isTrue();
						con.isReadOnly();
						DataSourceUtils.releaseConnection(con, dsToUse);

						con = DataSourceUtils.getConnection(dsToUse);
						assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isTrue();
						DataSourceUtils.releaseConnection(con, dsToUse);
					}
					catch (SQLException ex) {
					}
				}

				else {
					if (openOuterConnection) {
						DataSourceUtils.releaseConnection(con, dsToUse);
					}
				}
			}
		});

		assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isFalse();
		assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
		verify(userTransaction, times(6)).begin();
		verify(transactionManager, times(5)).resume(transaction);
		if (rollback) {
			verify(userTransaction, times(5)).commit();
			verify(userTransaction).rollback();
		}
		else {
			verify(userTransaction, times(6)).commit();
		}
		if (accessAfterResume && !openOuterConnection) {
			verify(connection, times(7)).close();
		}
		else {
			verify(connection, times(6)).close();
		}
	}

		void jtaTransactionCommitWithPropagationRequiredWithinSupports() throws Exception {
		doTestJtaTransactionCommitWithNewTransactionWithinEmptyTransaction(false, false);
	}

		void jtaTransactionCommitWithPropagationRequiredWithinNotSupported() throws Exception {
		doTestJtaTransactionCommitWithNewTransactionWithinEmptyTransaction(false, true);
	}

		void jtaTransactionCommitWithPropagationRequiresNewWithinSupports() throws Exception {
		doTestJtaTransactionCommitWithNewTransactionWithinEmptyTransaction(true, false);
	}

		void jtaTransactionCommitWithPropagationRequiresNewWithinNotSupported() throws Exception {
		doTestJtaTransactionCommitWithNewTransactionWithinEmptyTransaction(true, true);
	}

		private void doTestJtaTransactionCommitWithNewTransactionWithinEmptyTransaction(
			final boolean requiresNew, boolean notSupported) throws Exception {

		if (notSupported) {
			given(userTransaction.getStatus()).willReturn(
					Status.STATUS_ACTIVE,
					Status.STATUS_NO_TRANSACTION,
					Status.STATUS_ACTIVE,
					Status.STATUS_ACTIVE);
			given(transactionManager.suspend()).willReturn(transaction);
		}
		else {
			given(userTransaction.getStatus()).willReturn(
					Status.STATUS_NO_TRANSACTION,
					Status.STATUS_NO_TRANSACTION,
					Status.STATUS_ACTIVE,
					Status.STATUS_ACTIVE);
		}

		final DataSource dataSource = mock();
		final Connection connection1 = mock();
		final Connection connection2 = mock();
		given(dataSource.getConnection()).willReturn(connection1, connection2);

		final JtaTransactionManager ptm = new JtaTransactionManager(userTransaction, transactionManager);
		TransactionTemplate tt = new TransactionTemplate(ptm);
		tt.setPropagationBehavior(notSupported ?
				TransactionDefinition.PROPAGATION_NOT_SUPPORTED : TransactionDefinition.PROPAGATION_SUPPORTS);

		assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
		tt.execute(new TransactionCallbackWithoutResult() {
						protected void doInTransactionWithoutResult(TransactionStatus status) {
				assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
				assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
				assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
				assertThat(DataSourceUtils.getConnection(dataSource)).isSameAs(connection1);
				assertThat(DataSourceUtils.getConnection(dataSource)).isSameAs(connection1);

				TransactionTemplate tt2 = new TransactionTemplate(ptm);
				tt2.setPropagationBehavior(requiresNew ?
						TransactionDefinition.PROPAGATION_REQUIRES_NEW : TransactionDefinition.PROPAGATION_REQUIRED);
				tt2.execute(new TransactionCallbackWithoutResult() {
										protected void doInTransactionWithoutResult(TransactionStatus status) {
						assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
						assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
						assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
						assertThat(DataSourceUtils.getConnection(dataSource)).isSameAs(connection2);
						assertThat(DataSourceUtils.getConnection(dataSource)).isSameAs(connection2);
					}
				});

				assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
				assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
				assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
				assertThat(DataSourceUtils.getConnection(dataSource)).isSameAs(connection1);
			}
		});
		assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
		verify(userTransaction).begin();
		verify(userTransaction).commit();
		if (notSupported) {
			verify(transactionManager).resume(transaction);
		}
		verify(connection2).close();
		verify(connection1).close();
	}

		void jtaTransactionCommitWithPropagationRequiresNewAndSuspendException() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNewAndBeginException(true, false, false);
	}

		void jtaTransactionCommitWithPropagationRequiresNewWithOpenOuterConnectionAndSuspendException() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNewAndBeginException(true, true, false);
	}

		void jtaTransactionCommitWithPropagationRequiresNewWithTransactionAwareDataSourceAndSuspendException() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNewAndBeginException(true, false, true);
	}

		void jtaTransactionCommitWithPropagationRequiresNewWithOpenOuterConnectionAndTransactionAwareDataSourceAndSuspendException() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNewAndBeginException(true, true, true);
	}

		void jtaTransactionCommitWithPropagationRequiresNewAndBeginException() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNewAndBeginException(false, false, false);
	}

		void jtaTransactionCommitWithPropagationRequiresNewWithOpenOuterConnectionAndBeginException() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNewAndBeginException(false, true, false);
	}

		void jtaTransactionCommitWithPropagationRequiresNewWithOpenOuterConnectionAndTransactionAwareDataSourceAndBeginException() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNewAndBeginException(false, true, true);
	}

		void jtaTransactionCommitWithPropagationRequiresNewWithTransactionAwareDataSourceAndBeginException() throws Exception {
		doTestJtaTransactionWithPropagationRequiresNewAndBeginException(false, false, true);
	}

		private void doTestJtaTransactionWithPropagationRequiresNewAndBeginException(boolean suspendException,
			final boolean openOuterConnection, final boolean useTransactionAwareDataSource) throws Exception {

		given(userTransaction.getStatus()).willReturn(
				Status.STATUS_NO_TRANSACTION,
				Status.STATUS_ACTIVE,
				Status.STATUS_ACTIVE);
		if (suspendException) {
			given(transactionManager.suspend()).willThrow(new SystemException());
		}
		else {
			given(transactionManager.suspend()).willReturn(transaction);
			willThrow(new SystemException()).given(userTransaction).begin();
		}

		given(connection.isReadOnly()).willReturn(true);

		final DataSource dsToUse = useTransactionAwareDataSource ?
				new TransactionAwareDataSourceProxy(dataSource) : dataSource;
		if (dsToUse instanceof TransactionAwareDataSourceProxy) {
			((TransactionAwareDataSourceProxy) dsToUse).setReobtainTransactionalConnections(true);
		}

		JtaTransactionManager ptm = new JtaTransactionManager(userTransaction, transactionManager);
		final TransactionTemplate tt = new TransactionTemplate(ptm);
		tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isFalse();
		assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();

		assertThatExceptionOfType(TransactionException.class).isThrownBy(() ->
			tt.execute(new TransactionCallbackWithoutResult() {

								protected void doInTransactionWithoutResult(TransactionStatus status) throws RuntimeException {
					assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isFalse();
					assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
					assertThat(status.isNewTransaction()).isTrue();

					Connection con = DataSourceUtils.getConnection(dsToUse);
					try {
						assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isTrue();
						con.isReadOnly();
						DataSourceUtils.releaseConnection(con, dsToUse);

						con = DataSourceUtils.getConnection(dsToUse);
						assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isTrue();
						if (!openOuterConnection) {
							DataSourceUtils.releaseConnection(con, dsToUse);
						}
					}
					catch (SQLException ex) {
					}

					try {
						tt.execute(new TransactionCallbackWithoutResult() {
														protected void doInTransactionWithoutResult(TransactionStatus status) throws RuntimeException {
								assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isFalse();
								assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
								assertThat(status.isNewTransaction()).isTrue();

								Connection con = DataSourceUtils.getConnection(dsToUse);
								assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isTrue();
								DataSourceUtils.releaseConnection(con, dsToUse);

								con = DataSourceUtils.getConnection(dsToUse);
								assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isTrue();
								DataSourceUtils.releaseConnection(con, dsToUse);
							}
						});
					}
					finally {
						if (openOuterConnection) {
							try {
								con.isReadOnly();
								DataSourceUtils.releaseConnection(con, dsToUse);
							}
							catch (SQLException ex) {
							}
						}
					}
				}
			}));

		assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isFalse();
		assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();

		verify(userTransaction).begin();
		if (suspendException) {
			verify(userTransaction).rollback();
		}

		if (suspendException) {
			verify(connection, atLeastOnce()).close();
		}
		else {
			verify(connection, never()).close();
		}
	}

			void jtaTransactionWithConnectionHolderStillBound() throws Exception {
				JtaTransactionManager ptm = new JtaTransactionManager(userTransaction) {

						protected void doRegisterAfterCompletionWithJtaTransaction(
					JtaTransactionObject txObject,
					final List<TransactionSynchronization> synchronizations) {
				Thread async = new Thread(() -> invokeAfterCompletion(
						synchronizations, TransactionSynchronization.STATUS_COMMITTED));
				async.start();
				try {
					async.join();
				}
				catch (InterruptedException ex) {
					ex.printStackTrace();
				}
			}
		};
		TransactionTemplate tt = new TransactionTemplate(ptm);
		assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
		assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();

		given(userTransaction.getStatus()).willReturn(Status.STATUS_ACTIVE);
		for (int i = 0; i < 3; i++) {
			final boolean releaseCon = (i != 1);

			tt.execute(new TransactionCallbackWithoutResult() {
								protected void doInTransactionWithoutResult(TransactionStatus status) throws RuntimeException {
					assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
					assertThat(status.isNewTransaction()).isFalse();

					Connection con = DataSourceUtils.getConnection(dataSource);
					assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isTrue();
					DataSourceUtils.releaseConnection(con, dataSource);

					con = DataSourceUtils.getConnection(dataSource);
					assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isTrue();
					if (releaseCon) {
						DataSourceUtils.releaseConnection(con, dataSource);
					}
				}
			});

			if (!releaseCon) {
				assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isTrue();
			}
			else {
				assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
			}
			assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
		}
		verify(connection, times(3)).close();
	}

			void jtaTransactionWithIsolationLevelDataSourceAdapter() throws Exception {
		given(userTransaction.getStatus()).willReturn(
				Status.STATUS_NO_TRANSACTION,
				Status.STATUS_ACTIVE,
				Status.STATUS_ACTIVE,
				Status.STATUS_NO_TRANSACTION,
				Status.STATUS_ACTIVE,
				Status.STATUS_ACTIVE);

		final IsolationLevelDataSourceAdapter dsToUse = new IsolationLevelDataSourceAdapter();
		dsToUse.setTargetDataSource(dataSource);
		dsToUse.afterPropertiesSet();

		JtaTransactionManager ptm = new JtaTransactionManager(userTransaction);
		ptm.setAllowCustomIsolationLevels(true);

		TransactionTemplate tt = new TransactionTemplate(ptm);
		tt.execute(new TransactionCallbackWithoutResult() {
						protected void doInTransactionWithoutResult(TransactionStatus status) throws RuntimeException {
				Connection con = DataSourceUtils.getConnection(dsToUse);
				assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isTrue();
				assertThat(con).isSameAs(connection);
				DataSourceUtils.releaseConnection(con, dsToUse);
			}
		});

		tt.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		tt.setReadOnly(true);
		tt.execute(new TransactionCallbackWithoutResult() {
						protected void doInTransactionWithoutResult(TransactionStatus status) throws RuntimeException {
				Connection con = DataSourceUtils.getConnection(dsToUse);
				assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isTrue();
				assertThat(con).isSameAs(connection);
				DataSourceUtils.releaseConnection(con, dsToUse);
			}
		});

		verify(userTransaction, times(2)).begin();
		verify(userTransaction, times(2)).commit();
		verify(connection).setReadOnly(true);
		verify(connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
		verify(connection, times(2)).close();
	}

		void jtaTransactionWithIsolationLevelDataSourceRouter() throws Exception {
		doTestJtaTransactionWithIsolationLevelDataSourceRouter(false);
	}

		void jtaTransactionWithIsolationLevelDataSourceRouterWithDataSourceLookup() throws Exception {
		doTestJtaTransactionWithIsolationLevelDataSourceRouter(true);
	}

		private void doTestJtaTransactionWithIsolationLevelDataSourceRouter(boolean dataSourceLookup) throws Exception {
		given(userTransaction.getStatus())
			.willReturn(Status.STATUS_NO_TRANSACTION, Status.STATUS_ACTIVE, Status.STATUS_ACTIVE, Status.STATUS_NO_TRANSACTION, Status.STATUS_ACTIVE, Status.STATUS_ACTIVE);

		final DataSource dataSource1 = mock();
		final Connection connection1 = mock();
		given(dataSource1.getConnection()).willReturn(connection1);

		final DataSource dataSource2 = mock();
		final Connection connection2 = mock();
		given(dataSource2.getConnection()).willReturn(connection2);

		final IsolationLevelDataSourceRouter dsToUse = new IsolationLevelDataSourceRouter();
		Map<Object, Object> targetDataSources = new HashMap<>();
		if (dataSourceLookup) {
			targetDataSources.put("ISOLATION_REPEATABLE_READ", "ds2");
			dsToUse.setDefaultTargetDataSource("ds1");
			StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
			beanFactory.addBean("ds1", dataSource1);
			beanFactory.addBean("ds2", dataSource2);
			dsToUse.setDataSourceLookup(new BeanFactoryDataSourceLookup(beanFactory));
		}
		else {
			targetDataSources.put("ISOLATION_REPEATABLE_READ", dataSource2);
			dsToUse.setDefaultTargetDataSource(dataSource1);
		}
		dsToUse.setTargetDataSources(targetDataSources);
		dsToUse.afterPropertiesSet();

		JtaTransactionManager ptm = new JtaTransactionManager(userTransaction);
		ptm.setAllowCustomIsolationLevels(true);

		TransactionTemplate tt = new TransactionTemplate(ptm);
		tt.execute(new TransactionCallbackWithoutResult() {
						protected void doInTransactionWithoutResult(TransactionStatus status) throws RuntimeException {
				Connection con = DataSourceUtils.getConnection(dsToUse);
				assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isTrue();
				assertThat(con).isSameAs(connection1);
				DataSourceUtils.releaseConnection(con, dsToUse);
			}
		});

		tt.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		tt.execute(new TransactionCallbackWithoutResult() {
						protected void doInTransactionWithoutResult(TransactionStatus status) throws RuntimeException {
				Connection con = DataSourceUtils.getConnection(dsToUse);
				assertThat(TransactionSynchronizationManager.hasResource(dsToUse)).isTrue();
				assertThat(con).isSameAs(connection2);
				DataSourceUtils.releaseConnection(con, dsToUse);
			}
		});

		verify(userTransaction, times(2)).begin();
		verify(userTransaction, times(2)).commit();
		verify(connection1).close();
		verify(connection2).close();
	}

}
