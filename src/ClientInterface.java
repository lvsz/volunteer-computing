import java.rmi.*;

public interface ClientInterface extends Remote {
    public String getName() throws RemoteException;
    public int size() throws RemoteException;
    public void store(String source, byte[][] data, byte[][] hash) throws RemoteException, NotBoundException;
    public void start() throws RemoteException;
    public boolean isFree() throws RemoteException;
}
