
public class Keyword
{

    private String term;

    private String[] span;
    
    private String[] ontID;

    private String[] facets;
    
    public String[] getFacet ()
    {
        return facets;
    }

    public void setFacet (String[] facets)
    {
        this.facets = facets;
    }

    public String[] getOntID ()
    {
        return ontID;
    }

    public void setOntID (String[] ontID)
    {
        this.ontID = ontID;
    }

    public String getTerm ()
    {
        return term;
    }

    public void setTerm (String term)
    {
        this.term = term;
    }

    public String[] getSpan ()
    {
        return span;
    }

    public void setSpan (String[] span)
    {
        this.span = span;
    }

    public Keyword(String term, String[] span, String[] ontID, String[] facets)
    {
    	setFacet(facets);
    	setOntID(ontID);
    	setSpan(span);
    	setTerm(term);
    }
    
    @Override
    public String toString()
    {
        return "Keyword [facet = "+facets+", ontID = "+ontID+", term = "+term+", span = "+span+"]";
    }
}