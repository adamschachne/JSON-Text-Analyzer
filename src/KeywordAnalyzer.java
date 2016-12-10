import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.commons.codec.binary.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import com.google.gson.Gson;

public class KeywordAnalyzer {

	private OWLOntologyManager manager;
	private OWLDataFactory df;
	private OWLOntology cinergi, extensions;
	private LRUCache<IRI, IRI> cache;
	private List<Output> output;
	private List<String> stoplist;
	private List<String> nullIRIs;
	private Gson gson; 
	private LinkedHashMap<String, IRI> exceptionMap;
	private int counter;
	private HttpClient client;
	private NLPHelper nlpHelper;
	
	public KeywordAnalyzer(OWLOntologyManager manager, OWLDataFactory df, OWLOntology ont, 
			OWLOntology extensions, Gson gson, List<String> stoplist, 
			LinkedHashMap<String,IRI> exceptionMap, List<String> nullIRIs) throws IOException {
		output = new ArrayList<Output>();
		this.manager = manager;
		this.df = df;
		//this.cinergi = cinergi;
		this.extensions = extensions;
		this.gson = gson;
		this.stoplist = stoplist;
		this.exceptionMap = exceptionMap;
		this.nullIRIs = nullIRIs;
		client = new DefaultHttpClient();
		counter = 0;
		this.nlpHelper = new NLPHelper();
	}

	public List<Output> getOutput()	{
		return output;
	}
	
	public void processDocument(Document doc) throws UnsupportedEncodingException {

		String text = doc.getTitle() + ", " + doc.getText();		
		
		//System.out.println("processing: " + doc.getTitle());
		//long before = System.currentTimeMillis();
		ArrayList<Keyword> keywords = new ArrayList<Keyword>();
		HashSet<String> visited = new HashSet<String>();
		try {
			//keywords = process(text, visited);
			keywords = process2(text, visited);
		} catch (Exception e1) {
			e1.printStackTrace();
		}		
		if (!keywords.isEmpty())
		{	
			addDocumentToOutput(doc, keywords.toArray(new Keyword[keywords.size()]));
			//System.err.println("time to process document: " + (System.currentTimeMillis() - before) + " number of keywords: " + keywords.size());
		}
		else
		{
			System.err.println(doc.getTitle() + ": " + "no keywords");
		}
	}

	private void addDocumentToOutput(Document doc, Keyword[] keywords) {
		
		Output toAdd = new Output();
		toAdd.setKeyword(keywords);
		toAdd.setId(doc.getId());
		toAdd.setText(doc.getText());
		toAdd.setTitle(doc.getTitle());
		
		output.add(toAdd);		
		
	}

	public void processDocuments(Document[] docs) throws UnsupportedEncodingException {
		
		for (Document doc : docs)
		{			
			processDocument(doc);
		}
	}	
	
	public String readURL(String urlString) throws ClientProtocolException, IOException
	{
		
		String jsonStr = null;
	
		HttpGet httpGet = new HttpGet(urlString);		
		httpGet.addHeader("Content-Type", "application/json;charset=utf-8");
		try {
		    HttpResponse response = client.execute(httpGet);
		    
		    HttpEntity entity = response.getEntity();
		    if (entity != null) {
		        jsonStr = EntityUtils.toString(entity);
		        		        
		    }
		    if (response.getStatusLine().getStatusCode() == 404 
		    		|| response.getStatusLine().getStatusCode() == 406)
		    {
		    	jsonStr = null;
		    }
		    
		} finally {
		     httpGet.releaseConnection();
		}
		return jsonStr;
	}
	
