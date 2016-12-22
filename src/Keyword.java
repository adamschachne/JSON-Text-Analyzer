import java.util.Arrays;

public class Keyword {
    private String term;
    private String[] span;
    private String[] ontID;
    private String[] facet;
    private String[] fullHierarchy;

    public Keyword(String term, String[] span, String[] ontID, String[] facet, String[] fullHierarchy) {
        this.facet = facet;
        this.span = span;
        this.term = term;
        this.ontID = ontID;
        this.fullHierarchy = fullHierarchy;
    }


    public String[] getFacet() {
        return facet;
    }

    public void setFacet(String[] facet) {
        this.facet = facet;
    }

    public String[] getOntID() {
        return ontID;
    }

    public void setOntID(String[] ontID) {
        this.ontID = ontID;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public String[] getSpan() {
        return span;
    }

    public void setSpan(String[] span) {
        this.span = span;
    }

    public String[] getFullHierarchy() {
        return fullHierarchy;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Keyword{");
        sb.append("term='").append(term).append('\'');
        sb.append(", span=").append(Arrays.toString(span));
        sb.append(", ontID=").append(Arrays.toString(ontID));
        sb.append(", facet=").append(Arrays.toString(facet));
        sb.append(", fullHierarchy=").append(Arrays.toString(fullHierarchy));
        sb.append('}');
        return sb.toString();
    }
}