package vn.edu.wspm.backend.testsupport;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

/** No environment/application datasource fallback. Every instance owns its whole disposable container. */
public final class OwnedMySql implements AutoCloseable {
    private final MySQLContainer container;

    public OwnedMySql() {
        container = new MySQLContainer("mysql:8.4")
                .withDatabaseName("reader_" + UUID.randomUUID().toString().replace("-", ""))
                .withUrlParam("connectionTimeZone", "UTC")
                .withUrlParam("forceConnectionTimeZoneToSession", "true")
                .withUrlParam("socketTimeout", "15000");
        try {
            container.start();
            try(var c=dataSource().getConnection()) {
                var metadata=c.getMetaData();
                if(!metadata.getDatabaseProductVersion().startsWith("8.4.")) throw new IllegalStateException("MySQL 8.4 required.");
                System.out.println("Owned MySQL: server="+metadata.getDatabaseProductVersion()
                        +" driver="+metadata.getDriverVersion()+" Hikari="+HikariDataSource.class.getPackage().getImplementationVersion());
            }
        } catch (RuntimeException | SQLException failure) { container.stop(); throw new IllegalStateException("Owned MySQL fixture initialization failed.",failure); }
    }

    public DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    public HikariDataSource pool(int size) {
        var config = new HikariConfig();
        config.setJdbcUrl(container.getJdbcUrl());
        config.setUsername(container.getUsername());
        config.setPassword(container.getPassword());
        config.setMaximumPoolSize(size);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5000);
        config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        return new HikariDataSource(config);
    }

    /** All target CREATE/ALTER declarations, not the V1->V2 rename/data migration. */
    public void createV2Tables() throws Exception {
        String ddl = new ClassPathResource("db/migration/V2__complete_laptop_combo_schema.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        var definitions = Pattern.compile("(?ms)^(?:CREATE TABLE|ALTER TABLE) `.*?;").matcher(ddl);
        var jdbc = new JdbcTemplate(dataSource());
        int statements = 0;
        while (definitions.find()) { jdbc.execute(definitions.group()); statements++; }
        if (statements != 47) throw new IllegalStateException("Review the V2 test fixture declarations.");
    }

    @Override public void close() { container.stop(); }
}
