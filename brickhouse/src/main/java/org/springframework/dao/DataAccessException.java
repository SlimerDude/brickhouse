package org.springframework.dao;

import java.sql.SQLException;

public class DataAccessException extends RuntimeException {

	public DataAccessException(String message) {
		super(message);
	}

	public DataAccessException(String message, SQLException se) {
		super(message, se);
	}
}
