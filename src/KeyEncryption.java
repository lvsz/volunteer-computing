import javax.crypto.*;
import java.security.*;

public class KeyEncryption {

    private final int keySize = 2048;
    private final String encryptionInstance = "RSA";

    private KeyPairGenerator keyGen;
    private Cipher cipher;

    public KeyEncryption() {
        try {
            cipher = Cipher.getInstance(encryptionInstance);
            keyGen = KeyPairGenerator.getInstance(encryptionInstance);
            keyGen.initialize(keySize);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public KeyPair generateKeys() {
        return keyGen.generateKeyPair();
    }

    public byte[] encrypt(PublicKey key, byte[] input) {
        byte[] result = null;
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key);
            result =  cipher.doFinal(input);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    public byte[] decrypt(PrivateKey key, byte[] input) {
        byte[] result = null;
        try {
            cipher.init(Cipher.DECRYPT_MODE, key);
            result = cipher.doFinal(input);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return  result;
    }
}
