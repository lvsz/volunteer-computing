import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.security.*;
import java.util.*;

public class CommandServer implements Commander {

    private final int packageSize = 64;

    private String[] file;
    private int N;

    private String name;
    private List<Comment > comments;
    private ArrayList<DataPackage> dataPackages;
    private ArrayList<Pair<String, Float>> results[];

    private DataPackageEncryption dataEncryption;
    private byte[] dataKey;
    private SecretKey hashKey;
    private KeyEncryption keyEncryption;

    private Registry registry;
    private VolunteerRegistry volunteerRegistry;
    private HashSet<String> bannedClients;

    private boolean available = false;

    public CommandServer(String[] file, int N) {
        try {
            this.file = file;
            this.N = N;
            dataPackages = new ArrayList<>();

            comments = RedditCommentLoader.readData(file);
            for (int i = 0, id = 0; i < comments.size(); i += packageSize, ++id) {
                dataPackages.add(new DataPackage(id, comments.subList(i, Math.min(i + packageSize, comments.size()))));
            }

            results = new ArrayList[dataPackages.size()];
            for (int i = 0; i < results.length; ++i)
                results[i] = new ArrayList<>();

            SecureRandom secureRandom = new SecureRandom();
            dataKey = new byte[16];
            secureRandom.nextBytes(dataKey);
            hashKey = new SecretKeySpec(dataKey, HASH_SPEC);

            dataEncryption = new DataPackageEncryption();
            keyEncryption = new KeyEncryption();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void makeAvailable() {
        try {
            Commander stub = (Commander) UnicastRemoteObject.exportObject(this, 0);
            registry = LocateRegistry.getRegistry();

            bannedClients = new HashSet<>();
            volunteerRegistry = (VolunteerRegistry) registry.lookup(VolunteerRegistry.name);
            name = volunteerRegistry.generateId();
            registry.rebind(name, stub);

            dataPackages.forEach(dp -> dp.server = name);

            available = true;
            System.out.println("Command server " + name + " now online");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() {
        if (!available)
            makeAvailable();

        try {
            for (int i = 0; i < N; ++i) {
                System.out.println("Starting round " + i + " of " + N);
                int idx = 0;
                while (idx < dataPackages.size()) {
                    ClientInterface client = getClient();

                    int load = 0;
                    int maxLoad = Math.min(idx + client.size() / packageSize, dataPackages.size()) - idx;
                    for (int j = idx; j < maxLoad + idx; ++j) {
                        // send fewer tasks if next packages have already been analysed by this user
                        if (packageAlreadyAnalyzedByClient(j, client.getName()))
                            break;
                        else
                            ++load;
                    }

                    if (load > 0) {
                        ServerThread thd = new ServerThread(client, dataPackages.subList(idx, idx + load));
                        thd.start();
                        idx += load;
                    } else {
                        volunteerRegistry.set(client.getName(), VolunteerRegistry.FREE);
                    }
                }
            }

            // check which packages haven't been analysed N times yet
            // keep sending them seperately to users till all is done
            boolean allDone;
            do {
                allDone = true;

                for (int i = 0; i < results.length; ++i) {
                    if (results[i].size() < N) {
                        allDone = false;

                        ClientInterface client = getClient();
                        if (packageAlreadyAnalyzedByClient(i, client.getName())) {
                            volunteerRegistry.set(client.getName(), VolunteerRegistry.FREE);
                        } else {
                            ServerThread thd = new ServerThread(client, dataPackages.subList(i, i + 1));
                            thd.start();
                        }
                    }
                }
            } while (!allDone);

            double totalScore = 0;
            for (ArrayList<Pair<String, Float>> result : results) {
                for (Pair<String, Float> pair : result) {
                    totalScore += pair.snd / N;
                }
            }

            System.out.println("Analysis done, score is " + totalScore + " for " + comments.size() + " comments.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // returns true if package was already analysed by given client
    private boolean packageAlreadyAnalyzedByClient(int id, String clientName) {
        for (Pair<String, Float> pair : results[id])
            if (pair.fst.equals(clientName))
                return true;

        return false;
    }

    private ClientInterface getClient() throws InterruptedException, RemoteException, NotBoundException {
        while (true) {
            String clientName = volunteerRegistry.get();
            while (clientName == null || bannedClients.contains(clientName)) {
                Thread.sleep(100);
                clientName = volunteerRegistry.get();
            }

            ClientInterface client = (ClientInterface) registry.lookup(clientName);

            if (volunteerRegistry.set(clientName, VolunteerRegistry.BUSY))
                return client;
        }
    }

    // function to check results so far
    // bans users that sent faulty information
    private void verifyResults(ArrayList<Pair<String, Float>> pairs) {
        HashMap<Float, Pair<String, Integer>> scoreCount = new HashMap<>();
        for (Pair<String, Float> pair : pairs) {
            float score = pair.snd;
            Pair<String, Integer> count = scoreCount.getOrDefault(score, new Pair<>(pair.fst, 0));
            count.snd += 1;
            scoreCount.put(score, count);
        }
        for (float key : scoreCount.keySet()) {
            Pair<String, Integer> pair = scoreCount.get(key);
            if (pair.snd == 1) {
                for (int i = 0; i < pairs.size(); ++i) {
                    if (pairs.get(i).fst.equals(pair.snd)) {
                        pairs.remove(pairs.get(i));
                        break;
                    }
                }
                bannedClients.add(pair.fst);
                System.out.println("Banned user " + pair.fst);
            }
        }
    }

    @Override
    public synchronized void result(byte[] data) throws RemoteException, NotBoundException {
        DataPackage dp = dataEncryption.decrypt(dataKey, data);

        if (results[dp.id].size() < N && !bannedClients.contains(dp.analyser)) {
            Pair<String, Float> resultTuple = new Pair<>(dp.analyser, dp.score);
            results[dp.id].add(resultTuple);

            if (results[dp.id].size() > 2) {
                verifyResults(results[dp.id]);
            }
        }

        ClientInterface sender = (ClientInterface) registry.lookup(dp.analyser);
        if (sender.isFree())
            volunteerRegistry.set(sender.getName(), VolunteerRegistry.FREE);
    }

    @Override
    public synchronized byte[] getKey(PublicKey clientKey) {
        // return assymetrically encrypted data key
        return keyEncryption.encrypt(clientKey, dataKey);
    }

    public static void main(String[] args) {
        String fileName = "./files/dataset_2.json";
        int N = 3;
        try {
            fileName = args[0];
        } catch (IndexOutOfBoundsException e) { }

        if (args.length > 1) {
            try {
                N = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Excpected integer as 2nd argument, defaulting replication factor to " + N);
            }
        }

        new CommandServer(new String[]{fileName}, N).run();
    }

    // threads that send data to clients, so server doesn't have to wait
    // for each to finish before it can start sending the next package
    private class ServerThread extends Thread {

        ClientInterface client;
        List<DataPackage> dataPackages;

        private ServerThread(ClientInterface client, List<DataPackage> dataPackages) {
            this.client = client;
            this.dataPackages = dataPackages;
        }

        public void run() {
            try {
                Mac mac = Mac.getInstance(HASH_SPEC);
                mac.init(hashKey);

                byte[][] data = new byte[dataPackages.size()][];
                byte[][] hash = new byte[data.length][];

                for (int i = 0; i < data.length; ++i) {
                    data[i] = dataEncryption.encrypt(dataKey, dataPackages.get(i));
                    hash[i] = mac.doFinal(data[i]);
                }

                client.store(name, data, hash);
                client.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
