/**
 * Created by Zik on 26.06.2019.
 */
import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import opennlp.tools.doccat.*;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.*;
import org.apache.log4j.Logger;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

class ONLPClassfy {
    private final static Logger logger = Logger.getLogger(ONLPClassfy.class);

    private List<String> get_stopwords_list(){
        Scanner s = null;
        try {
            s = new Scanner(new File("C:\\Users\\Zik\\IdeaProjects\\Elastic\\src\\main\\resources\\Stopwords.txt"));
        } catch (FileNotFoundException e) {
            logger.error("File Not Found", e);
        }
        ArrayList<String> list = new ArrayList<String>();
        while (s != null && s.hasNext()){
            list.add(s.next());
        }
        if (s != null) {
            s.close();
        }
        return list;
    }

    private String delete_stopwords(String text, List <String> stopwords){
        for (String word : stopwords){
            text = text.replace(" " + word + " ", " ");
            if (text.startsWith(word + " "))
                text = text.substring(word.length() + 1);
            if (text.endsWith(" " + word))
                text = text.substring(0, text.length() - word.length());
        }
        return text;
    }

    private  List<Map> get_doc_for_classify(){
        Client client = null;
        List<Map> text_classify = new ArrayList<>();
        try {
            client = new PreBuiltTransportClient(
                    Settings.builder().put("cluster.name", "docker-cluster").build())
                    .addTransportAddress(new TransportAddress(InetAddress.getByName("192.168.44.128"), 9300));
        } catch (UnknownHostException e) {
            logger.error("UnknownHostException", e);
        }
        SearchResponse sr = client.prepareSearch("myapp_with_tags").setSize(200).get();
        SearchHit[] hits = sr.getHits().getHits();
        for (SearchHit hit : hits)
            text_classify.add(hit.getSourceAsMap());
        return text_classify;
    }

    private List<String> get_tags_es(){
        List <String> tags = new ArrayList<>();
        Client client = null;
        try {
            client = new PreBuiltTransportClient(
                    Settings.builder().put("cluster.name", "docker-cluster").build())
                    .addTransportAddress(new TransportAddress(InetAddress.getByName("192.168.44.128"), 9300));
        } catch (UnknownHostException e) {
            logger.error("UnknownHostException", e);
        }
        SearchResponse sr = client.prepareSearch("myapp_with_tags")
                .addAggregation(AggregationBuilders.terms("tags").field("tag").size(20))
                .execute().actionGet();
        Terms tag_aggregation = sr.getAggregations().get("tags");
        List <? extends Terms.Bucket> tag_bucket = tag_aggregation.getBuckets();
        for (Terms.Bucket agg:tag_bucket)
            tags.add(agg.getKeyAsString());
        client.close();
        return tags;
    }

    private void prepare_data() throws IOException {
        List <String> tags = get_tags_es();
        List <String> stopwords = get_stopwords_list();
        String text;
        int count_doc = 1;
        FileWriter writer = new FileWriter("C:\\Users\\Zik\\IdeaProjects\\Elastic\\src\\main\\resources\\train_data.txt", false);
        Reader reader = Files.newBufferedReader(Paths.get("C:\\Users\\Zik\\IdeaProjects\\Elastic\\src\\main\\resources\\lenta-ru-news.csv"));
        CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build();
        // Reading Records One by One in a String array
        String[] nextRecord;
        logger.info("Starting preparing data...");
        while ((nextRecord = csvReader.readNext()) != null) {
            if (!tags.contains(nextRecord[3]) || nextRecord[2].isEmpty()){
                continue;
            }
            text = nextRecord[2].replace("\n", "").replaceAll("[^А-Яа-я ]", "").toLowerCase();
            text = delete_stopwords(text, stopwords);
            writer.append(nextRecord[3].replace(" ", "_")).append(" ");
            writer.append(text);
            if (!text.substring(0, text.length() - 1).equals("\n"))
                writer.append("\n");
            writer.flush();
            if (count_doc == 150000)
                break;
            count_doc++;
        }
        logger.info("The training data is ready.");
    }

    private void train() {
        String onlpModelPath = "C:\\Users\\Zik\\IdeaProjects\\Elastic\\src\\main\\resources\\model";
        File file_model = new File("C:\\Users\\Zik\\IdeaProjects\\Elastic\\src\\main\\resources\\train_data.txt");
        DoccatModel model = null;
        OutputStream onlpModelOutput = null;
        TrainingParameters params = TrainingParameters.defaultParams();
        DoccatFactory factory = new DoccatFactory();
        try {
            InputStreamFactory isf = new MarkableFileInputStreamFactory(file_model);
            // Read each training instance (Reads a plain text file and return each line as a String object.)
            ObjectStream<String> lineStream = new PlainTextByLineStream(isf, "UTF-8");
            ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream);
            logger.info("Start training the model...");
            logger.info("Settings: " + params.getObjectSettings());
            model = DocumentCategorizerME.train("ru", sampleStream, params, factory);
        } catch (IOException e) {
            logger.error("IOException when training the model", e);
        }
        logger.info("Saving model to the file...");
        try {
            onlpModelOutput = new BufferedOutputStream(new FileOutputStream(onlpModelPath));
            model.serialize(onlpModelOutput);
        } catch (IOException e) {
            logger.error("IOException when saving the model", e);
        } finally {
            if (onlpModelOutput != null) {
                try {
                    onlpModelOutput.close();
                } catch (IOException e) {
                    logger.error("IOException when closing BufferedOutputStream", e);
                }

            }
        }
    }

    private String get_category(String text) throws IOException {
        List <String> stopwords = get_stopwords_list();
        Tokenizer tokenizer = WhitespaceTokenizer.INSTANCE;
        text = text.replaceAll("[^А-Яа-я ]", "").toLowerCase();
        text = delete_stopwords(text, stopwords);
        String[] tokens = tokenizer.tokenize(text);
        String classificationModelFilePath = "C:\\Users\\Zik\\IdeaProjects\\Elastic\\src\\main\\resources\\model";
        InputStream is = new FileInputStream(classificationModelFilePath);
        DoccatModel classificationModel = new DoccatModel(is);
        DocumentCategorizerME classificationME = new DocumentCategorizerME(classificationModel);
        double[] classDistribution = classificationME.categorize(tokens);
        return classificationME.getBestCategory(classDistribution);
    }

    static void main() {
        int correct_tags = 0;
        File file_model = new File("C:\\Users\\Zik\\IdeaProjects\\Elastic\\src\\main\\resources\\train_data.txt");
        if (!file_model.exists()) {
            try {
                new ONLPClassfy().prepare_data();
            } catch (IOException e) {
                logger.error("IOException when preparing data", e);
            }
            new ONLPClassfy().train();
        }
        logger.info("Getting files for classification from elasticsearch...");
        List<Map> docs = new ONLPClassfy().get_doc_for_classify();
        logger.info("The beginning of the classification process...");
        for (Map i : docs){
            String predict = null;
            try {
                predict = new ONLPClassfy().get_category((String)i.get("text"));
            } catch (IOException e) {
                logger.error("IOException in the classification process", e);
            }
            String true_tag = (String) i.get("tag");
            true_tag = true_tag.replace(" ", "_");
            if (true_tag.equals(predict))
                correct_tags++;
            System.out.println("True tag: " + true_tag + " | " + "Predicted tag: " + predict);
        }
        System.out.println("Accuracy = " + correct_tags * 1.0 / docs.size() * 100);
    }
}
