import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.util.OWLOntologyWalker;
import org.semanticweb.owlapi.util.OWLOntologyWalkerVisitor;

public class OWLFunctions {
	
	private static Map<OWLClass, String> labelMap = new HashMap<OWLClass, String>();
	private static Map<OWLClass, Boolean> facetMap = new HashMap<OWLClass, Boolean>();
	
	// returns true if the OWLClass is a cinergifacet
	public static boolean hasCinergiFacet(OWLClass c, OWLOntology o, OWLDataFactory df)
	{
	/*	if (facetMap.containsKey(c)) // not sure if this will improve time
		{
			return facetMap.get(c);
		}
	*/	for (OWLAnnotation a : c.getAnnotations(o,df.getOWLAnnotationProperty
				(IRI.create("http://hydro10.sdsc.edu/cinergi_ontology/cinergiExtensions.owl#cinergiFacet"))))
		{
			if (a.getValue().equals(df.getOWLLiteral(true)))
			{
				//facetMap.put(c, true);
				return true;
			}
		}
		return false;
	}
	
	public static boolean hasParentAnnotation(OWLClass c, OWLOntology extensionsOntology, OWLDataFactory df) {
		
		for (OWLAnnotation a : c.getAnnotations(extensionsOntology))
		{
			if (a.getProperty().equals(df.getOWLAnnotationProperty
					(IRI.create("http://hydro10.sdsc.edu/cinergi_ontology/cinergiExtensions.owl#cinergiParent"))))
			{	
				return true;
			}
		}
		return false;
	}
	
	public static List<OWLClass> getParentAnnotationClass(OWLClass c, OWLOntology extensionsOntology, OWLDataFactory df) {
		
		List<OWLClass> cinergiParents = new ArrayList<OWLClass>();
		
		for (OWLAnnotation a : c.getAnnotations(extensionsOntology))
		{
			if (a.getProperty().equals(df.getOWLAnnotationProperty
					(IRI.create("http://hydro10.sdsc.edu/cinergi_ontology/cinergiExtensions.owl#cinergiParent"))))
			{
				cinergiParents.add(df.getOWLClass((IRI)(a.getValue())));
			}
		}
		return cinergiParents;
	}
	
	public static String getLabel(OWLClass c, OWLOntologyManager m, OWLDataFactory df)
	{
		if (labelMap.containsKey(c))
		{
			return labelMap.get(c);
		}
		String label = "";
		for (OWLOntology o : m.getOntologies())
		{
			for (OWLAnnotation a : c.getAnnotations(o, df.getRDFSLabel()))
			{				
				if (((OWLLiteral)a.getValue()).getLang().toString().equals("en"))
				{					
					label = ((OWLLiteral)a.getValue()).getLiteral();
					break;  
				}
				label = ((OWLLiteral)a.getValue()).getLiteral();
			}
		}
		for (OWLOntology o : m.getOntologies())			
		{
			for (OWLAnnotation a : c.getAnnotations(o, df.getOWLAnnotationProperty
						(IRI.create("http://hydro10.sdsc.edu/cinergi_ontology/cinergiExtensions.owl#cinergiPreferredLabel"))))
			{
				label = ((OWLLiteral)a.getValue()).getLiteral();
			}
		}
		labelMap.put(c, label);
		return label;		
	}
	
	public static boolean isCinergiFacet(OWLAnnotation a)
	{
		return (a.getProperty().getIRI().getShortForm().toString().equals("cinergiFacet"));
	}
	
	public static boolean cinergiFacetTrue(OWLAnnotation a, OWLDataFactory df)
	{		
		return a.getValue().equals(df.getOWLLiteral(true));
	}
	
	public static boolean isTopLevelFacet(OWLClass c, OWLOntology extensionsOntology, OWLDataFactory df)
	{
		if (hasParentAnnotation( c, extensionsOntology, df))
		{
			if (getParentAnnotationClass(c, extensionsOntology, df).get(0).
					getIRI().equals(IRI.create("http://www.w3.org/2002/07/owl#Thing")))
			{
				return true;
			}
		}
		return false;
	}
	
public static List<String> getFacets(final OWLOntologyManager manager, final OWLDataFactory df) {
		
		final Set<IRI> iri = new HashSet<IRI>();
		
		final List<String> facets = new ArrayList<String>();
		
		OWLOntologyWalker walker = new OWLOntologyWalker(manager.getOntologies());
        OWLOntologyWalkerVisitor<Object> visitor = 
        		
    		new OWLOntologyWalkerVisitor<Object>(walker)    
        	{
	        	@Override
	        	public Object visit(OWLClass c)
	        	{
	        		if (!iri.contains(c.getIRI()))
    				{	
	        			iri.add(c.getIRI());
	        			for (OWLOntology o : manager.getOntologies())
	        			{	
	        				if (hasCinergiFacet(c, o, df))
	        				{
	        					if (hasParentAnnotation(c, o, df))
			        			{
	        						List<OWLClass> parentClasses = getParentAnnotationClass(c, o, df);
	        						for (OWLClass parent : parentClasses )
	        						{
	        							if (isTopLevelFacet(parent, o, df))
    									{
	        								facets.add(getLabel(c, manager, df) + ", " + getLabel(parent, manager, df));
    									}
	        						}
			        			}
	        				}
	        			}
    				}
	        		return null;
	        	}
        	};
		walker.walkStructure(visitor);
		
		return facets;
	}
	
}
