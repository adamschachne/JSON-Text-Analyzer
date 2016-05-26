import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

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
		counter = 0;
	}

	public List<Output> getOutput()	{
		return output;
	}
	
	public void processDocument(Document doc) throws UnsupportedEncodingException {

		String text = doc.getTitle() + " " + doc.getText();		
		
		System.out.println("processing: " + doc.getTitle());
		
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
	
	public String readUrl(String urlString) throws Exception {
		counter++;
	    BufferedReader reader = null;
	    try {
	    	if (counter % 25000 == 0)
	    	{
	    		counter = 0;
	    		System.out.println("waiting for TIME_WAIT");
	    		Thread.sleep(25000);
	    	}
	        URL url = new URL(urlString);
	        reader = new BufferedReader(new InputStreamReader(url.openStream()));
	        StringBuffer buffer = new StringBuffer();
	        int read;
	        char[] chars = new char[1024];
	        while ((read = reader.read(chars)) != -1)
	            buffer.append(chars, 0, read); 
	        //System.out.println(buffer.toString());	      	     
	        
	        return buffer.toString();
	    } finally {
	        if (reader != null)
	            reader.close();
	    }
	}
	
	public Vocab vocabTerm(String input) throws UnsupportedEncodingException
	{
		String prefix = "http://tikki.neuinfo.org:9000/scigraph/vocabulary/term/";
		String suffix = "?limit=10&searchSynonyms=true&searchAbbreviations=false&searchAcronyms=false";
		String urlInput = URLEncoder.encode(input, StandardCharsets.UTF_8.name()).replace("+", "%20");
	
		String urlOut = null;
		if (stoplist.contains(input.toLowerCase()))
		{
			return null;
		}
		//System.out.println(prefix+urlInput+suffix);
		try {			
			urlOut = readUrl(prefix+urlInput+suffix);	
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
	private IRI getFacetIRI(OWLClass cls, HashSet<IRI> visited)
	{
		if (visited.contains(cls.getIRI()))
		{
			return null;
		}
		visited.add(cls.getIRI());		
		
		//System.err.println(cls.getIRI());
		if (cls.getIRI().equals("http://www.w3.org/2002/07/owl#Thing"))
			return null;
	
		if (OWLFunctions.hasCinergiFacet(cls, extensions, df))
		{
			return cls.getIRI();
		}		
		if (OWLFunctions.hasParentAnnotation(cls, extensions ,df))
		{		        		
			return getFacetIRI(OWLFunctions.getParentAnnotationClass(cls, extensions, df), visited);							
		}			
		if (!cls.getEquivalentClasses(manager.getOntologies()).isEmpty())
		{ 			

			for (OWLClassExpression oce : cls.getEquivalentClasses(manager.getOntologies())) // equivalencies
			{
				if (oce.getClassExpressionType().toString().equals("Class"))
				{
					IRI retVal = getFacetIRI(oce.getClassesInSignature().iterator().next(), visited); 
					
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
					IRI retVal = getFacetIRI(oce.getClassesInSignature().iterator().next(), visited); 
					
					if (retVal == null)
						continue;
					
					return retVal;	
				}
			}
		}
		return null;
	}

	private ArrayList<Keyword> process(String testInput, HashSet<String> visited) throws Exception
	{
		String url = URLEncoder.encode(testInput, StandardCharsets.UTF_8.name());
		String chunks = "http://tikki.neuinfo.org:9000/scigraph/lexical/chunks?text=";
		//System.out.println(chunks+url);
		String json = readUrl(chunks + url);
		
	    ArrayList<Keyword> keywords = new ArrayList<Keyword>();
	    
	    // each result from chunking
	    Tokens[] tokens = gson.fromJson(json, Tokens[].class);
	
	    for (Tokens tok : tokens) // each chunk t
	    {
	    	boolean used = false;
	    	if (processChunk(tok, keywords, visited) == true)
	    	{
	    		used = true;
	    		continue;
	    	}
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
		    				used = true;
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
		    				used = true;
		    				continue;
		    			}
		    	    	// if the result of vocabulary/term is all caps, ignore it unless the pos search was all caps as well
		    	    	
		/*    	    	for (String label : vocab.concepts.get(0).labels) // comparing it to label of concept
		    	    	{				    	    	 
			    	    	if (label.equals(p.token))
			    	    	{
			    	    		writer.printf("%-60s\t%s\n", t.token, label);
			    	    		used = true;
			    	    		break;
			    	    	}
			    	    	else if (label.contains(p.token.toUpperCase()) || p.token.toUpperCase().contains(label))
			    	    	{	
			    	    		// do nothing since the token was referenced incorrectly
			    	    		used = true;
			    	    		break;
			    	    	}
		    	    	}
		    	    	if (!printed)
		    	    	{	
		    	    		if (!p.token.toUpperCase().equals(p.token) 
		    	    				&& !vocab.concepts.get(0).labels.get(0).toUpperCase().equals(vocab.concepts.get(0).labels.get(0))) // last check if token and label are not both caps
		    	    		{	
			    	    		writer.printf("%-60s\t%s\n", t.token, vocab.concepts.get(0).labels.get(0));
			    	    		printed =  true;
		    	    		}
		    	    	}
		    */	    	
	    			}    							
    			}
	    	}
	    }
		return keywords;	    			 
	}
	
	private boolean processChunk(Tokens t, ArrayList<Keyword> keywords, HashSet<String> visited) throws Exception {
		
		Vocab vocab = vocabTerm(t.getToken());
		if (vocab == null)
		{
			return false;
		}
		if (visited.contains(t.getToken()))
		{
			return false;
		}
		visited.add(t.getToken());
			
		Concept toUse = vocab.concepts.get(0);
		if (vocab.concepts.size() > 1) 
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
	
		HashSet<IRI> visitedIRI = new HashSet<IRI>();
		
		IRI facetIRI = getFacetIRI(df.getOWLClass(IRI.create(toUse.uri)), visitedIRI);
		if (facetIRI == null)
		{	
			//System.err.println("no facet for: " + toUse.uri);
			return false;
		}
		keywords.add(new Keyword(t.getToken(), new String[] { t.getStart(), t.getEnd() }, facetIRI.toString(), 
					OWLFunctions.getLabel(df.getOWLClass(facetIRI), manager, df)));
		
		return false;
		
	}

	public POS[] pos(Gson gson, String input) throws Exception
	{
		String prefix = "http://tikki.neuinfo.org:9000/scigraph/lexical/pos?text=";
		String urlInput = URLEncoder.encode(input, StandardCharsets.UTF_8.name());
		String urlOut = readUrl(prefix+urlInput);
		
		POS[] p = gson.fromJson(urlOut, POS[].class);
		return p;
	}
	
	
	
	
}
