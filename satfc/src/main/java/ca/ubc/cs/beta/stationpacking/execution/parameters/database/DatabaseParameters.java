package ca.ubc.cs.beta.stationpacking.execution.parameters.database;

import ca.ubc.cs.beta.aeatk.misc.options.OptionLevel;
import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import com.beust.jcommander.Parameter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Created by newmanne on 2016-11-18.
 */
@UsageTextField(title="MySQL parameters",description="Parameters describing access to mysql db", level = OptionLevel.DEVELOPER)
public class DatabaseParameters extends AbstractOptions implements AutoCloseable {

    @Parameter(names = "-DB-PORT", description = "port")
    public Integer port = 4545;
    @Parameter(names = "-DB-HOST", description = "host")
    public String host = "arrowdb.cs.ubc.ca";
    @Parameter(names = "-DB-USER", description = "username")
    public String user = System.getenv("SATFC_DB_USER");
    @Parameter(names = "-DB-PASS", description = "password", password = true)
    public String password = System.getenv("SATFC_DB_PASS");
    @Parameter(names = "-DB-NAME", description = "database name")
    public String databaseName = "SpectrumAuctions";

    private Connection connection;

    public boolean isValid() {
        return host != null && user != null && password != null && databaseName != null;
    }

    // TODO: close the connection
    public synchronized Connection getConnection() {
        if (connection != null) {
            return connection;
        } else {
            final String url = "jdbc:mysql://" + host + ":" + port + "/" + databaseName + "?allowMultiQueries=true";
            try {
                connection = DriverManager.getConnection(url, user, password);
                return connection;
            } catch (SQLException e) {
                throw new RuntimeException("Could not connect to database", e);
            }
        }
    }

    @Override
    public void close() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

}
