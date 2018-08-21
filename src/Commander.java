import java.rmi.*;
import java.security.PublicKey;

public interface Commander extends Remote {
    public final String HASH_SPEC = "HmacSHA256";

    public void result(byte[] data) throws RemoteException, NotBoundException;
    public byte[] getKey(PublicKey publicKey) throws RemoteException;
}
