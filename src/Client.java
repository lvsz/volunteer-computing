import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.security.*;
import java.util.ArrayList;
import java.util.Arrays;

public class Client implements ClientInterface {

    private String name;

    private static int size = 500;
    private ArrayList<DataPackage> dataPackages;
    private int toDo = 0;   // amount of tasks left

    private DataPackageEncryption dataEncryption;
    private byte[] dataKey;
    private KeyEncryption keyEncryption;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    private Registry registry;
    private VolunteerRegistry volunteerRegistry;

    public Client() {
        dataEncryption = new DataPackageEncryption();
        keyEncryption = new KeyEncryption();
        KeyPair keys = keyEncryption.generateKeys();
        privateKey = keys.getPrivate();
        publicKey = keys.getPublic();
    }

    public void makeAvailable() {
        try {
            ClientInterface stub = (ClientInterface) UnicastRemoteObject.exportObject(this, 0);
            registry = LocateRegistry.getRegistry();

            volunteerRegistry = (VolunteerRegistry) registry.lookup(VolunteerRegistry.name);
            name = volunteerRegistry.generateId();
            registry.rebind(name, stub);
            volunteerRegistry.add(name);

            Runtime runtime = Runtime.getRuntime();
            runtime.addShutdownHook(new AtExit());

            System.out.println("Client " + name + " now online");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getName() throws RemoteException {
        return name;
    }

    @Override
    public int size() throws RemoteException {
        return size;
    }

    @Override
    public void store(String source, byte[][] data, byte[][] hash) throws RemoteException, NotBoundException {
        Commander server = (Commander) registry.lookup(source);
        dataKey = keyEncryption.decrypt(privateKey, server.getKey(publicKey));
        String hashSpec = server.HASH_SPEC;
        Mac mac;

        dataPackages = new ArrayList<>();
        try {
            mac = Mac.getInstance(hashSpec);
            SecretKey hashKey = new SecretKeySpec(dataKey, hashSpec);
            mac.init(hashKey);

            // decrypt input & check if digital signatures match
            for (int i = 0; i < data.length; ++i) {
                if (Arrays.equals(hash[i], mac.doFinal(data[i]))) {
                    dataPackages.add(dataEncryption.decrypt(dataKey, data[i]));
                } else {
                    throw new Exception("Corrupted package");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.toDo = dataPackages.size();
    }

    @Override
    public void start() throws RemoteException {
        System.out.println("Starting analysis on " + dataPackages.size() + " packages");
        try {
            String sender = dataPackages.get(0).server;
            Commander server = (Commander) registry.lookup(sender);

            SequentialAnalyser analyser = new SequentialAnalyser();

            for (DataPackage dp : dataPackages) {
                dp.score = analyser.analyse(dp.comments);
                System.out.println("Score is: " + dp.score);
                dp.analyser = name;
                --toDo;
                server.result(dataEncryption.encrypt(dataKey, dp));
                System.out.println("Result sent");
            }

            System.out.println("analysis done\n");
        } catch (NotBoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isFree() {
        return toDo < 1;
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            try {
                size = Integer.decode(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid argument for size limit, defaulting to " + size);
            }
        }

        new Client().makeAvailable();
    }

    // thread hook to log off from volunteer registry at exit
    private class AtExit extends Thread {

        @Override
        public void run() {
            try {
                volunteerRegistry.remove(name);
            } catch (RemoteException e) { }
        }
    }
}