	public Vocab vocabTerm(String input) throws UnsupportedEncodingException
	{
		//String prefix = "http://tikki.neuinfo.org:9000/scigraph/vocabulary/term/";
		String prefix = "http://ec-scigraph.sdsc.edu:9000/scigraph/vocabulary/term/";
		String suffix = "?limit=10&searchSynonyms=true&searchAbbreviations=false&searchAcronyms=false";
		String urlInput = URLEncoder.encode(input, StandardCharsets.UTF_8.name()).replace("+", "%20");
	
		if (input == null) {
			return null;
		}

		String urlOut = null;
		if (stoplist.contains(input.toLowerCase()))
		{
			return null;
		}
		try {			
			urlOut = readURL(prefix+urlInput+suffix);	
		} catch (Exception e) {
			urlOut = null;
		}
		
		if (urlOut == null) {
			if (input.contains("-")) {
	            // if there is a hyphen then separate it there
	            int i = input.indexOf("-");
	            String[] substr = {input.substring(0, i), input.substring(i + 1)};
	            return vocabTerm(substr[0] + " " + substr[1]);
			}
			return null;
		}
		
		// fixed for ec-scigraph
		Concept[] concepts = gson.fromJson(urlOut, Concept[].class);
		//Vocab vocab = gson.fromJson(urlOut, Vocab.class);
		ArrayList<Concept> conceptList = new ArrayList<Concept>(Arrays.asList(concepts));
		Vocab vocab = new Vocab(conceptList);
		// preliminary check
		if (stoplist.contains(vocab.concepts.get(0).labels.get(0).toLowerCase()))
			return null; 
		return vocab;
	}
	
	// returns the cinegiFacet associated with any class, returns null if there is not one
	public List<IRI> getFacetIRI(OWLClass cls, HashSet<IRI> visited)
	{		
	//	System.err.println(OWLFunctions.getLabel(cls, manager, df));
		if (visited.contains(cls.getIRI()))
		{
			// prevent infinite loops
			return null;
		}
		visited.add(cls.getIRI());		
		
		if (cls.getIRI().equals("http://www.w3.org/2002/07/owl#Thing"))
			return null; // if the class is Thing, then stop
		
		if (OWLFunctions.hasCinergiFacet(cls, extensions, df))
		{
			//System.err.println(OWLFunctions.getLabel(cls, manager, df) + "is a cinergi Facet");
			// if the class is a cinergiFacet already then return itself
			return new ArrayList<IRI>(Arrays.asList(cls.getIRI()));
		}	
		if (!cls.getEquivalentClasses(manager.getOntologies()).isEmpty()) // check all equivalent classes for cinergiFacet
		{ 		
			for (OWLClassExpression oce : cls.getEquivalentClasses(manager.getOntologies()))
			{
				if (oce.getClassExpressionType().toString().equals("Class"))
				{
					OWLClass equivalentClass = oce.getClassesInSignature().iterator().next();
					if (OWLFunctions.hasCinergiFacet(equivalentClass, extensions, df))
					{
						return new ArrayList<IRI>(Arrays.asList(equivalentClass.getIRI()));
					}
				}				
			}
		}	
		if (OWLFunctions.hasParentAnnotation(cls, extensions ,df))
		{		    
			ArrayList<IRI> parentIRIs = new ArrayList<IRI>();
			for (OWLClass c : OWLFunctions.getParentAnnotationClass(cls, extensions, df))
			{
			/*	if (OWLFunctions.isTopLevelFacet(c, extensions, df))
				{
					System.err.println("debug here");
					// this class's cinergiParent is a topLevel facet and its not a facet; should be excluded
					return null; 
				}
			*/  List<IRI> parentFacet = getFacetIRI(c, visited);
				if (parentFacet == null)
					return null;
				parentIRIs.addAll(parentFacet);   
			}	
			return parentIRIs;
		}			

		if (!cls.getSuperClasses(manager.getOntologies()).isEmpty())
		{
			for (OWLClassExpression oce : cls.getSuperClasses(manager.getOntologies())) // subClassOf
			{
				if (oce.getClassExpressionType().toString().equals("Class"))
				{	
					OWLClass cl = oce.getClassesInSignature().iterator().next();
					{
					if (OWLFunctions.getLabel(cl, manager, df).equals(OWLFunctions.getLabel(cls, manager, df)))
						continue; // skip if child of the same class
					}
				/*	if (OWLFunctions.isTopLevelFacet(cl, extensions, df))
					{
						// this class's cinergiParent is a topLevel facet and its not a facet; should be excluded\
						//System.err.println(OWLFunctions.getLabel(cl, manager, df) + " is being excluded; from: " + OWLFunctions.getLabel(cls, manager, df));
						return null; 
					} */
					List<IRI> retVal = getFacetIRI(oce.getClassesInSignature().iterator().next(), visited); 
					
					if (retVal == null)
						continue;
					
					return retVal;	
				}
			}
		}
		return null;
	}
	
