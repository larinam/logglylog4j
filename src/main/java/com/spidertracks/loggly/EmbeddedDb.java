/**
 * 
 */
package com.spidertracks.loggly;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.spi.ErrorHandler;

/**
 * Class that performs all operations on the embedded log database
 * 
 * @author Todd Nine
 * 
 */
public class EmbeddedDb {

	private Connection conn;
	private ErrorHandler errorHandler;
	private String insertStatement = null;
	private String selectStatement = null;
	private String deleteStatement = null;

	/**
	 * The embedded db
	 * 
	 * @param dirName
	 * @param logName
	 * @param errorHandler
	 */
	public EmbeddedDb(String dirName, String logName, ErrorHandler errorHandler) {

		this.errorHandler = errorHandler;
		try {
			createTableAndIndex(dirName, logName);
		} catch (SQLException e) {
			errorHandler.error("Unable to create local database for log queue",
					e, 1);
		}
	}

	/**
	 * Create a new entry with the given timestamp
	 * 
	 * @param message
	 * @param time
	 */
	public boolean writeEntry(String message, long time) {
		try {
			PreparedStatement ps = conn.prepareStatement(insertStatement);
			ps.setLong(1, System.nanoTime());
			ps.setString(2, message);
			ps.executeUpdate();
			ps.close();

			return true;
		} catch (SQLException e) {
			errorHandler.error("Unable to persist log message", e, 1);
		}

		return false;
	}

	/**
	 * Return the next entry in the db. If one does not exist, null is returned
	 * 
	 * @return
	 */
	public Entry getNext() {
		try {
			PreparedStatement ps = conn.prepareStatement(selectStatement);

			ResultSet rs = ps.executeQuery();

			if (!rs.next()) {
				return null;
			}

			Entry entry = new Entry();

			entry.setId(rs.getLong(1));

			entry.setMessage(rs.getString(2));
			entry.setTime(rs.getLong(3));
			ps.close();

			return entry;

		} catch (SQLException e) {
			errorHandler.error("Unable to query the embedded db", e, 2);
		}

		return null;
	}

	/**
	 * Delete the entity. True if successful, false otherwise
	 * 
	 * @param e
	 * @return
	 */
	public boolean deleteEntry(Entry entry) {
		try {
			PreparedStatement ps = conn.prepareStatement(deleteStatement);
			ps.setLong(1, entry.getId());
			int count = ps.executeUpdate();
			ps.close();

			return count > 0;
		} catch (SQLException e) {
			errorHandler.error("Unable to persist log message", e, 1);
		}

		return false;
	}

	/**
	 * Start the database and create the table if it doesn't exist
	 */
	private void createTableAndIndex(String dirName, String logName)
			throws SQLException {

		String hsqlConfigUrl = "jdbc:hsqldb:file:" + dirName + "/" + logName
				+ ";hsqldb.applog=1; hsqldb.lock_file=false";

		conn = DriverManager.getConnection(hsqlConfigUrl, "sa", "");

		insertStatement = "INSERT INTO " + logName
				+ " (time, message) VALUES(?, ?)";
		
		selectStatement = "SELECT TOP 1 id, message, time FROM " + logName
				+ " ORDER BY id";
		
		deleteStatement = "DELETE FROM " + logName + " WHERE id = ?";

		// The table may allready exsists if the queue is persistent.
		if (tableExists(logName)) {
			return;
		}

		Statement st = conn.createStatement();

		st.execute("CREATE CACHED TABLE "
				+ logName
				+ "(id BIGINT IDENTITY PRIMARY KEY, message LONGVARCHAR, time BIGINT)");
		st.execute("CREATE INDEX id_index ON " + logName + "(id)");
		st.execute("CREATE INDEX time_index ON " + logName + "(time)");
		st.close();

	}

	/**
	 * Check if the given tablename already exists
	 * 
	 * @param tableName
	 * @return
	 * @throws SQLException
	 */
	private boolean tableExists(String tableName) throws SQLException {

		PreparedStatement stmt = null;
		ResultSet results = null;
		try {
			stmt = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
			results = stmt.executeQuery();
			return true; // if table does exist, no rows will ever be returned
		} catch (SQLException e) {
			return false; // if table does not exist, an exception will be
							// thrown
		} finally {
			if (results != null) {
				results.close();
			}
			if (stmt != null) {
				stmt.close();
			}
		}
	}

}