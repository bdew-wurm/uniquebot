package net.bdew.wurm.uniquebot;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.File;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

public class Main {
    public static ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);

    public static void main(String[] args) {
        File cf = new File("application.conf");
        Config config = ConfigFactory.parseFile(cf);

        GameServers servers = new GameServers(config.getConfigList("servers").stream().map(c ->
                new GameServer(
                        c.getString("name"),
                        c.getString("addr"),
                        c.getInt("port"),
                        c.getString("pass")
                )
        ).collect(Collectors.toList()));

        try {
            DB db = new DB(config.getString("dbConnection"));
            new DiscordBot(
                    config.getString("discord.server"),
                    config.getString("discord.channel"),
                    config.getString("discord.token"),
                    servers,
                    db
            ).start();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
