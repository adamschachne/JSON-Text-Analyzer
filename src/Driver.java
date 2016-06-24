import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Driver {

	// argument 0 is input json, argument 1 is output json, argument 2 is stoplist
	public static void main(String[] argv) throws OWLOntologyCreationException, IOException
	{
			
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = manager.getOWLDataFactory();
		manager.setSilentMissingImportsHandling(true);
		
		System.out.println("loading ontology");
		OWLOntology cinergi_ont = manager.loadOntologyFromOntologyDocument(IRI.create("http://hydro10.sdsc.edu/cinergi_ontology/cinergi.owl"));
		System.out.println("ontology loaded");
		OWLOntology extensions = null;
		for (OWLOntology o : manager.getOntologies())
		{
			if (o.getOntologyID().getOntologyIRI().toString().equals("http://hydro10.sdsc.edu/cinergi_ontology/cinergiExtensions.owl"))
			{
				extensions = o;
			}			
		}
		if (extensions == null)
		{
			System.err.println("failed to gather extensions");
			System.exit(1);
		}
		
		// load documents
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		BufferedReader bufferedReader = new BufferedReader(new FileReader(argv[0]));
		
		Document[] docs = gson.fromJson(bufferedReader, Document[].class);
		List<String> stoplist = Files.readAllLines(Paths.get(argv[2]), StandardCharsets.UTF_8);
		List<String> nullIRIs = Files.readAllLines(Paths.get(argv[3]), StandardCharsets.UTF_8);
		LinkedHashMap<String, IRI> exceptionMap = null; // Create this using label duplicates spreadsheet
		
		KeywordAnalyzer analyzer = new KeywordAnalyzer(manager, df, cinergi_ont, extensions, gson,
					stoplist, exceptionMap, nullIRIs);
		

		//OWLFunctions.getFacets(manager, df)
		
	//	long time = System.currentTimeMillis();
	//	System.err.println("processing documents...");
	
		analyzer.processDocuments(docs);
	
	//	System.out.println("total time: " + (System.currentTimeMillis()-time));
		
		FileWriter fw = new FileWriter(argv[1]);
		fw.write(gson.toJson(analyzer.getOutput())); 
	
		fw.close();
	}
}
