import org.datavec.api.util.ClassPathResource;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.apache.log4j.Logger;
import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Created by Zik on 22.04.2019.
 */
public class Classification {
    private final static Logger logger = Logger.getLogger(Classification.class);

//    private List<String> Stopwords_list(){
//        Scanner s = null;
//        try {
//            s = new Scanner(new File("C:\\Users\\Zik\\IdeaProjects\\Elastic\\src\\main\\resources\\Stopwords.txt"));
//        } catch (FileNotFoundException e) {
//            logger.error("File Not Found", e);
//        }
//        ArrayList<String> list = new ArrayList<String>();
//        while (s.hasNext()){
//            list.add(s.next());
//        }
//        s.close();
//        return list;
//    }

    private Word2Vec create_word2vec_model() throws FileNotFoundException {
        String filePath = new ClassPathResource("Full_text.txt").getFile().getAbsolutePath();
        logger.info("Load & Vectorize Sentences...");
        // Strip white space before and after for each line
        SentenceIterator iter = new BasicLineIterator(filePath);
        TokenizerFactory t = new DefaultTokenizerFactory();
        t.setTokenPreProcessor(new CommonPreprocessor());
        AbstractCache<VocabWord> cache = new AbstractCache<>();

        logger.info("Building model....");
        Word2Vec vec = new Word2Vec.Builder()
                .minWordFrequency(5)
                .layerSize(100)
                .seed(42)
                .windowSize(5)
                .iterate(iter)
                .tokenizerFactory(t)
                .vocabCache(cache)
                //.stopWords(Stopwords_list())
                .build();

        logger.info("Fitting Word2Vec model....");
        vec.fit();
        logger.info("Save vectors....");
        System.out.println(cache.wordAtIndex(10));
        System.out.println(cache.wordAtIndex(1));
        System.out.println(cache.wordAtIndex(2));
        System.out.println(cache.wordAtIndex(3));
        WordVectorSerializer.writeWord2VecModel(vec, "C:\\Users\\Zik\\IdeaProjects\\Elastic\\src\\main\\resources\\Word2Vec_model.txt");
        return vec;
    }

    public void start() {
//        try {
//            testDifferentLabels();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        Word2Vec word2Vec = null;
        File file_model = new File("C:\\Users\\Zik\\IdeaProjects\\Elastic\\src\\main\\resources\\Word2Vec_model.txt");
        if (!file_model.exists()) {
            try {
                word2Vec = create_word2vec_model();
            } catch (FileNotFoundException e) {
                logger.error("File Not Found", e);
            }
        } else {
            word2Vec = WordVectorSerializer.readWord2VecModel(file_model);
        }
//        WeightLookupTable weightLookupTable = word2Vec.lookupTable();
//        Iterator<INDArray> vectors = weightLookupTable.vectors();
//        double[] wordVector = word2Vec.getWordVector("россия");
//        System.out.println(Arrays.toString(wordVector));
        logger.info("Closest Words:");
        Collection<String> lst = word2Vec.wordsNearestSum("россия", 10);
        System.out.println(lst);
    }

    public void testDifferentLabels() throws Exception {
        //tokenization with lemmatization,part of speech taggin,sentence segmentation

        TokenizerFactory tokenizerFactory = new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new CommonPreprocessor());
        Tokenizer tokenizer = tokenizerFactory.create("This tokenizer preprocessor 4 implements basic cleaning inherited from CommonPreprocessor does english Porter stemming on tokens.");
        //get the whole list of tokens
        List<String> tokens = tokenizer.getTokens();
        System.out.println(tokens);
        Client client = null;
        try {
            client = new PreBuiltTransportClient(
                    Settings.builder().put("cluster.name", "docker-cluster").build())
                    .addTransportAddress(new TransportAddress(InetAddress.getByName("192.168.44.128"), 9300));
        } catch (UnknownHostException e) {
            System.err.println("Error: UnknownHostException");
        }
        SearchResponse sr = client.prepareSearch("myapp_with_tags")
                .addAggregation(
                        AggregationBuilders.terms("tags").field("tag").size(20)
                )
                .execute().actionGet();
        Terms tag_aggregation = sr.getAggregations().get("tags");
        List <? extends Terms.Bucket> tag_bucket = tag_aggregation.getBuckets();
        System.out.println(tag_bucket.size());
        for (Terms.Bucket agg:tag_bucket)
            System.out.println(agg.getKeyAsString() + "   " + agg.getDocCount());
        client.close();
    }
    }

