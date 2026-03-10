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

package org.springframework.jdbc.core.simple;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeanUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SimplePropertyRowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SimplePropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.JdbcAccessor;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.util.Assert;

/**
 * The default implementation of {@link JdbcClient},
 * as created by the static factory methods.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 6.1
 * @see JdbcClient#create(DataSource)
 * @see JdbcClient#create(JdbcOperations)
 * @see JdbcClient#create(NamedParameterJdbcOperations)
 */
final class DefaultJdbcClient implements JdbcClient {

	private final NamedParameterJdbcOperations namedParamOps;

	private final ConversionService conversionService;

	private final Map<Class<?>, RowMapper<?>> rowMapperCache = new ConcurrentHashMap<>();


	public DefaultJdbcClient(DataSource dataSource) {
		this(new JdbcTemplate(dataSource));
	}

	public DefaultJdbcClient(JdbcOperations jdbcTemplate) {
		this(new NamedParameterJdbcTemplate(jdbcTemplate), null);
	}

	public DefaultJdbcClient(NamedParameterJdbcOperations jdbcTemplate, ConversionService conversionService) {
		Assert.notNull(jdbcTemplate, "JdbcTemplate must not be null");
		this.namedParamOps = jdbcTemplate;
		this.conversionService =
				(conversionService != null ? conversionService : DefaultConversionService.getSharedInstance());
	}


		public StatementSpec sql(String sql) {
		return new DefaultStatementSpec(sql, this.namedParamOps);
	}


	private class DefaultStatementSpec implements StatementSpec {

		private final String sql;

		private JdbcOperations classicOps;

		private NamedParameterJdbcOperations namedParamOps;

		private @Nullable JdbcTemplate customTemplate;

		private final List<@Nullable Object> indexedParams = new ArrayList<>();

		private final MapSqlParameterSource namedParams = new MapSqlParameterSource();

		private SqlParameterSource namedParamSource = this.namedParams;

		public DefaultStatementSpec(String sql, NamedParameterJdbcOperations namedParamOps) {
			this.sql = sql;
			this.classicOps = namedParamOps.getJdbcOperations();
			this.namedParamOps = namedParamOps;
		}

		private JdbcTemplate enforceCustomTemplate() {
			if (this.customTemplate == null) {
				if (!(this.classicOps instanceof JdbcAccessor original)) {
					throw new IllegalStateException(
							"Needs to be bound to a JdbcAccessor for custom settings support: " + this.classicOps);
				}
				this.customTemplate = new JdbcTemplate(original);
				this.classicOps = this.customTemplate;
				this.namedParamOps = (this.namedParamOps instanceof NamedParameterJdbcTemplate originalNamedParam ?
						new NamedParameterJdbcTemplate(originalNamedParam, this.customTemplate) :
						new NamedParameterJdbcTemplate(this.customTemplate));
			}
			return this.customTemplate;
		}

				public StatementSpec withFetchSize(int fetchSize) {
			enforceCustomTemplate().setFetchSize(fetchSize);
			return this;
		}

				public StatementSpec withMaxRows(int maxRows) {
			enforceCustomTemplate().setMaxRows(maxRows);
			return this;
		}

				public StatementSpec withQueryTimeout(int queryTimeout) {
			enforceCustomTemplate().setQueryTimeout(queryTimeout);
			return this;
		}

				public StatementSpec param(Object value) {
			validateIndexedParamValue(value);
			this.indexedParams.add(value);
			return this;
		}

				public StatementSpec param(int jdbcIndex, Object value) {
			if (jdbcIndex < 1) {
				throw new IllegalArgumentException("Invalid JDBC index: needs to start at 1");
			}
			validateIndexedParamValue(value);
			int index = jdbcIndex - 1;
			int size = this.indexedParams.size();
			if (index < size) {
				this.indexedParams.set(index, value);
			}
			else {
				for (int i = size; i < index; i++) {
					this.indexedParams.add(null);
				}
				this.indexedParams.add(value);
			}
			return this;
		}

		private void validateIndexedParamValue(Object value) {
			if (value instanceof Iterable) {
				throw new IllegalArgumentException("Invalid positional parameter value of type Iterable (" +
						value.getClass().getSimpleName() +
						"): Parameter expansion is only supported with named parameters.");
			}
		}

				public StatementSpec param(int jdbcIndex, Object value, int sqlType) {
			return param(jdbcIndex, new SqlParameterValue(sqlType, value));
		}

				public StatementSpec param(String name, Object value) {
			this.namedParams.addValue(name, value);
			return this;
		}

				public StatementSpec param(String name, Object value, int sqlType) {
			this.namedParams.addValue(name, value, sqlType);
			return this;
		}

				public StatementSpec params(Object... values) {
			Collections.addAll(this.indexedParams, values);
			return this;
		}

				public StatementSpec params(List<?> values) {
			this.indexedParams.addAll(values);
			return this;
		}

				public StatementSpec params(Map<String, ?> paramMap) {
			this.namedParams.addValues(paramMap);
			return this;
		}

						public StatementSpec paramSource(Object namedParamObject) {
			this.namedParamSource = (namedParamObject instanceof Map map ?
					new MapSqlParameterSource(map) :
					new SimplePropertySqlParameterSource(namedParamObject));
			return this;
		}

				public StatementSpec paramSource(SqlParameterSource namedParamSource) {
			this.namedParamSource = namedParamSource;
			return this;
		}

				public ResultQuerySpec query() {
			return (useNamedParams() ?
					new NamedParamResultQuerySpec() :
					new IndexedParamResultQuerySpec());
		}

