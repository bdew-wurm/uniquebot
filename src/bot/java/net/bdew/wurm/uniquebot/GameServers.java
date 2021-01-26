package net.bdew.wurm.uniquebot;

import net.bdew.wurm.uniquebot.rmi.UniqueEntry;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class GameServers {
    public final List<GameServer> servers;

    public GameServers(List<GameServer> servers) {
        this.servers = servers;
    }

    public CompletableFuture<Map<GameServer, Optional<List<UniqueEntry>>>> getUniques() {
        return Utils.mapFutures(
                servers.stream().map(s -> s.getUniques()
                        .thenApply(res -> Pair.of(s, res))
                ).collect(Collectors.toList())
        );
    }
}
