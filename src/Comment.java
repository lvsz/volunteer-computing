import com.google.gson.annotations.SerializedName;

import java.io.Serializable;


public class Comment implements Serializable {
    @SerializedName("author")
    public String author;

    @SerializedName("body")
    public String body;

    @SerializedName("score")
    public Integer score = 0;

    @SerializedName("subreddit")
    public String subreddit;

    @SerializedName("id")
    public String id;

    @Override
    public String toString() {
        return "Comment{" +
                "author='" + author + '\'' +
                ", body='" + body + '\'' +
                ", score=" + score +
                ", subreddit='" + subreddit + '\'' +
                ", id='" + id + '\'' +
                '}';
    }
}