package net.bdew.wurm.uniquebot;

import net.bdew.wurm.uniquebot.rmi.UniqueEntry;
import org.jetbrains.annotations.Nullable;

public class KnownUnique {
    public final long id;
    public final String server, name;

    @Nullable
    public String location, reporter;

    public KnownUnique(long id, String server, String name, @Nullable String location, @Nullable String reporter) {
        this.id = id;
        this.server = server;
        this.name = name;
        this.location = location;
        this.reporter = reporter;
    }

    @Override
    public String toString() {
        return String.format("KnownUnique{id=%d, server='%s', name='%s', location=%s reporter=%s}",
                id, server, name,
                location == null ? "NULL" : String.format("'%s'", location),
                reporter == null ? "NULL" : String.format("'%s'", reporter)
        );
    }

    public static KnownUnique from(UniqueEntry r, String server) {
        return new KnownUnique(r.id, server, r.name, null, null);
    }
}
