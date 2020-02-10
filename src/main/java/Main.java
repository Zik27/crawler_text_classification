/**
 * Created by Zik on 01.04.2019.
 */
import java.io.IOException;
import java.util.Scanner;
import org.apache.log4j.Logger;

public class Main {

    private final static Logger logger = Logger.getLogger(Main.class);

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        System.out.println("Enter:\n1 - Start the process of downloading documents from the site.\n" +
                "2 - Start the process of classification of documents.");
        String choose = in.next();
        switch (choose) {
            case "1":
                Executor download = new Executor();
                while (true) {
                    download.start();
                    try {
                        Thread.sleep(120000);
                    } catch (InterruptedException e) {
                        logger.error("InterruptedException", e);
                    }
                }
            case "2":
                ONLPClassfy.main();
                break;
            default:
                System.out.println("Incorrect value");
                break;
        }
    }
}