	// given a (2nd level) cinergiFacet, returns a string of path facet2, facet1
    private String facetPath(OWLClass cls) {
        if (OWLFunctions.getParentAnnotationClass(cls, extensions, df).size() == 0) {
            System.err.println(OWLFunctions.getLabel(cls, manager, df) + " has no cinergiParent, terminating.");
            return "";
            //return null;
        }
        OWLClass cinergiParent = OWLFunctions.getParentAnnotationClass(cls, extensions, df).get(0);
        if (OWLFunctions.isTopLevelFacet(cinergiParent, extensions, df) ||
                cinergiParent.getIRI().equals(IRI.create("http://www.w3.org/2002/07/owl#Thing"))) {
            return (OWLFunctions.getLabel(cls, manager, df) + " | " + OWLFunctions.getLabel(cinergiParent, manager, df));
        } else {
            return (OWLFunctions.getLabel(cls, manager, df) + " | " + facetPath(cinergiParent));
        }
    }

	private ArrayList<Keyword> process(String testInput, HashSet<String> visited) throws Exception
	{
		String url = URLEncoder.encode(testInput, StandardCharsets.UTF_8.name());
		//String chunks = "http://tikki.neuinfo.org:9000/scigraph/lexical/chunks?text=";
		String chunks = "http://ec-scigraph.sdsc.edu:9000/scigraph/lexical/chunks?text=";		
		//System.out.println(chunks+url);
		//long time = System.currentTimeMillis();
		String json = readURL(chunks + url);
		//System.err.println("time to read url: " + (System.currentTimeMillis()-time));
	    ArrayList<Keyword> keywords = new ArrayList<Keyword>();
	    
	    // each result from chunking
	    Tokens[] tokens = gson.fromJson(json, Tokens[].class);
	
	    for (Tokens tok : tokens) // each chunk t
	    {
	    	//long time = System.currentTimeMillis();
	    	//System.out.println(tok.getToken());
	    	if (processChunk(tok, keywords, visited) == true)
	    		continue;
	    	
	    	POS[] parts = pos(gson, tok.getToken());
	    	
	    	for (POS p : parts)
	    	{	
	    		//Vocab vocab;
	    		//System.out.println(p.token + " " + p.pos);
	    		if (p.pos.equals("NN") || p.pos.equals("NNP") || p.pos.equals("NNPS") || p.pos.equals("NNS") || p.pos.equals("JJ"))
	    		{ 		   
	    			//System.out.println(p.token);
	    			// if there is a hyphen
	    			if (p.token.contains("-"))
		    		{
		    			// if there is a hyphen in the array of POS, then
		    			// break it into separate parts and process them individually
		    			int i = p.token.indexOf("-");
		    			String[] substr = {p.token.substring(0, i), p.token.substring(i+1)}; 
		    			Tokens tempToken = new Tokens(substr[0]+" "+substr[1]);
		    			if (processChunk(tempToken, keywords, visited) == true) // see if the phrase with a space replacing the hyphen exists
		    			{
		    			//	System.err.println("time to process " + p.token + " :" 
		    			//			+ (System.currentTimeMillis()-time));
		    				continue;
		    			}
		    		}
	    			else // doesnt contain a hyphen
	    			{
		    			// call vocab Term search for each of these tokens
		    			Tokens tempToken = new Tokens(tok);
		    			tempToken.setToken(p.token);
		    			if (processChunk(tempToken, keywords, visited) == true)
		    			{
		    			//	System.err.println("time to process " + p.token + " :" 
		    			//			+ (System.currentTimeMillis()-time));
		    				continue;
		    			}
		    	    		
	    			}    							
    			}
	    	}
	    }
		return keywords;	    			 
	}
	
	
	private ArrayList<Keyword> process2(String testInput, HashSet<String> visited) throws Exception {
        ArrayList<Keyword> keywords = new ArrayList<Keyword>();
        List<NLPHelper.NP> npList = nlpHelper.processText(testInput);
        for (NLPHelper.NP np : npList) {       	
        	
            Tokens tok = new Tokens(np.getText());
            tok.setStart(String.valueOf(np.getStart()));
            tok.setEnd(String.valueOf(np.getEnd()));
            
            int numKeywords = 0;            
            if (processChunk(tok, keywords, visited) == true) {
                continue;
            }
            POS[] parts = np.getPosArr();  
            if (parts.length > 2) {
                // try shorter phrases (IBO)
                boolean found = false;    
                for (int i = parts.length - 1; i >= 2; i--) {
                    StringBuilder sb = new StringBuilder();                   
                    int count = 0;
                    for (int j = 0; j < i; j++) {
                        if (isEligibleTerm(parts[j])) {
                            sb.append(parts[j].token).append(' ');
                            count++;
                        }
                    }
                    Tokens tempToken = new Tokens(tok);
                    tempToken.setToken(sb.toString().trim());
                    //System.out.println("trying: " + tempToken.getToken());
                    if (processChunk(tempToken, keywords, visited) == true) {
                        found = true; 
                        numKeywords++;
                        //System.out.println("using: " + tempToken.getToken());
                        break;
                    }
                }
                /*
                if (found) {
                    continue;
                }
              	*/
                for (int i = 1; i <= parts.length - 2; i++) {
                    StringBuilder sb = new StringBuilder();
                    int count = 0;
                    for (int j = i; j < parts.length; j++) {
                        if (isEligibleTerm(parts[j])) {
                            sb.append(parts[j].token).append(' ');
                            count++;
                        }
                    }
                    Tokens tempToken = new Tokens(tok);
                    tempToken.setToken(sb.toString().trim());
                    //System.out.println("trying: " + tempToken.getToken());
                    if (processChunk(tempToken, keywords, visited) == true) {
                    	//System.out.println("using: " + tempToken.getToken());
                    	numKeywords++;
                        found = true;
                        break;
                    }
                }
                if (found) {
                    continue;
                }  
            }
            
            for (POS p : parts) { 
            	if (isEligibleTerm(p)) {
                    Tokens tempToken = new Tokens(tok);
                    tempToken.setToken(p.token);
                    if (processChunk(tempToken, keywords, visited) == true) 
                    { 	
                    	//System.out.println("here using: " + tempToken.getToken());
                    	numKeywords++;
                        continue;
                    }
                }            	
            }
            
            // remove smaller keywords from the same phrase and facet
            if (numKeywords > 1) {
	    		for (int i = keywords.size()-numKeywords; i < keywords.size(); i++) {
	    			for (int j = i + 1; j < keywords.size(); j++) {
	    				Keyword temp_i = keywords.get(i);
	    				Keyword temp_j = keywords.get(j);
	    				if (temp_i.getFacet()[0].equals(temp_j.getFacet()[0])) {
	    					if (temp_i.getTerm().length() >= temp_j.getTerm().length()) {	    					
	    					//	System.out.println("removed " + temp_j.getTerm());
	    						keywords.remove(j);	    						
	    						j--;
	    						numKeywords--;
	    					}
	    					else {
	    					//	System.out.println("removed " + temp_i.getTerm());
	    						keywords.remove(i);	    						
	    						i--;
	    						numKeywords--;
	    						break;
	    					}
	    				}
	    			}
	    		}
            }       	
        } 
        return keywords;
    }
	
