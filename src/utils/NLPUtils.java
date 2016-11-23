package utils;

import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by bozyurt on 6/9/16.
 */
public class NLPUtils {

    public static NameFinderME initializeNameFinder() throws IOException {
        TokenNameFinderModel model;
        InputStream in = null;
        try {
            in = NLPUtils.class.getClassLoader().getResourceAsStream("models/en-ner-person.bin");
            model = new TokenNameFinderModel(in);        	
            return new NameFinderME(model);
        } finally {
            Utils.close(in);
        }
    }
    public static ChunkerME initializeChunker() throws IOException {
        ChunkerModel model;
        InputStream cin = null;
        try {
            cin = NLPUtils.class.getClassLoader().getResourceAsStream(
                    "models/en-chunker.bin");
            model = new ChunkerModel(cin);
            return new ChunkerME(model);
        } finally {
            Utils.close(cin);
        }
    }

    public static POSTaggerME initializePOSTagger() throws IOException {
        POSModel model;
        InputStream pin = null;
        try {
            pin = NLPUtils.class.getClassLoader().getResourceAsStream(
                    "models/en-pos-maxent.bin");
            model = new POSModel(pin);
            return new POSTaggerME(model);
        } finally {
            Utils.close(pin);
        }
    }

    public static SentenceDetectorME initializeSentenceDetector() throws IOException {
        SentenceModel model;
        InputStream in = null;
        try {
            in = NLPUtils.class.getClassLoader().getResourceAsStream("models/en-sent.bin");
            model = new SentenceModel(in);
            return new SentenceDetectorME(model);
        } finally {
            Utils.close(in);
        }
    }

    public static TokenizerME initializeTokenizer() throws IOException {
        TokenizerModel model;
        InputStream in = null;
        try {
            in = NLPUtils.class.getClassLoader().getResourceAsStream("models/en-token.bin");
            model = new TokenizerModel(in);
            return new TokenizerME(model);
        } finally {
            Utils.close(in);
        }
    }
}
