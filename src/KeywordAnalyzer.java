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
import org.semanticweb.owlapi.model.OWLOntology;
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
	
	public KeywordAnalyzer(OWLOntologyManager manager, OWLDataFactory df, OWLOntology ont, 
			OWLOntology extensions, Gson gson, List<String> stoplist, 
			LinkedHashMap<String,IRI> exceptionMap, List<String> nullIRIs) {
		output = new ArrayList<Output>();
		this.manager = manager;
		this.df = df;
		this.cinergi = cinergi;
		this.extensions = extensions;
		this.gson = gson;
		this.stoplist = stoplist;
		this.exceptionMap = exceptionMap;
		this.nullIRIs = nullIRIs;
		client = new DefaultHttpClient();
		counter = 0;
	}

	public List<Output> getOutput()	{
		return output;
	}
	
	public void processDocument(Document doc) throws UnsupportedEncodingException {

		String text = doc.getTitle() + " " + doc.getText();		
		
		//System.out.println("processing: " + doc.getTitle());
		long before = System.currentTimeMillis();
		ArrayList<Keyword> keywords = new ArrayList<Keyword>();
		HashSet<String> visited = new HashSet<String>();
		try {
			keywords = process(text, visited);
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
		String prefix = "http://tikki.neuinfo.org:9000/scigraph/vocabulary/term/";
		String suffix = "?limit=10&searchSynonyms=true&searchAbbreviations=false&searchAcronyms=false";
		String urlInput = URLEncoder.encode(input, StandardCharsets.UTF_8.name()).replace("+", "%20");
	
		if (input == null)
			return null;
		
		String urlOut = null;
		if (stoplist.contains(input.toLowerCase()))
		{
			return null;
		}
		try {			
			urlOut = readURL(prefix+urlInput+suffix);	
			if (urlOut == null)
				return null;
		} catch (Exception e) {
			return null;
		}
		Vocab vocab = gson.fromJson(urlOut, Vocab.class);
		
		// preliminary check
		if (stoplist.contains(vocab.concepts.get(0).labels.get(0).toLowerCase()))
			return null; 
		return vocab;
	}
	
	// returns the cinegiFacet associated with any class, returns null if there is not one
	public List<IRI> getFacetIRI(OWLClass cls, HashSet<IRI> visited)
	{		
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
			*/	parentIRIs.addAll(getFacetIRI(c, visited));   
			}	
			return parentIRIs;
		}			
		if (!cls.getEquivalentClasses(manager.getOntologies()).isEmpty())
		{ 			
			for (OWLClassExpression oce : cls.getEquivalentClasses(manager.getOntologies())) // equivalences
			{
				if (oce.getClassExpressionType().toString().equals("Class"))
				{
					List<IRI> retVal = getFacetIRI(oce.getClassesInSignature().iterator().next(), visited); 
					
					if (retVal == null)
						continue;
					
					return retVal;	
				}	 
			}
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
	private String facetPath(OWLClass cls)
	{
		if (OWLFunctions.getParentAnnotationClass(cls, extensions, df).size() == 0)
		{
			//System.err.println(OWLFunctions.getLabel(cls, manager, df) + " has no cinergiParent, terminating.");
			return null;
			//return null;
		}
		OWLClass cinergiParent = OWLFunctions.getParentAnnotationClass(cls, extensions, df).get(0);
		if (OWLFunctions.isTopLevelFacet(cinergiParent, extensions, df) || 
				cinergiParent.getIRI().equals(IRI.create("http://www.w3.org/2002/07/owl#Thing")))
		{
			return (OWLFunctions.getLabel(cls, manager, df) + ", " + OWLFunctions.getLabel(cinergiParent, manager, df));
		}
		else
		{
			return (OWLFunctions.getLabel(cls,  manager, df) + ", " + facetPath(cinergiParent));
		} 
	}

	private ArrayList<Keyword> process(String testInput, HashSet<String> visited) throws Exception
	{
		String url = URLEncoder.encode(testInput, StandardCharsets.UTF_8.name());
		String chunks = "http://tikki.neuinfo.org:9000/scigraph/lexical/chunks?text=";
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
		String closestLabel = toUse.labels.get(0); // default assignment
		
		int minDistance = 100;
		for (Concept conc : vocab.concepts)
		{
			for (String label : conc.labels)
			{
				// get levelshtein distance between the label and input phrase
				int tempDist = Levenshtein.distance(label, t.getToken());
				if (tempDist < minDistance)
				{
					toUse = conc; // update the concept
					closestLabel = label; // update the label
				}
			}
		}
		
		//toUse = vocab.concepts.get(0); // TODO find the concept that matched the token
	/*	if (vocab.concepts.size() > 1) 
		{ // change this later to make use of exceptionMap TODO
			for (int i = 0; i < vocab.concepts.size(); i++)
			{
				if (nullIRIs.contains(vocab.concepts.get(i).uri))
				{
					vocab.concepts.remove(i);
					i--;
				}
			}
			if (vocab.concepts.size() == 0) 
				return false;
			toUse = vocab.concepts.get(0);		
		}
 	*/	if (!t.getToken().equals(t.getToken().toUpperCase()) 
				&& closestLabel.equals(closestLabel.toUpperCase()))
		{
			// check if the input token is all caps and if the response term is also all caps
			return false;
		}
		OWLClass cls = df.getOWLClass(IRI.create(toUse.uri));	
		// check for repeated terms
		if (visited.contains(cls.getIRI().toString()))
		{
			return false;
		}
		visited.add(cls.getIRI().toString());

		// filter chemical entities that cause errors and any input less than 2
		if (toUse.uri.contains("CHEBI") && t.getToken().length() <= 3) 
		{
			return false;
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
		String prefix = "http://tikki.neuinfo.org:9000/scigraph/lexical/pos?text=";
		String urlInput = URLEncoder.encode(input, StandardCharsets.UTF_8.name());
		String urlOut = readURL(prefix+urlInput);
		
		POS[] p = gson.fromJson(urlOut, POS[].class);
		return p;
	}
	
	
	
	
}
