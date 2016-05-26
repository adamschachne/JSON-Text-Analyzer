import com.google.gson.annotations.SerializedName;

public class Document
{
    @SerializedName("abstract") 
    private String text;

    private String id;

    private String title;

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

    @Override
    public String toString()
    {
        return "Document [abstract = "+text+", id = "+id+", title = "+title+"]";
    }
}
			
			