import org.coode.owlapi.manchesterowlsyntax.ManchesterOWLSyntaxTokenizer.Token;

public class Tokens {
	private String token;
	private String start;
	private String end;
		
	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getStart() {
		return start;
	}

	public void setStart(String start) {
		this.start = start;
	}
	
	public String getEnd() {
		return end;
	}

	public void setEnd(String end) {
		this.end = end;
	}
	
	public Tokens(String str)
	{
		token = str;
		start = "";
		end = "";
	}
	
	public Tokens(Tokens other)
	{
		token = other.getToken();
		start = other.getStart();
		end = other.getEnd();				
	}
}
