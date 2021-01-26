package net.bdew.wurm.uniquebot;

import net.bdew.wurm.uniquebot.rmi.UniqueEntry;
import net.bdew.wurm.uniquebot.rmi.UniqueRemote;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class GameServer {
    private static final Logger logger = LogManager.getLogger("GameServer");

    public final String name;
    private final String addr;
    private final int port;
    private final String pass;

    public GameServer(String name, String addr, int port, String pass) {
        this.name = name;
        this.addr = addr;
        this.port = port;
        this.pass = pass;
    }

    private UniqueRemote connect() throws RemoteException, NotBoundException {
        Registry registry = LocateRegistry.getRegistry(addr, port, (h, p) -> {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(h, p), 500);
            socket.setSoTimeout(500);
            socket.setSoLinger(false, 0);
            return socket;
        });
        return (UniqueRemote) registry.lookup("uniques");
    }

    public CompletableFuture<Optional<List<UniqueEntry>>> getUniques() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                UniqueRemote conn = connect();
                return Optional.of(conn.getUniques(pass));
            } catch (RemoteException | NotBoundException e) {
                logger.log(Level.WARN, String.format("Error querying server %s: %s", name, e.getMessage()));
                return Optional.empty();
            }
        });
    }

    @Override
    public String toString() {
        return String.format("GameServer{name='%s', addr='%s', port=%d}", name, addr, port);
    }
}
