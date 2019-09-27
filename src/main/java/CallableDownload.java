import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;

/**
 * Created by Zik on 17.04.2019.
 */
public class CallableDownload implements Callable<String> {
    private String url;
    private int status = 0;
    private final static Logger logger = Logger.getLogger(CallableDownload.class);

    CallableDownload(String url)
    {
        this.url=url;
    }

    private String send_to_es(String article_json)
    {
        Client client = null;
        try {
            client = new PreBuiltTransportClient(
                    Settings.builder().put("cluster.name","docker-cluster").build())
                    .addTransportAddress(new TransportAddress(InetAddress.getByName("192.168.44.128"), 9300));
        } catch (UnknownHostException e) {
            logger.error("UnknownHostException", e);
            status = 0;
        }
        //System.out.println(article_json);
        /*
        ** API-интерфейс prepareIndex добавляет (или обновляет) документ к индексу.
        ** Если ранее был не создан индекс и тип, ElasticSearch создаст его на лету с параметрами по умолчанию.
        */
        IndexResponse response = status == 0 ? null : client.prepareIndex("myapp", "lenta")
                .setSource(article_json, XContentType.JSON)
                .get();
        if (response.status() == RestStatus.CONFLICT)
        {
            status = 0;
            logger.warn("Exception occurred while Insert Index");
        }
        else
        {
            String index = response.getIndex();
            String type = response.getType();
            String id = response.getId();
            logger.info("Index has been created successfully with Index: " + index + " / Type: " + type + " ID: " + id);
        }
        if (client != null)
            client.close();
        return (status == 0 ? url : "1");
    }

    private String serialized_to_json(String content)
    {
        Document doc = Jsoup.parse(content);
        String[] header_tmp = doc.select("title").first().text().split(": ");
        String title = header_tmp[0];
        String tag = header_tmp[header_tmp.length - 2];
        String time = doc.select("time[datetime].g-date").attr("datetime").split("\\+")[0];
        String text = "";
        Elements links = doc.select("p");
        for (Element link : links)
            text += link.text();
        String serialized = null;
        try {
            serialized = new ObjectMapper().writeValueAsString(new Article(title, tag, text, time));
        } catch (JsonProcessingException e) {
            logger.error("Serialization error", e);
            status = 0;
        }
        //System.out.println(Thread.currentThread().getName());
        return (status == 0 ? url : send_to_es(serialized));
    }

    private String get_body_html(HttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        return (EntityUtils.toString(entity));
    }

    public String call() {
        HttpClient client = HttpClientBuilder.create().build();
        HttpGet request = new HttpGet(url);
        HttpResponse response = null;
        String body_html = null;
        try {
            response = client.execute(request);
            body_html = get_body_html(response);
            status = 1;
        } catch (IOException e) {
            logger.error("Connection#1 Error: IOException", e);
        }
        int statusCode = response == null ? -1 : response.getStatusLine().getStatusCode();
        //5xx: Server Error:
        // 503 Service Unavailable; 504 Gateway Timeout; 522 Connection Timed Out; 524 A Timeout Occurred;
        if (statusCode == 503 || statusCode == 504 || statusCode == 522 || statusCode == 524)
        {
            try {
                Thread.sleep(1000);
                response = client.execute(request);
                body_html = get_body_html(response);
                status = 1;
            } catch (IOException e) {
                logger.error("Connection#2 Error: IOException", e);
            } catch (InterruptedException e) {
                logger.error("InterruptedException", e);
            }
        }
        request.releaseConnection();
        return (status == 0 ? url : serialized_to_json(body_html));
    }
}
