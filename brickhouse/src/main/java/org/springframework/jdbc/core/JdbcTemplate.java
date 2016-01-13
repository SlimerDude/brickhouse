package org.springframework.jdbc.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

public class JdbcTemplate {
	private DataSource dataSource;

	public JdbcTemplate(DataSource ds) {
		this.dataSource = ds;
	}	

	public <T> T execute(ConnectionCallback<T> action) {
		try {
			Connection con = dataSource.getConnection();
			try {
				return action.doInConnection(con);
			}
			finally {
				JdbcUtils.releaseConnection(con);
			}
		} catch (SQLException se) {
			throw new DataAccessException(se.getMessage(), se);
		}
	}
	
	public <T> T execute(StatementCallback<T> action) {
		try {
			Connection con = dataSource.getConnection();
			Statement stmt = null;
			try {
				stmt = con.createStatement();
				return action.doInStatement(stmt);
			}
			finally {
				JdbcUtils.closeStatement(stmt);
				JdbcUtils.releaseConnection(con);
			}
		} catch (SQLException se) {
			throw new DataAccessException(se.getMessage(), se);
		}		
	}

	public <T> T execute(SimplePreparedStatementCreator psc, PreparedStatementCallback<T> action) {
		try {
			Connection con = dataSource.getConnection();
			PreparedStatement ps = null;
			try {
				ps = psc.createPreparedStatement(con);
				return action.doInPreparedStatement(ps);
			}
			finally {
				JdbcUtils.closeStatement(ps);
				JdbcUtils.releaseConnection(con);
			}
		} catch (SQLException se) {
			throw new DataAccessException(se.getMessage(), se);
		}		
	}

	public void execute(final String sql) {
		execute(new StatementCallback<Object>() {
			@Override
			public Object doInStatement(Statement stmt) throws SQLException {
				stmt.execute(sql);
				return null;
			}
		});
	}

	public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
		List<T> results = execute(new SimplePreparedStatementCreator(sql), new PreparedStatementCallback<List<T>>() {
			@Override
			public List<T> doInPreparedStatement(PreparedStatement ps) throws SQLException {
				ResultSet rs = null;
				try {
					new ArgumentPreparedStatementSetter(args).setValues(ps);
					rs = ps.executeQuery();
					
					SingleColumnRowMapper<T> rowMapper = new SingleColumnRowMapper<T>(requiredType);
					List<T> results = new ArrayList<T>(1);
					int rowNum = 0;
					while (rs.next()) {
						results.add(rowMapper.mapRow(rs, rowNum++));
					}
					return results;
				}
				finally {
					JdbcUtils.closeResultSet(rs);
				}
			}
		});
		return requiredSingleResult(results);
	}
	
	public void query(String sql, RowCallbackHandler rch) {
		final String sql1 = sql;
		execute(new StatementCallback<Object>() {
			@Override
			public Object doInStatement(Statement stmt) throws SQLException {
				ResultSet rs = null;
				try {
					rs = stmt.executeQuery(sql1);
					while (rs.next()) {
						rch.processRow(rs);
					}
					return null;
				}
				finally {
					JdbcUtils.closeResultSet(rs);
				}
			}
		});
	}
	
	public int update(String sql, Object... args) {
		return execute(new SimplePreparedStatementCreator(sql), new PreparedStatementCallback<Integer>() {
			@Override
			public Integer doInPreparedStatement(PreparedStatement ps) throws SQLException {
				new ArgumentPreparedStatementSetter(args).setValues(ps);
				return ps.executeUpdate();
			}
		});
	}

	public int[] batchUpdate(String sql, final BatchPreparedStatementSetter pss) {
		return execute(new SimplePreparedStatementCreator(sql), new PreparedStatementCallback<int[]>() {
			@Override
			public int[] doInPreparedStatement(PreparedStatement ps) throws SQLException {
				int batchSize = pss.getBatchSize();
				
				if (JdbcUtils.supportsBatchUpdates(ps.getConnection())) {
					for (int i = 0; i < batchSize; i++) {
						pss.setValues(ps, i);
						ps.addBatch();
					}
					return ps.executeBatch();
				}
				else {
					List<Integer> rowsAffected = new ArrayList<Integer>();
					for (int i = 0; i < batchSize; i++) {
						pss.setValues(ps, i);
						rowsAffected.add(ps.executeUpdate());
					}
					int[] rowsAffectedArray = new int[rowsAffected.size()];
					for (int i = 0; i < rowsAffectedArray.length; i++) {
						rowsAffectedArray[i] = rowsAffected.get(i);
					}
					return rowsAffectedArray;
				}
			}
		});
	}
	
	public static <T> T requiredSingleResult(Collection<T> results) {
		int size = (results != null ? results.size() : 0);
		if (size == 0) {
			throw new EmptyResultDataAccessException(1);
		}
		if (results.size() > 1) {
			throw new IncorrectResultSizeDataAccessException(1, size );
		}
		return results.iterator().next();
	}

	// needed to wrap the SQLException in a consistent place
	private static class SimplePreparedStatementCreator {
		private final String sql;

		public SimplePreparedStatementCreator(String sql) {
			this.sql = sql;
		}

		public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
			return con.prepareStatement(this.sql);
		}
	}
}