	public static boolean isEligibleTerm(POS p) {
        return (p.pos.equals("NN") || p.pos.equals("NNP") ||
                p.pos.equals("NNPS") || p.pos.equals("NNS") || p.pos.equals("JJ"));
    }
	
	// takes a token from POS service and adds the corresponding keyword to a set
	private boolean processChunk(Tokens t, ArrayList<Keyword> keywords, HashSet<String> visited) throws Exception {		
		
		Vocab vocab = vocabTerm(t.getToken());
		if (vocab == null)
		{			
			return false;
		}		
		
		if (vocab.concepts.size() > 1) 
		{
			for (int i = 0; i < vocab.concepts.size(); i++)
			{
				if (nullIRIs.contains(vocab.concepts.get(i).uri))
				{
					vocab.concepts.remove(i);
					i--;
				}
			}	
		}
		if (vocab.concepts.size() == 0) 
			return false;
		
		Concept toUse = vocab.concepts.get(0);	// default assignment
		List<Concept> consideringToUse = new ArrayList<Concept>();
		
		String closestLabel = toUse.labels.get(0); // default assignment
		
		int minDistance = 100;
		//System.out.println(t.getToken().toString() + "    <-   started here");
		for (Concept conc : vocab.concepts)
		{
			for (String label : conc.labels)
			{
				// get levelshtein distance between the label and input phrase			
				int tempDist = Levenshtein.distance(label, t.getToken());
				//System.out.println(label + "   " + tempDist);
				if (tempDist < 2) // within 2 changes away, add it to a consideration list 
				{					
					if (df.getOWLClass(IRI.create(conc.uri)).getSuperClasses(manager.getOntologies()).isEmpty())
					{
						// not an OWLClass, can skip
						continue;
					}
					if (conc.uri.contains("obo/ENVO") || conc.uri.contains("cinergi_ontology/cinergi.owl")) 
					{
						consideringToUse.add(0, conc); // if its ENVO or cinergi at to beginning
					}
					else
						consideringToUse.add(conc);
				}
				
				if (tempDist < minDistance)
				{
					minDistance = tempDist;
					toUse = conc; // update the concept
					closestLabel = label; // update the label
				}
			}
		}
		
		if (consideringToUse.size() > 0)
			toUse = consideringToUse.get(0); // the first element of this list is the best concept
		
		//if input is all caps (abbreviation), check if the output is the exact same 
		if (t.getToken().equals(t.getToken().toUpperCase()))
			if (closestLabel.equals(t.getToken()) == false)
		{
			return false;
		}

		OWLClass cls = df.getOWLClass(IRI.create(toUse.uri));
				
		// check for repeated terms
		if (visited.contains(cls.getIRI().toString()))
		{
			//System.err.println(cls.getIRI().toString() + " has already been used");
			return false;
		}
		visited.add(cls.getIRI().toString());
		
		if (toUse.uri.contains("CHEBI"))
		{
			// filter chemical entities that cause errors and any input less than 2
			if (t.getToken().length() <= 3) 
			{
				return false;
			}
		}
		if (t.getToken().length() <= 2)
		{
			return false;
		}
		// determined the class, now obtain the facet it is associated with
		HashSet<IRI> visitedIRI = new HashSet<IRI>();
		List<IRI> facetIRI = getFacetIRI(cls, visitedIRI);		
		if (facetIRI == null)
		{	
			//System.err.println("no facet for: " + toUse.uri);
			return false;
		}
		ArrayList<String> facetLabels = new ArrayList<String>();
		ArrayList<String> IRIstr = new ArrayList<String>();
		
		for (IRI iri : facetIRI)
		{		
			//facetLabels.add(OWLFunctions.getLabel(df.getOWLClass(iri), manager, df));
			//System.out.println(t.getToken());
			facetLabels.add(facetPath(df.getOWLClass(iri)));
			IRIstr.add(iri.toString());		
		}
		keywords.add(new Keyword(/*trimToken(t.getToken())*/toCamelCase(closestLabel),
					new String[] { t.getStart(), t.getEnd() }, 
				IRIstr.toArray(new String[IRIstr.size()]), 
				facetLabels.toArray(new String[facetLabels.size()])	));
		
		return true; // 
		
	}

	private String toCamelCase(String label) {
		String returnLabel = "" + Character.toUpperCase(label.charAt(0));
		for (int i = 1; i < label.length(); i++)
		{
			char c = label.charAt(i);
			if (label.charAt(i-1) == ' ')
			{
				returnLabel += Character.toUpperCase(c);
			}
			else returnLabel += c;
		}
		return returnLabel;
	}

	private String trimToken(String token) {
		
		String temp = token.substring(1).replaceAll("([-+.^:,])", "");
		temp = Character.toUpperCase(token.charAt(0)) + temp;
		return temp;
		
	}

	public POS[] pos(Gson gson, String input) throws Exception
	{
		//String prefix = "http://tikki.neuinfo.org:9000/scigraph/lexical/pos?text=";
		String prefix = "http://ec-scigraph.sdsc.edu:9000/scigraph/lexical/pos?text=";
		String urlInput = URLEncoder.encode(input, StandardCharsets.UTF_8.name());
		String urlOut = readURL(prefix+urlInput);
		
		POS[] p = gson.fromJson(urlOut, POS[].class);
		return p;
	}
	
	
	
	
}
