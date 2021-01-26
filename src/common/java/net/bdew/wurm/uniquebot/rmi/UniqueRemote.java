package net.bdew.wurm.uniquebot.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface UniqueRemote extends Remote {
    List<UniqueEntry> getUniques(String password) throws RemoteException;
}
