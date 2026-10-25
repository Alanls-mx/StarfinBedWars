package com.starfinplugins.bedwars.storage;

import com.starfinplugins.bedwars.stats.StatsSettings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/** Portable SQL import contract: MySQL for networks, SQLite only for local/standalone archives. */
public final class SqlMigrationWriter {
    public enum Target { MYSQL, SQLITE }

    public void importBundle(Target target, MigrationBundle bundle,
                             StatsSettings mysql, File dataFolder) throws Exception {
        if (target == Target.MYSQL && !mysql.enabled) {
            throw new IllegalStateException("MySQL requer stats.yml enabled: true e credenciais validas.");
        }
        HikariDataSource mysqlPool = null;
        try {
            Connection connection;
            if (target == Target.MYSQL) {
                HikariConfig config = new HikariConfig();
                config.setPoolName("StarfinBedWars-Migration");
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                config.setJdbcUrl("jdbc:mysql://" + mysql.host + ":" + mysql.port + "/" + mysql.schema
                    + "?sslMode=" + mysql.sslMode + "&connectionTimeZone=UTC&characterEncoding=UTF-8");
                config.setUsername(mysql.user);
                config.setPassword(mysql.password);
                config.setMaximumPoolSize(1);
                config.setMinimumIdle(0);
                config.setConnectionTimeout(mysql.timeoutMs);
                mysqlPool = new HikariDataSource(config);
                connection = mysqlPool.getConnection();
            } else {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:"
                    + new File(dataFolder, "bedwars-migration.sqlite").getAbsolutePath());
            }
            try (Connection db = connection) {
                createTables(db, target);
                db.setAutoCommit(false);
                try {
                    // Check ALL existing hashes before writing anything; never overwrite remote edits.
                    verifyNoConflicts(db, "sbw_storage_arenas", "arena_id", bundle.serverId, bundle.arenas);
                    verifyNoConflicts(db, "sbw_storage_documents", "document_name", bundle.serverId,
                        bundle.documents);
                    insertEntries(db, target, "sbw_storage_arenas", "arena_id", bundle.serverId,
                        bundle.arenas);
                    insertEntries(db, target, "sbw_storage_documents", "document_name", bundle.serverId,
                        bundle.documents);
                    db.commit();
                } catch (SQLException | RuntimeException error) {
                    db.rollback();
                    throw error;
                } finally {
                    db.setAutoCommit(true);
                }
            }
        } finally {
            if (mysqlPool != null) mysqlPool.close();
        }
    }

    private void createTables(Connection db, Target target) throws SQLException {
        String text = target == Target.MYSQL ? "MEDIUMTEXT" : "TEXT";
        String suffix = target == Target.MYSQL ? " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4" : "";
        try (Statement statement = db.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS sbw_storage_arenas ("
                + "server_id VARCHAR(32) NOT NULL,arena_id VARCHAR(32) NOT NULL,"
                + "content_yaml " + text + " NOT NULL,sha256 CHAR(64) NOT NULL,"
                + "imported_at BIGINT NOT NULL,PRIMARY KEY(server_id,arena_id))" + suffix);
            statement.execute("CREATE TABLE IF NOT EXISTS sbw_storage_documents ("
                + "server_id VARCHAR(32) NOT NULL,document_name VARCHAR(64) NOT NULL,"
                + "content_yaml " + text + " NOT NULL,sha256 CHAR(64) NOT NULL,"
                + "imported_at BIGINT NOT NULL,PRIMARY KEY(server_id,document_name))" + suffix);
        }
    }

    private void verifyNoConflicts(Connection db, String table, String key, String server,
                                   Map<String, MigrationBundle.Entry> data) throws SQLException {
        String sql = "SELECT sha256 FROM " + table + " WHERE server_id=? AND " + key + "=?";
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (Map.Entry<String, MigrationBundle.Entry> entry : data.entrySet()) {
                statement.setString(1, server);
                statement.setString(2, entry.getKey());
                try (ResultSet row = statement.executeQuery()) {
                    if (row.next() && !entry.getValue().sha256.equals(row.getString(1))) {
                        throw new SQLException("Conflito em " + table + "/" + entry.getKey()
                            + ": o destino ja tem conteudo diferente. Nenhum registro sera substituido.");
                    }
                }
            }
        }
    }

    private void insertEntries(Connection db, Target target, String table, String key,
                               String server, Map<String, MigrationBundle.Entry> data) throws SQLException {
        String prefix = target == Target.MYSQL ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ";
        String sql = prefix + table + " (server_id," + key
            + ",content_yaml,sha256,imported_at) VALUES (?,?,?,?,?)";
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (Map.Entry<String, MigrationBundle.Entry> entry : data.entrySet()) {
                statement.setString(1, server);
                statement.setString(2, entry.getKey());
                statement.setString(3, entry.getValue().text);
                statement.setString(4, entry.getValue().sha256);
                statement.setLong(5, System.currentTimeMillis());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
