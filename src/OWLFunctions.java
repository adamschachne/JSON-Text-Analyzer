import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

public class OWLFunctions {
	
	// returns true if the OWLClass is a cinergifacet
	public static boolean hasCinergiFacet(OWLClass c, OWLOntology o, OWLDataFactory df)
	{
		for (OWLAnnotation a : c.getAnnotations(o))
		{
			if (isCinergiFacet(a))
			{
				if (cinergiFacetTrue(a, df))
				{
					return true;
				}
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
	
	public static OWLClass getParentAnnotationClass(OWLClass c, OWLOntology extensionsOntology, OWLDataFactory df) {
		
		for (OWLAnnotation a : c.getAnnotations(extensionsOntology))
		{
			if (a.getProperty().equals(df.getOWLAnnotationProperty
					(IRI.create("http://hydro10.sdsc.edu/cinergi_ontology/cinergiExtensions.owl#cinergiParent"))))
			{
				return (df.getOWLClass((IRI)(a.getValue())));
			}
		}
		return null;
	}
	
	public static String getLabel(OWLClass c, OWLOntologyManager m, OWLDataFactory df)
	{
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
	
	
	
	
	
}
