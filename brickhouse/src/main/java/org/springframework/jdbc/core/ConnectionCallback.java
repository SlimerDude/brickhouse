package org.springframework.jdbc.core;

import java.sql.Connection;
import java.sql.SQLException;

public interface ConnectionCallback<T> {

	T doInConnection(Connection con) throws SQLException;

}
