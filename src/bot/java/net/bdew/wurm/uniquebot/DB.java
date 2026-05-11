package net.bdew.wurm.uniquebot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DB {
    private static final Logger logger = LoggerFactory.getLogger("DB");
    private static final int DB_CHECK_TIMEOUT = 5;

    private final String connString;
    private volatile Connection connection;

    public DB(String connString) throws SQLException {
        this.connString = connString;
        connection = DriverManager.getConnection(connString);
    }

    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || !connection.isValid(DB_CHECK_TIMEOUT)) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
            logger.info("DB connection lost, reconnecting...");
            connection = DriverManager.getConnection(connString);
        }
        return connection;
    }

    public CompletableFuture<List<KnownUnique>> loadUniques() {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = getConnection().prepareStatement("SELECT `id`, `server`, `name`, `location`, `reporter` FROM `uniques` ORDER BY `server`, `id`")) {
                try (ResultSet rs = ps.executeQuery()) {
                    ArrayList<KnownUnique> res = new ArrayList<>();
                    while (rs.next()) {
                        res.add(new KnownUnique(
                                rs.getLong(1),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getString(4),
                                rs.getString(5)
                        ));
                    }
                    return res;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> createUniques(List<KnownUnique> uniques) {
        if (uniques.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = getConnection().prepareStatement("INSERT INTO `uniques` (`id`, `server`, `name`, `location`, `reporter`) VALUES (?,?,?,?,?)")) {
                for (KnownUnique unique : uniques) {
                    ps.setLong(1, unique.id);
                    ps.setString(2, unique.server);
                    ps.setString(3, unique.name);
                    ps.setString(4, unique.location);
                    ps.setString(5, unique.reporter);
                    ps.execute();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> deleteUniques(List<KnownUnique> uniques) {
        if (uniques.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = getConnection().prepareStatement("DELETE FROM `uniques` WHERE `id` = ?")) {
                for (KnownUnique u : uniques) {
                    ps.setLong(1, u.id);
                    ps.execute();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> updateLocation(KnownUnique unique) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = getConnection().prepareStatement("UPDATE `uniques` SET `location` = ?, `reporter` = ? WHERE `id` = ?")) {
                ps.setString(1, unique.location);
                ps.setString(2, unique.reporter);
                ps.setLong(3, unique.id);
                ps.execute();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
