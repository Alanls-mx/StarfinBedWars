package com.starfinplugins.bedwars.stats;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assume;

/** Guard shared by JDBC suites. The caller must invoke this before any destructive SQL. */
public final class IntegrationMysql {
    public final String host, schema, user, password, url;
    public final int port;

    private IntegrationMysql(String host, int port, String schema, String user, String password)
            throws Exception {
        this.host = host; this.port = port; this.schema = schema;
        this.user = user; this.password = password;
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + schema
            + "?sslMode=DISABLED&allowPublicKeyRetrieval=true";
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement query = connection.createStatement();
             ResultSet row = query.executeQuery("SELECT DATABASE()")) {
            if (!row.next() || !schema.equals(row.getString(1)))
                throw new IllegalStateException("MySQL test schema mismatch");
            try (ResultSet marker = query.executeQuery(
                    "SELECT marker FROM starfin_disposable_test_schema LIMIT 1")) {
                if (!marker.next() || !"phase72-disposable-v1".equals(marker.getString(1)))
                    throw new IllegalStateException("Missing disposable MySQL marker");
            }
        }
    }

    public static IntegrationMysql load(String prefix, String fallback, String schemaPrefix)
            throws Exception {
        return load(prefix, fallback, schemaPrefix, null);
    }
    public static IntegrationMysql load(String prefix, String fallback, String schemaPrefix,
            String schemaOverrideKey) throws Exception {
        boolean required = Boolean.getBoolean("requireIntegrationTests");
        String flag = System.getenv("SBW_INTEGRATION_TESTS");
        if (!required) Assume.assumeTrue("Set SBW_INTEGRATION_TESTS=1 for disposable MySQL tests",
            "1".equals(flag));
        String host = value(prefix, fallback, "HOST");
        String port = value(prefix, fallback, "PORT");
        String schema = schemaOverrideKey == null ? value(prefix, fallback, "SCHEMA")
            : System.getenv(schemaOverrideKey);
        String user = value(prefix, fallback, "USER");
        String password = value(prefix, fallback, "PASSWORD");
        if (host == null || port == null || schema == null || user == null || password == null)
            throw new IllegalStateException("Missing disposable MySQL test configuration: " + prefix);
        if (!("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)))
            throw new IllegalStateException("JDBC integration tests require loopback MySQL");
        if (!schema.matches(schemaPrefix + "[a-z0-9_]+")
                || schema.matches("(?i).*(production|prod|minecraft|starfin).*"))
            throw new IllegalStateException("Unsafe disposable MySQL schema");
        if (user.isEmpty()) throw new IllegalStateException("Missing MySQL test user");
        int parsedPort = Integer.parseInt(port);
        if (parsedPort < 1 || parsedPort > 65535) throw new IllegalStateException("Invalid MySQL port");
        return new IntegrationMysql(host, parsedPort, schema, user, password);
    }
    private static String value(String primary, String fallback, String suffix) {
        String selected = System.getenv(primary + suffix);
        return selected != null ? selected : fallback == null ? null
            : System.getenv(fallback + suffix);
    }
    public StatsSettings settings(String sslMode) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true); yaml.set("mysql.host", host);
        yaml.set("mysql.port", port); yaml.set("mysql.database", schema);
        yaml.set("mysql.username", user); yaml.set("mysql.password", password);
        yaml.set("mysql.ssl-mode", sslMode);
        return new StatsSettings(yaml);
    }
}
