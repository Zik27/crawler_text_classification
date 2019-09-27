import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Created by Zik on 15.04.2019.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Article {
    public String title;
    public String tag;
    public String text;
    public String time;

    public Article(String title, String tag, String text, String time) {
        this.title = title;
        this.tag = tag;
        this.text = text;
        if (!time.equals(""))
            this.time = time;
    }
}
