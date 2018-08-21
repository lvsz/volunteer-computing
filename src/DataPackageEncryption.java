import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.nio.ByteBuffer;

public class DataPackageEncryption {

    final private String encryptionSpec = "AES";
    final private String encryptionInstance = "AES/GCM/NoPadding";

    public byte[] encrypt(byte[] key, DataPackage dp) {
        byte[] encryptedData = null;
        try {
            byte[] data = dataPackage2byteArray(dp);
            byte[] iv = new byte[12];

            final Cipher cipher = Cipher.getInstance(encryptionInstance);
            SecretKey secretKey = new SecretKeySpec(key, encryptionSpec);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(data);

            ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + iv.length + cipherText.length);
            buffer.putInt(iv.length);
            buffer.put(iv);
            buffer.put(cipherText);

            return buffer.array();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return encryptedData;
    }

    public DataPackage decrypt(byte[] key, byte[] encryptedData) {
        DataPackage dp = null;
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedData);
            int ivLength = byteBuffer.getInt();
            byte[] iv = new byte[ivLength];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            final Cipher cipher = Cipher.getInstance(encryptionInstance);
            SecretKey secretKey = new SecretKeySpec(key, encryptionSpec);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            dp = byteArray2dataPackage(cipher.doFinal(cipherText));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return dp;
    }

    private byte[] dataPackage2byteArray(DataPackage dp) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] result = null;
        try {
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeObject(dp);
            out.flush();
            result = bos.toByteArray();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    private DataPackage byteArray2dataPackage(byte[] data) {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        DataPackage result = null;
        try {
            ObjectInput in = new ObjectInputStream(bis);
            result = (DataPackage) in.readObject();
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return result;
    }
}
