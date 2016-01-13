package org.springframework.jdbc.core;

import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;

import org.springframework.util.ClassUtils;

public class JdbcUtils {
	
	// Check for JDBC 4.1 getObject(int, Class) method - available on JDK 7 and higher
	private static final boolean getObjectWithTypeAvailable =
			ClassUtils.hasMethod(ResultSet.class, "getObject", int.class, Class.class);
	
	public static Object getResultSetValue(ResultSet rs, int index, Class<?> requiredType) throws SQLException {
		if (requiredType == null) {
			return getResultSetValue(rs, index);
		}

		Object value;

		// Explicitly extract typed value, as far as possible.
		if (String.class.equals(requiredType)) {
			return rs.getString(index);
		}
		else if (boolean.class.equals(requiredType) || Boolean.class.equals(requiredType)) {
			value = rs.getBoolean(index);
		}
		else if (byte.class.equals(requiredType) || Byte.class.equals(requiredType)) {
			value = rs.getByte(index);
		}
		else if (short.class.equals(requiredType) || Short.class.equals(requiredType)) {
			value = rs.getShort(index);
		}
		else if (int.class.equals(requiredType) || Integer.class.equals(requiredType)) {
			value = rs.getInt(index);
		}
		else if (long.class.equals(requiredType) || Long.class.equals(requiredType)) {
			value = rs.getLong(index);
		}
		else if (float.class.equals(requiredType) || Float.class.equals(requiredType)) {
			value = rs.getFloat(index);
		}
		else if (double.class.equals(requiredType) || Double.class.equals(requiredType) ||
				Number.class.equals(requiredType)) {
			value = rs.getDouble(index);
		}
		else if (BigDecimal.class.equals(requiredType)) {
			return rs.getBigDecimal(index);
		}
		else if (java.sql.Date.class.equals(requiredType)) {
			return rs.getDate(index);
		}
		else if (java.sql.Time.class.equals(requiredType)) {
			return rs.getTime(index);
		}
		else if (java.sql.Timestamp.class.equals(requiredType) || java.util.Date.class.equals(requiredType)) {
			return rs.getTimestamp(index);
		}
		else if (byte[].class.equals(requiredType)) {
			return rs.getBytes(index);
		}
		else if (Blob.class.equals(requiredType)) {
			return rs.getBlob(index);
		}
		else if (Clob.class.equals(requiredType)) {
			return rs.getClob(index);
		}
		else {
			// Some unknown type desired -> rely on getObject.
			if (getObjectWithTypeAvailable) {
				try {
					return rs.getObject(index, requiredType);
				}
				catch (AbstractMethodError err) { }
				catch (SQLFeatureNotSupportedException ex) { }
				catch (SQLException ex) { }
			}
			// Fall back to getObject without type specification...
			return getResultSetValue(rs, index);
		}

		// Perform was-null check if necessary (for results that the JDBC driver returns as primitives).
		return (rs.wasNull() ? null : value);
	}

	public static Object getResultSetValue(ResultSet rs, int index) throws SQLException {
		Object obj = rs.getObject(index);
		String className = null;
		if (obj != null) {
			className = obj.getClass().getName();
		}
		if (obj instanceof Blob) {
			Blob blob = (Blob) obj;
			obj = blob.getBytes(1, (int) blob.length());
		}
		else if (obj instanceof Clob) {
			Clob clob = (Clob) obj;
			obj = clob.getSubString(1, (int) clob.length());
		}
		else if ("oracle.sql.TIMESTAMP".equals(className) || "oracle.sql.TIMESTAMPTZ".equals(className)) {
			obj = rs.getTimestamp(index);
		}
		else if (className != null && className.startsWith("oracle.sql.DATE")) {
			String metaDataClassName = rs.getMetaData().getColumnClassName(index);
			if ("java.sql.Timestamp".equals(metaDataClassName) || "oracle.sql.TIMESTAMP".equals(metaDataClassName)) {
				obj = rs.getTimestamp(index);
			}
			else {
				obj = rs.getDate(index);
			}
		}
		else if (obj != null && obj instanceof java.sql.Date) {
			if ("java.sql.Timestamp".equals(rs.getMetaData().getColumnClassName(index))) {
				obj = rs.getTimestamp(index);
			}
		}
		return obj;
	}

	public static boolean supportsBatchUpdates(Connection con) {
		try {
			DatabaseMetaData dbmd = con.getMetaData();
			return dbmd != null && dbmd.supportsBatchUpdates();
		}
		catch (SQLException ex) { }
		return false;
	}
	
	public static void closeResultSet(ResultSet rs) {
		try {
			if (rs != null) rs.close();
		} catch (Throwable ex) { }
	}
	
	public static void closeStatement(Statement stmt) {
		try {
			if (stmt != null) stmt.close();
		} catch (Throwable ex) { }
	}

	public static void releaseConnection(Connection con) {
		try {
			if (con != null) con.close();
		} catch (Throwable ex) { }
	}
}
