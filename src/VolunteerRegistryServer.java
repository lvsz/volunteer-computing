import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.ArrayList;

public class VolunteerRegistryServer implements VolunteerRegistry {

    private int idCounter = 0;
    private ArrayList<String> free;
    private ArrayList<String> busy;

    public VolunteerRegistryServer() {
        free = new ArrayList<>();
        busy = new ArrayList<>();
    }

    public void makeAvailable() {
        try {
            Registry registry = LocateRegistry.getRegistry();
            UnicastRemoteObject.exportObject(this, 0);
            registry.rebind(name, this);
            System.out.println("Volunteer registry server now online");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public synchronized String generateId() throws RemoteException {
        return Integer.toString(++idCounter);
    }

    @Override
    public synchronized void add(String name) throws RemoteException {
        free.add(name);
        System.out.println("Added new client: " + name);
    }

    @Override
    public synchronized void remove(String name) throws RemoteException {
        if (!free.remove(name))
            busy.remove(name);
        System.out.println("Removed client: " + name);
    }

    @Override
    public synchronized String get() throws RemoteException {
        return free.isEmpty() ? null : free.get(0);
    }

    @Override
    public synchronized boolean set(String name, boolean free) throws RemoteException {
        if (free) {
            this.busy.remove(name);
            this.free.add(name);
            return true;
        } else if (this.free.contains(name)) {
            this.free.remove(name);
            this.busy.add(name);
            return true;
        } else {
            return false;
        }
    }

    public static void main(String[] args) {
        new VolunteerRegistryServer().makeAvailable();
    }
}
