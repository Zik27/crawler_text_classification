import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import java.io.IOException;

/**
 * Created by Zik on 15.04.2019.
 */
class LoaderMain {
    private final static Logger logger = Logger.getLogger(LoaderMain.class);

    Elements download_main_page(String url) throws IOException {
        HttpClient client = HttpClientBuilder.create().build();
        HttpGet request = new HttpGet(url);
        HttpResponse response;
        response = client.execute(request);
        HttpEntity entity = response.getEntity();
        String content;
        content = EntityUtils.toString(entity);
        Document doc = Jsoup.parse(content);
        //Выбирает новости в домене lenta.ru, исключая moslenta.ru, motor.ru и т.д.
        Elements links = doc.select("a[href^=/news/]:not([class]), a[href^=/articles/]:not([class])," +
                "a[href^=/photo/]:not([class]), a[href^=/brief/]:not([class])");
        request.releaseConnection();
        return links;
    }
}