						public <T> MappedQuerySpec<T> query(Class<T> mappedClass) {
			RowMapper<?> rowMapper = rowMapperCache.computeIfAbsent(mappedClass, key ->
					BeanUtils.isSimpleProperty(mappedClass) ?
							new SingleColumnRowMapper<>(mappedClass, conversionService) :
							new SimplePropertyRowMapper<>(mappedClass, conversionService));
			return query((RowMapper<T>) rowMapper);
		}

				public <T extends Object> MappedQuerySpec<T> query(RowMapper<T> rowMapper) {
			return (useNamedParams() ?
					new NamedParamMappedQuerySpec<>(rowMapper) :
					new IndexedParamMappedQuerySpec<>(rowMapper));
		}

				public void query(RowCallbackHandler rch) {
			if (useNamedParams()) {
				this.namedParamOps.query(this.sql, this.namedParamSource, rch);
			}
			else {
				this.classicOps.query(statementCreatorForIndexedParams(), rch);
			}
		}

				public <T extends Object> T query(ResultSetExtractor<T> rse) {
			T result = (useNamedParams() ?
					this.namedParamOps.query(this.sql, this.namedParamSource, rse) :
					this.classicOps.query(statementCreatorForIndexedParams(), rse));
			Assert.state(result != null, "No result from ResultSetExtractor");
			return result;
		}

				public int update() {
			return (useNamedParams() ?
					this.namedParamOps.update(this.sql, this.namedParamSource) :
					this.classicOps.update(statementCreatorForIndexedParams()));
		}

				public int update(KeyHolder generatedKeyHolder) {
			return (useNamedParams() ?
					this.namedParamOps.update(this.sql, this.namedParamSource, generatedKeyHolder) :
					this.classicOps.update(statementCreatorForIndexedParamsWithKeys(null), generatedKeyHolder));
		}

				public int update(KeyHolder generatedKeyHolder, String... keyColumnNames) {
			return (useNamedParams() ?
					this.namedParamOps.update(this.sql, this.namedParamSource, generatedKeyHolder, keyColumnNames) :
					this.classicOps.update(statementCreatorForIndexedParamsWithKeys(keyColumnNames), generatedKeyHolder));
		}

		private boolean useNamedParams() {
			boolean hasNamedParams = (this.namedParams.hasValues() || this.namedParamSource != this.namedParams);
			if (hasNamedParams && !this.indexedParams.isEmpty()) {
				throw new IllegalStateException("Configure either named or indexed parameters, not both");
			}
			if (this.namedParams.hasValues() && this.namedParamSource != this.namedParams) {
				throw new IllegalStateException(
						"Configure either individual named parameters or a SqlParameterSource, not both");
			}
			return hasNamedParams;
		}

		private PreparedStatementCreator statementCreatorForIndexedParams() {
			return new PreparedStatementCreatorFactory(this.sql).newPreparedStatementCreator(this.indexedParams);
		}

		private PreparedStatementCreator statementCreatorForIndexedParamsWithKeys(String [] keyColumnNames) {
			PreparedStatementCreatorFactory pscf = new PreparedStatementCreatorFactory(this.sql);
			if (keyColumnNames != null) {
				pscf.setGeneratedKeysColumnNames(keyColumnNames);
			}
			else {
				pscf.setReturnGeneratedKeys(true);
			}
			return pscf.newPreparedStatementCreator(this.indexedParams);
		}


		private class IndexedParamResultQuerySpec implements ResultQuerySpec {

						public SqlRowSet rowSet() {
				return classicOps.queryForRowSet(sql, indexedParams.toArray());
			}

						public List<Map<String, Object>> listOfRows() {
				return classicOps.queryForList(sql, indexedParams.toArray());
			}

						public Map<String, Object> singleRow() {
				return classicOps.queryForMap(sql, indexedParams.toArray());
			}

						public List<Object> singleColumn() {
				return classicOps.queryForList(sql, Object.class, indexedParams.toArray());
			}
		}


		private class NamedParamResultQuerySpec implements ResultQuerySpec {

						public SqlRowSet rowSet() {
				return namedParamOps.queryForRowSet(sql, namedParamSource);
			}

						public List<Map<String, Object>> listOfRows() {
				return namedParamOps.queryForList(sql, namedParamSource);
			}

						public Map<String, Object> singleRow() {
				return namedParamOps.queryForMap(sql, namedParamSource);
			}

						public List<Object> singleColumn() {
				return namedParamOps.queryForList(sql, namedParamSource, Object.class);
			}
		}


		private class IndexedParamMappedQuerySpec<T extends Object> implements MappedQuerySpec<T> {

			private final RowMapper<T> rowMapper;

			public IndexedParamMappedQuerySpec(RowMapper<T> rowMapper) {
				this.rowMapper = rowMapper;
			}

						public Stream<T> stream() {
				return classicOps.queryForStream(sql, this.rowMapper, indexedParams.toArray());
			}

						public List<T> list() {
				return classicOps.query(sql, this.rowMapper, indexedParams.toArray());
			}
		}


		private class NamedParamMappedQuerySpec<T extends Object> implements MappedQuerySpec<T> {

			private final RowMapper<T> rowMapper;

			public NamedParamMappedQuerySpec(RowMapper<T> rowMapper) {
				this.rowMapper = rowMapper;
			}

						public Stream<T> stream() {
				return namedParamOps.queryForStream(sql, namedParamSource, this.rowMapper);
			}

						public List<T> list() {
				return namedParamOps.query(sql, namedParamSource, this.rowMapper);
			}
		}
	}

}
