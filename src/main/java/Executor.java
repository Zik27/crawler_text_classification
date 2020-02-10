import org.apache.log4j.Logger;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by Zik on 14.04.2019.
 */
class Executor {
    private final static Logger logger = Logger.getLogger(Executor.class);
    private String[] url_recent = new String[220];
    private int count_site = 0;

    private void starting_threads(ExecutorService service, List<CallableDownload> task_list, int attempt) {
        List<Future<String>> futures = null;
        logger.info("Running threads for execution...");
        try {
            futures = service.invokeAll(task_list);
        } catch (InterruptedException e) {
            logger.warn("Interrupted exception:", e);
        }
        check_results(futures, service, attempt);
    }

    private void check_results(List<Future<String>> futures, ExecutorService service, int attempt)
    {
        List<CallableDownload> retry_list = new ArrayList<CallableDownload>();
        for (Future future : futures)
        {
            try {
                if (!future.get().toString().equals("1"))
                {
                    CallableDownload my_Callable = new CallableDownload(future.get().toString());
                    retry_list.add(my_Callable);
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted exception:", e);
            } catch (ExecutionException e) {
                logger.error("Execution exception:", e);
            }
        }
        if (!retry_list.isEmpty() && attempt < 3)
            starting_threads(service, retry_list, attempt + 1);
        if (attempt == 3)
            logger.warn("Number of attempts exceeded");
    }

    public void start() {
        ExecutorService service = Executors.newFixedThreadPool(15);
        String url = "https://www.lenta.ru";
        LoaderMain load = new LoaderMain();
        Elements links;
        try {
            logger.info("Downloading the main page of the site " + url);
            links = load.download_main_page(url);
        } catch (IOException e) {
            logger.error("Error load main page: " + url, e);
            return;
        }
        List<CallableDownload> future_list = new ArrayList<CallableDownload>();
        for (Element link : links) {
            if (Arrays.asList(url_recent).contains(url + link.attr("href")))
                continue;
            else {
                url_recent[count_site % 220] = url + link.attr("href");
                count_site++;
            }
            CallableDownload task = new CallableDownload(url + link.attr("href"));
            future_list.add(task);
        }
        if (future_list.isEmpty()) {
            logger.info("No new news.");
            service.shutdown();
            return;
        }
        starting_threads(service, future_list, 1);
        logger.info("Completed.");
        service.shutdown();
    }
}
