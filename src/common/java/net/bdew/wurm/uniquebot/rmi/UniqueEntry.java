package net.bdew.wurm.uniquebot.rmi;

import java.io.Serializable;

public class UniqueEntry implements Serializable {
    public final long id;
    public final String name;
    public final int x, y;

    public UniqueEntry(long id, String name, int x, int y) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
    }
}
