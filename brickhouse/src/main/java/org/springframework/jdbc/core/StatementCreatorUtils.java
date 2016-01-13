package org.springframework.jdbc.core;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.Calendar;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StatementCreatorUtils {
	static final Set<String> driversWithNoSupportForGetParameterType = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>(1));
	
	public static void setParameterValue(PreparedStatement ps, int paramIndex, int sqlType, Object inValue) throws SQLException {
		setParameterValueInternal(ps, paramIndex, sqlType, null, null, inValue);
	}
	
	private static void setParameterValueInternal(PreparedStatement ps, int paramIndex, int sqlType, String typeName, Integer scale, Object inValue) throws SQLException {
		String typeNameToUse = typeName;
		int sqlTypeToUse = sqlType;
		Object inValueToUse = inValue;

		if (inValueToUse == null) {
			setNull(ps, paramIndex, sqlTypeToUse, typeNameToUse);
		}
		else {
			setValue(ps, paramIndex, sqlTypeToUse, typeNameToUse, scale, inValueToUse);
		}
	}
	
	private static void setNull(PreparedStatement ps, int paramIndex, int sqlType, String typeName) throws SQLException {
		if (sqlType == SqlTypeValue.TYPE_UNKNOWN || sqlType == Types.OTHER) {
			boolean useSetObject = false;
			Integer sqlTypeToUse = null;
			DatabaseMetaData dbmd = null;
			String jdbcDriverName = null;
			boolean checkGetParameterType = true;
			if (checkGetParameterType && !driversWithNoSupportForGetParameterType.isEmpty()) {
				try {
					dbmd = ps.getConnection().getMetaData();
					jdbcDriverName = dbmd.getDriverName();
					checkGetParameterType = !driversWithNoSupportForGetParameterType.contains(jdbcDriverName);
				}
				catch (Throwable ex) { }
			}
			if (checkGetParameterType) {
				try {
					sqlTypeToUse = ps.getParameterMetaData().getParameterType(paramIndex);
				}
				catch (Throwable ex) { }
			}
			if (sqlTypeToUse == null) {
				// JDBC driver not compliant with JDBC 3.0 -> proceed with database-specific checks
				sqlTypeToUse = Types.NULL;
				try {
					if (dbmd == null) {
						dbmd = ps.getConnection().getMetaData();
					}
					if (jdbcDriverName == null) {
						jdbcDriverName = dbmd.getDriverName();
					}
					if (checkGetParameterType) {
						driversWithNoSupportForGetParameterType.add(jdbcDriverName);
					}
					String databaseProductName = dbmd.getDatabaseProductName();
					if (databaseProductName.startsWith("Informix") ||
							jdbcDriverName.startsWith("Microsoft SQL Server")) {
						useSetObject = true;
					}
					else if (databaseProductName.startsWith("DB2") ||
							jdbcDriverName.startsWith("jConnect") ||
							jdbcDriverName.startsWith("SQLServer")||
							jdbcDriverName.startsWith("Apache Derby")) {
						sqlTypeToUse = Types.VARCHAR;
					}
				}
				catch (Throwable ex) { }
			}
			if (useSetObject) {
				ps.setObject(paramIndex, null);
			}
			else {
				ps.setNull(paramIndex, sqlTypeToUse);
			}
		}
		else if (typeName != null) {
			ps.setNull(paramIndex, sqlType, typeName);
		}
		else {
			ps.setNull(paramIndex, sqlType);
		}
	}

	private static void setValue(PreparedStatement ps, int paramIndex, int sqlType, String typeName, Integer scale, Object inValue) throws SQLException {
		if (sqlType == Types.VARCHAR || sqlType == Types.NVARCHAR ||
				sqlType == Types.LONGVARCHAR || sqlType == Types.LONGNVARCHAR) {
			ps.setString(paramIndex, inValue.toString());
		}
		else if ((sqlType == Types.CLOB || sqlType == Types.NCLOB) && isStringValue(inValue.getClass())) {
			String strVal = inValue.toString();
			if (strVal.length() > 4000) {
				// Necessary for older Oracle drivers, in particular when running against an Oracle 10 database.
				// Should also work fine against other drivers/databases since it uses standard JDBC 4.0 API.
				try {
					if (sqlType == Types.NCLOB) {
						ps.setNClob(paramIndex, new StringReader(strVal), strVal.length());
					}
					else {
						ps.setClob(paramIndex, new StringReader(strVal), strVal.length());
					}
					return;
				}
				catch (AbstractMethodError err) { }
				catch (SQLFeatureNotSupportedException ex) { }
			}
			// Fallback: regular setString binding
			ps.setString(paramIndex, strVal);
		}
		else if (sqlType == Types.DECIMAL || sqlType == Types.NUMERIC) {
			if (inValue instanceof BigDecimal) {
				ps.setBigDecimal(paramIndex, (BigDecimal) inValue);
			}
			else if (scale != null) {
				ps.setObject(paramIndex, inValue, sqlType, scale);
			}
			else {
				ps.setObject(paramIndex, inValue, sqlType);
			}
		}
		else if (sqlType == Types.DATE) {
			if (inValue instanceof java.util.Date) {
				if (inValue instanceof java.sql.Date) {
					ps.setDate(paramIndex, (java.sql.Date) inValue);
				}
				else {
					ps.setDate(paramIndex, new java.sql.Date(((java.util.Date) inValue).getTime()));
				}
			}
			else if (inValue instanceof Calendar) {
				Calendar cal = (Calendar) inValue;
				ps.setDate(paramIndex, new java.sql.Date(cal.getTime().getTime()), cal);
			}
			else {
				ps.setObject(paramIndex, inValue, Types.DATE);
			}
		}
		else if (sqlType == Types.TIME) {
			if (inValue instanceof java.util.Date) {
				if (inValue instanceof java.sql.Time) {
					ps.setTime(paramIndex, (java.sql.Time) inValue);
				}
				else {
					ps.setTime(paramIndex, new java.sql.Time(((java.util.Date) inValue).getTime()));
				}
			}
			else if (inValue instanceof Calendar) {
				Calendar cal = (Calendar) inValue;
				ps.setTime(paramIndex, new java.sql.Time(cal.getTime().getTime()), cal);
			}
			else {
				ps.setObject(paramIndex, inValue, Types.TIME);
			}
		}
		else if (sqlType == Types.TIMESTAMP) {
			if (inValue instanceof java.util.Date) {
				if (inValue instanceof java.sql.Timestamp) {
					ps.setTimestamp(paramIndex, (java.sql.Timestamp) inValue);
				}
				else {
					ps.setTimestamp(paramIndex, new java.sql.Timestamp(((java.util.Date) inValue).getTime()));
				}
			}
			else if (inValue instanceof Calendar) {
				Calendar cal = (Calendar) inValue;
				ps.setTimestamp(paramIndex, new java.sql.Timestamp(cal.getTime().getTime()), cal);
			}
			else {
				ps.setObject(paramIndex, inValue, Types.TIMESTAMP);
			}
		}
		else if (sqlType == SqlTypeValue.TYPE_UNKNOWN || sqlType == Types.OTHER) {
			if (isStringValue(inValue.getClass())) {
				ps.setString(paramIndex, inValue.toString());
			}
			else if (isDateValue(inValue.getClass())) {
				ps.setTimestamp(paramIndex, new java.sql.Timestamp(((java.util.Date) inValue).getTime()));
			}
			else if (inValue instanceof Calendar) {
				Calendar cal = (Calendar) inValue;
				ps.setTimestamp(paramIndex, new java.sql.Timestamp(cal.getTime().getTime()), cal);
			}
			else {
				// Fall back to generic setObject call without SQL type specified.
				ps.setObject(paramIndex, inValue);
			}
		}
		else {
			// Fall back to generic setObject call with SQL type specified.
			ps.setObject(paramIndex, inValue, sqlType);
		}
	}

	private static boolean isStringValue(Class<?> inValueType) {
		// Consider any CharSequence (including StringBuffer and StringBuilder) as a String.
		return (CharSequence.class.isAssignableFrom(inValueType) ||
				StringWriter.class.isAssignableFrom(inValueType));
	}

	private static boolean isDateValue(Class<?> inValueType) {
		return (java.util.Date.class.isAssignableFrom(inValueType) &&
				!(java.sql.Date.class.isAssignableFrom(inValueType) ||
						java.sql.Time.class.isAssignableFrom(inValueType) ||
						java.sql.Timestamp.class.isAssignableFrom(inValueType)));
	}
}
