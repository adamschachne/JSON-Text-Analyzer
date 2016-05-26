import com.google.gson.annotations.SerializedName;

public class Output {
	
	@SerializedName("abstract") 
	private String text;

	private String id;
	
	private String title;
	
	private Keyword[] keywords;
	
	public String getText ()
	{
	    return text;
	}
	
	public void setText (String text)
	{
	    this.text = text;
	}
	
	public String getId ()
	{
	    return id;
	}
	
	public void setId (String id)
	{
	    this.id = id;
	}
	
	public String getTitle ()
	{
	    return title;
	}
	
	public void setTitle (String title)
	{
	    this.title = title;
	}
	
	public Keyword[] getKeyword ()
	{
	    return keywords;
	}
	
	public void setKeyword (Keyword[] keywords)
	{
	    this.keywords = keywords;
	}
	
	@Override
	public String toString()
	{
	    return "Output [abstract = "+text+", id = "+id+", title = "+title+", keyword = "+keywords+"]";
	}
}
