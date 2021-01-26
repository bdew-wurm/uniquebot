package net.bdew.wurm.uniquebot;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DB {
    private final Connection connection;

    public DB(String connString) throws SQLException {
        connection = DriverManager.getConnection(connString);
    }

    public CompletableFuture<List<KnownUnique>> loadUniques() {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT `id`, `server`, `name`, `location`, `reporter` FROM `uniques` ORDER BY `server`, `id`")) {
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
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO `uniques` (`id`, `server`, `name`, `location`, `reporter`) VALUES (?,?,?,?,?)")) {
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
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM `uniques` WHERE `id` = ?")) {
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
            try (PreparedStatement ps = connection.prepareStatement("UPDATE `uniques` SET `location` = ?, `reporter` = ? WHERE `id` = ?")) {
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
