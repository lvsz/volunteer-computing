import java.rmi.*;

public interface VolunteerRegistry extends Remote {
    public String name = "VolunteerRegistry";
    public boolean FREE = true;
    public boolean BUSY = false;

    public String generateId() throws RemoteException;

    public void add(String name) throws RemoteException;
    public void remove(String name) throws RemoteException;
    public String get() throws RemoteException;
    public boolean set(String name, boolean free) throws RemoteException;
}
