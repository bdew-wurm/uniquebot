package net.bdew.wurm.uniquebot.mod;

import com.wurmonline.server.Servers;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.CreatureStatus;
import com.wurmonline.server.creatures.Creatures;
import net.bdew.wurm.tools.server.ServerThreadExecutor;
import net.bdew.wurm.uniquebot.rmi.UniqueEntry;
import net.bdew.wurm.uniquebot.rmi.UniqueRemote;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class UniqueRemoteImpl extends UnicastRemoteObject implements UniqueRemote {
    private static final Logger log = Logger.getLogger("UniqueRemote");
    private final Method mGetStatusString;

    public UniqueRemoteImpl() throws RemoteException {
        try {
            mGetStatusString = ReflectionUtil.getMethod(CreatureStatus.class, "getTypeString");
            mGetStatusString.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static void register(Registry reg) {
        try {
            reg.bind("uniques", new UniqueRemoteImpl());
            log.info("Registered RMI interface");
        } catch (RemoteException | AlreadyBoundException e) {
            log.log(Level.SEVERE, "Failed to bind uniques remote", e);
        }
    }

    private String getClientHostSafe() {
        try {
            return getClientHost();
        } catch (ServerNotActiveException e) {
            return "<unknown>";
        }
    }

    private void validateIntraServerPassword(final String intraServerPassword) throws AccessException {
        if (!Servers.localServer.INTRASERVERPASSWORD.equals(intraServerPassword)) {
            throw new AccessException("Access denied.");
        }
    }

    private UniqueEntry makeEntry(Creature c) {
        try {
            return new UniqueEntry(c.getWurmId(), mGetStatusString.invoke(c.getStatus()) + c.getNameWithoutPrefixes().toLowerCase(), c.getTileX(), c.getTileY());
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<UniqueEntry> getUniques(String password) throws RemoteException {
        validateIntraServerPassword(password);
        log.info(String.format("Sending uniques to %s", getClientHostSafe()));
        try {
            return ServerThreadExecutor.INSTANCE.submit(() ->
                    Arrays.stream(Creatures.getInstance().getCreatures())
                            .filter(c -> c.isUnique() && !c.isReborn() && !c.isDead())
                            .map(this::makeEntry)
                            .collect(Collectors.toList())).get();
        } catch (InterruptedException | ExecutionException e) {
            log.log(Level.SEVERE, "Error generating unique list", e);
            throw new RuntimeException(e);
        }
    }
}
