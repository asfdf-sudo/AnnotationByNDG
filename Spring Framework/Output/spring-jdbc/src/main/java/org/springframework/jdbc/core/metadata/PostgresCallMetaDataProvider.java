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

package org.springframework.jdbc.core.metadata;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;


import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;

import org.jspecify.annotations.Nullable;

import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;

/**
 * Postgres-specific implementation for the {@link CallMetaDataProvider} interface.
 * This class is intended for internal use by the Simple JDBC classes.
 *
 * @author Thomas Risberg
 * @author Juergen Hoeller
 * @since 2.5
 */
public class PostgresCallMetaDataProvider extends GenericCallMetaDataProvider {

	private static final String RETURN_VALUE_NAME = "returnValue";

	private final String schemaName;


	public PostgresCallMetaDataProvider(DatabaseMetaData databaseMetaData) throws SQLException {
		super(databaseMetaData);

		// Use current schema (or public schema) if no schema specified
		String schema = databaseMetaData.getConnection().getSchema();
		this.schemaName = (schema != null ? schema : "public");
	}


		public boolean isReturnResultSetSupported() {
		return false;
	}

		public boolean isRefCursorSupported() {
		return true;
	}

		public int getRefCursorSqlType() {
		return Types.OTHER;
	}

		public String metaDataSchemaNameToUse( @Nullable String schemaName) {
		return (schemaName == null ? this.schemaName : super.metaDataSchemaNameToUse(schemaName));
	}

		public SqlParameter createDefaultOutParameter(String parameterName, CallParameterMetaData meta) {
		if (meta.getSqlType() == Types.OTHER && "refcursor".equals(meta.getTypeName())) {
			return new SqlOutParameter(parameterName, getRefCursorSqlType(), new ColumnMapRowMapper());
		}
		else {
			return super.createDefaultOutParameter(parameterName, meta);
		}
	}

		public boolean byPassReturnParameter(String parameterName) {
		return RETURN_VALUE_NAME.equals(parameterName);
	}

}
