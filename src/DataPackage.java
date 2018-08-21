import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DataPackage implements Serializable {

    public String server;
    public String analyser;
    public int id;
    public float score;
    public ArrayList<Comment> comments;

    private byte[] encryptedData;

    public DataPackage(int id, List<Comment> comments) {
        this.id = id;
        this.comments = new ArrayList<>();
        this.comments.addAll(comments);
    }
}
