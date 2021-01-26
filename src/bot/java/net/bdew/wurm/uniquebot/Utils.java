package net.bdew.wurm.uniquebot;

import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Utils {
    static <T> CompletableFuture<List<T>> sequence(List<CompletableFuture<T>> futures) {
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
                );
    }

    static <K, V> CompletableFuture<Map<K, V>> mapFutures(List<CompletableFuture<Pair<K, V>>> futures) {
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toMap(Pair::getLeft, Pair::getRight))
                );
    }
}
