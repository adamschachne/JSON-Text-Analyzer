//import net.didion.jwnl.data.Exc;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.util.Span;
import utils.NLPUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by bozyurt on 6/9/16.
 */
public class NLPHelper {
    private TokenizerME tokenizer;
    private SentenceDetectorME sentenceDetector;
    private POSTaggerME postTagger;
    private ChunkerME chunker;
    private NameFinderME nameFinder;

    public NLPHelper() throws IOException {
        this.sentenceDetector = NLPUtils.initializeSentenceDetector();
        this.postTagger = NLPUtils.initializePOSTagger();
        this.chunker = NLPUtils.initializeChunker();
        this.tokenizer = NLPUtils.initializeTokenizer();
        this.nameFinder = NLPUtils.initializeNameFinder();
    }


    public List<NP> processText(String text) {
        List<NP> npList = new ArrayList<NP>();
        String[] sentences = sentenceDetector.sentDetect(text);
        nameFinder.clearAdaptiveData();
        for (String sentence : sentences) {

            //  System.out.println(sentence);
            //  System.out.println("------------------------");
            String[] toks = tokenizer.tokenize(sentence);
            // remove any recognized names
            Span[] nameSpans = nameFinder.find(toks);
            if (nameSpans != null && nameSpans.length > 0) {
                for (int i = 0; i < toks.length; i++) {
                    boolean found = false;
                    for (int j = 0; j < nameSpans.length; j++) {
                        if (nameSpans[j].contains(i)) {
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        toks[i] = "X";
                    }
                }
                // replace sentence
                sentence = StringUtils.join(toks, " ");

            }

            Span[] tokSpans = tokenizer.tokenizePos(sentence);
            String[] posTags = postTagger.tag(toks);
            String[] chunkTags = chunker.chunk(toks, posTags);

            int len = chunkTags.length;
            int i = 0;
            while (i < len) {
                if (chunkTags[i].equals("B-NP")) {
                    int startIdx = i;
                    int idx = i + 1;
                    boolean hasMore = false;
                    while (idx < len && chunkTags[idx].equals("I-NP")) {
                        idx++;
                        hasMore = true;
                    }
                    if (hasMore) {
                        int endIdx = idx - 1;
                        if (posTags[startIdx].equals("DT")) {
                            startIdx++;
                        }
                        String phrase = sentence.substring(tokSpans[startIdx].getStart(), tokSpans[endIdx].getEnd());
                        NP np = new NP(phrase, tokSpans[startIdx].getStart(), tokSpans[endIdx].getEnd());
                        POS[] posArr = new POS[(endIdx - startIdx) + 1];
                        np.posArr = posArr;
                        for (int j = startIdx; j <= endIdx; j++) {
                            POS pos = new POS();
                            pos.token = toks[j];
                            pos.pos = posTags[j];
                            pos.start = String.valueOf(tokSpans[j].getStart());
                            pos.end = String.valueOf(tokSpans[j].getEnd());
                            posArr[j - startIdx] = pos;
                        }
                        npList.add(np);
                        i = idx;
                        continue;
                    } else {
                        if (!posTags[i].equals("PRP") && !posTags[i].equals("DT")) {
                            NP np = new NP(toks[i], tokSpans[i].getStart(), tokSpans[i].getEnd());
                            POS pos = new POS();
                            pos.token = toks[i];
                            pos.pos = posTags[i];
                            pos.start = String.valueOf(tokSpans[i].getStart());
                            pos.end = String.valueOf(tokSpans[i].getEnd());

                            np.posArr = new POS[]{pos};
                            npList.add(np);
                        }
                    }

                }
                i++;
            }
        }

        return npList;
    }


    public static class NP {
        String text;
        int start;
        int end;
        POS[] posArr;

        public NP(String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }

        public String getText() {
            return text;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public POS[] getPosArr() {
            return posArr;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("NP{");
            sb.append("text='").append(text).append('\'');
            sb.append(", start=").append(start);
            sb.append(", end=").append(end);
            sb.append(", posArr=").append(Arrays.toString(posArr));
            sb.append('}');
            return sb.toString();
        }
    }

    public static void main(String[] args) throws Exception {
        NLPHelper helper = new NLPHelper();
        String text = "This is project presents data related to discharge measurements from the Bisley Watershed " +
                "in the Luquillo Mountains.Long-term rainfall and discharge data from the Luquillo Experimental " +
                "Forest (LEF) were analysed to develop relationships between rainfall, stream-runoff, and elevation.";
        List<NP> npList = helper.processText(text);
        for (NP np : npList) {
            System.out.println(np);
        }

    }
}
