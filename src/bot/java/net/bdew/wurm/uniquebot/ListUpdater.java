package net.bdew.wurm.uniquebot;

import net.bdew.wurm.uniquebot.rmi.UniqueEntry;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ListUpdater {
    private final GameServers servers;
    private final DB db;

    public ListUpdater(GameServers servers, DB db) {
        this.servers = servers;
        this.db = db;
    }

    public class Result {
        public final Map<GameServer, List<KnownUnique>> uniques;
        public final List<KnownUnique> added, removed;

        public Result(Map<GameServer, List<KnownUnique>> uniques, List<KnownUnique> added, List<KnownUnique> removed) {
            this.uniques = uniques;
            this.added = added;
            this.removed = removed;
        }
    }

    private class Merged {
        public final List<KnownUnique> uniques, added, removed;

        public Merged(List<KnownUnique> uniques, List<KnownUnique> added, List<KnownUnique> removed) {
            this.uniques = uniques;
            this.added = added;
            this.removed = removed;
        }
    }


    private Merged mergeUniques(GameServer server, List<KnownUnique> dbData, List<UniqueEntry> serverData) {
        Set<Long> inDb = dbData.stream().map(x -> x.id).collect(Collectors.toSet());
        Set<Long> inServer = serverData.stream().map(x -> x.id).collect(Collectors.toSet());

        List<KnownUnique> removed = dbData.stream()
                .filter(e -> !inServer.contains(e.id))
                .collect(Collectors.toList());

        List<KnownUnique> added = serverData.stream()
                .filter(e -> !inDb.contains(e.id))
                .map(e -> KnownUnique.from(e, server.name))
                .collect(Collectors.toList());

        List<KnownUnique> uniques = Stream.concat(
                dbData.stream().filter(e -> inServer.contains(e.id)),
                added.stream()
        ).sorted(Comparator.comparing(e -> e.id))
                .collect(Collectors.toList());

        return new Merged(uniques, added, removed);
    }

    private Result processData(List<KnownUnique> dbData, Map<GameServer, Optional<List<UniqueEntry>>> serverData) {
        Map<GameServer, List<KnownUnique>> dbSorted = servers.servers.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        gs -> dbData.stream()
                                .filter(x -> x.server.equals(gs.name))
                                .collect(Collectors.toList()))
                );

        List<Pair<GameServer, Merged>> merged = dbSorted.entrySet().stream()
                .map(e -> Pair.of(e.getKey(),
                        serverData.get(e.getKey())
                                .map(s -> mergeUniques(e.getKey(), e.getValue(), s))
                                .orElseGet(() -> new Merged(e.getValue(), Collections.emptyList(), Collections.emptyList()))
                        )
                )
                .collect(Collectors.toList());

        List<KnownUnique> added = merged.stream().flatMap(e -> e.getRight().added.stream()).collect(Collectors.toList());
        List<KnownUnique> removed = merged.stream().flatMap(e -> e.getRight().removed.stream()).collect(Collectors.toList());
        Map<GameServer, List<KnownUnique>> uniques = merged.stream().map(e -> Pair.of(e.getLeft(), e.getRight().uniques)).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        return new Result(uniques, added, removed);
    }

    private CompletableFuture<Result> doDbUpdates(Result data) {
        return CompletableFuture.allOf(db.createUniques(data.added), db.deleteUniques(data.removed))
                .thenApply((x) -> data);
    }

    public CompletableFuture<Result> getUniqueList() {
        return db.loadUniques().thenCompose(dbData ->
                servers.getUniques()
                        .thenApply(serverData -> processData(dbData, serverData))
                        .thenCompose(this::doDbUpdates)
        );
    }
}
