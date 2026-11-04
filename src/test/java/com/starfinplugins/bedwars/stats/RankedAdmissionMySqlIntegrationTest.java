package com.starfinplugins.bedwars.stats;

import com.starfinplugins.bedwars.ranked.RankedAllocation;
import com.starfinplugins.bedwars.ranked.RankedRepository;
import com.starfinplugins.minigames.api.ReservationJdbc;
import com.starfinplugins.minigames.api.ReservationSnapshot;
import com.starfinplugins.minigames.api.ReservationState;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

/** Reservation-to-authorization contract against a disposable MySQL schema. */
public class RankedAdmissionMySqlIntegrationTest {
    @Test public void onlyCompleteAuthoritativeRosterCanBeAdmitted() throws Exception {
        IntegrationMysql target = IntegrationMysql.load("SBW_RANKED_TEST_DB_", null,
            "sbw_ranked_test_", "SBW_RANKED_ADMISSION_DB_SCHEMA");
        String schema = target.schema, host = target.host;
        String user = target.user, password = target.password;
        String port = Integer.toString(target.port);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true); yaml.set("mysql.host", host);
        yaml.set("mysql.port", Integer.parseInt(port)); yaml.set("mysql.database", schema);
        yaml.set("mysql.username", user); yaml.set("mysql.password", password);
        yaml.set("mysql.ssl-mode", "DISABLED");
        StatsSettings settings = new StatsSettings(yaml);
        String url = "jdbc:mysql://" + host + ":" + port + "/" + schema + "?sslMode=DISABLED";
        UUID allocation = UUID.randomUUID(), instance = UUID.randomUUID();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        UUID entryA = UUID.randomUUID(), entryB = UUID.randomUUID();
        long now = System.currentTimeMillis();
        try (RankedRepository ranked = new RankedRepository()) {
            ranked.open(settings);
            try (Connection c = DriverManager.getConnection(url, user, password);
                 Statement sql = c.createStatement()) {
                ReservationJdbc.ensureTable(c);
                sql.execute("CREATE TABLE IF NOT EXISTS sm_ranked_allocations ("
                    + "allocation_id CHAR(36) PRIMARY KEY,server_id VARCHAR(32),"
                    + "server_instance CHAR(36),arena_id VARCHAR(32),mode VARCHAR(16),"
                    + "context VARCHAR(32),state VARCHAR(16),expected_players INT,"
                    + "created_at BIGINT,expires_at BIGINT) ENGINE=InnoDB");
                sql.execute("CREATE TABLE IF NOT EXISTS sm_ranked_allocation_members ("
                    + "allocation_id CHAR(36),player_uuid CHAR(36),entry_id CHAR(36),"
                    + "reservation_id CHAR(36),team_id VARCHAR(32),owner_proxy CHAR(36),"
                    + "state VARCHAR(16),PRIMARY KEY(allocation_id,player_uuid)) ENGINE=InnoDB");
                sql.execute("CREATE TABLE IF NOT EXISTS sm_ranked_queue_entries ("
                    + "entry_id CHAR(36) PRIMARY KEY,allocation_id CHAR(36),state VARCHAR(16))"
                    + " ENGINE=InnoDB");
                sql.execute("CREATE TABLE IF NOT EXISTS sm_ranked_queue_members ("
                    + "player_uuid CHAR(36) PRIMARY KEY,entry_id CHAR(36)) ENGINE=InnoDB");
                try (PreparedStatement policy = c.prepareStatement(
                        "INSERT INTO sbw_ranked_policy(server_id,instance_id,enabled,context,"
                        + "initial_rating,updated_at,season_version,pending_results,active_matches)"
                        + " VALUES('r62test',?,1,'preseason-1',1000,?,0,0,0)"
                        + " ON DUPLICATE KEY UPDATE instance_id=VALUES(instance_id),"
                        + "updated_at=VALUES(updated_at)")) {
                    policy.setString(1, instance.toString()); policy.setLong(2, now);
                    policy.executeUpdate();
                }
                try (PreparedStatement insert = c.prepareStatement(
                        "INSERT INTO sm_ranked_allocations VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                    insert.setString(1, allocation.toString()); insert.setString(2, "r62test");
                    insert.setString(3, instance.toString()); insert.setString(4, "duel");
                    insert.setString(5, "solo"); insert.setString(6, "preseason-1");
                    insert.setString(7, "TRANSFERRING"); insert.setInt(8, 2);
                    insert.setLong(9, now); insert.setLong(10, now + 60000); insert.executeUpdate();
                }
                insertMember(c, a, entryA, allocation, instance, "red", now);
                insertMember(c, b, entryB, allocation, instance, "blue", now);
            }
            ReservationSnapshot first;
            ReservationSnapshot second;
            try (Connection c = DriverManager.getConnection(url, user, password)) {
                first = ReservationJdbc.claim(c, a, "r62test", instance, now + 1, 60000);
                second = ReservationJdbc.claim(c, b, "r62test", instance, now + 1, 60000);
            }
            assertNotNull(first); assertNotNull(second);
            RankedAllocation verified = ranked.inspectAllocation(first, "r62test", instance.toString());
            assertNotNull(verified);
            assertEquals(2, verified.teams.size());
            assertNull(ranked.inspectAllocation(first, "another", instance.toString()));
            assertNull(ranked.inspectAllocation(first, "r62test", UUID.randomUUID().toString()));
            assertFalse(ranked.markAdmitted(allocation, first.getId(), a));
            assertFalse(ranked.markAdmitted(allocation, UUID.randomUUID(), b));
            assertTrue(ranked.markAdmitted(allocation, second.getId(), b));
            assertFalse(ranked.markAdmitted(allocation, second.getId(), b));
            try (Connection c = DriverManager.getConnection(url, user, password);
                 PreparedStatement query = c.prepareStatement(
                    "SELECT state FROM sm_ranked_allocations WHERE allocation_id=?")) {
                query.setString(1, allocation.toString());
                try (ResultSet row = query.executeQuery()) {
                    assertTrue(row.next()); assertEquals("ADMITTED", row.getString(1));
                }
            }
        }
    }
    private static void insertMember(Connection c, UUID player, UUID entry, UUID allocation,
                                     UUID instance, String team, long now) throws Exception {
        ReservationSnapshot reservation = new ReservationSnapshot(UUID.randomUUID(), player,
            "bedwars", "solo", "r62test", instance, "duel", ReservationState.PENDING,
            now, now + 60000, UUID.randomUUID(), null, player, 0L, 1,
            team, allocation, "preseason-1");
        ReservationJdbc.save(c, reservation, false, now);
        try (PreparedStatement insert = c.prepareStatement(
                "INSERT INTO sm_ranked_allocation_members VALUES(?,?,?,?,?,?,?)")) {
            insert.setString(1, allocation.toString()); insert.setString(2, player.toString());
            insert.setString(3, entry.toString()); insert.setString(4, reservation.getId().toString());
            insert.setString(5, team); insert.setString(6, UUID.randomUUID().toString());
            insert.setString(7, "RESERVED"); insert.executeUpdate();
        }
        try (PreparedStatement insert = c.prepareStatement(
                "INSERT INTO sm_ranked_queue_entries VALUES(?,?,'TRANSFERRING')")) {
            insert.setString(1, entry.toString()); insert.setString(2, allocation.toString());
            insert.executeUpdate();
        }
        try (PreparedStatement insert = c.prepareStatement(
                "INSERT INTO sm_ranked_queue_members VALUES(?,?)")) {
            insert.setString(1, player.toString()); insert.setString(2, entry.toString());
            insert.executeUpdate();
        }
    }
}
