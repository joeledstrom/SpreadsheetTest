package foo.joeledstrom.spreadsheets;

import java.io.IOException;
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.xml.atom.AtomFeedParser;
import com.google.api.client.util.Key;
import com.google.api.client.xml.XmlNamespaceDictionary;

import foo.joeledstrom.spreadsheets.SpreadsheetsService.FeedResponse;
import foo.joeledstrom.spreadsheets.SpreadsheetsService.WiseUrl;


public class Worksheet {
    static final XmlNamespaceDictionary WORKSHEET_FEED_NS = new XmlNamespaceDictionary()
        .set("", "http://www.w3.org/2005/Atom")
        .set("openSearch", "http://a9.com/-/spec/opensearch/1.1/")
        .set("gs", "http://schemas.google.com/spreadsheets/2006")
        .set("gd", "http://schemas.google.com/g/2005")
        .set("app", "http://www.w3.org/2007/app");
    

    private String title;
    private String listFeed;
    private final SpreadsheetsService service;

    Worksheet(SpreadsheetsService service, String title, String listFeed) {
        this.title = title;
        this.listFeed = listFeed;
        this.service = service;
    }
    
    
    public String getTitle() {
        return title;
    }
    
    
    public FeedResponse<WorksheetRow> getRows() {
        return getRows(null, null, false);
    }
    public FeedResponse<WorksheetRow> getRows(final String sq, final String orderby, final boolean reverse) {
        return WorksheetRow.get(service, listFeed, sq, orderby, reverse);
    }
    
    
    
    static FeedResponse<Worksheet> get(final SpreadsheetsService service, final String worksheetFeed) {
        return service.new FeedResponse<Worksheet>() {
            public void init() throws IOException, XmlPullParserException {
                WiseUrl url = new WiseUrl(worksheetFeed);
                
                    
                HttpResponse response = service.wiseRequestFactory.buildGetRequest(url).execute();
                
                
                feedParser = 
                    AtomFeedParser.create(response, WORKSHEET_FEED_NS, WorksheetFeed.class, WorksheetEntry.class);
                
            }
            public Worksheet parseOne() throws IOException, XmlPullParserException {
                WorksheetEntry entry = (WorksheetEntry)feedParser.parseNextEntry();
                
                if (entry == null)
                    return null;
                
                return new Worksheet(service, entry.title, entry.content.src);
            }
            
        };
    }
    
    
    
    
    public static class WorksheetFeed {
        @Key("entry") public List<WorksheetEntry> entries;
    }
    
    public static class WorksheetEntry {
        @Key public String title;
        @Key public WorksheetContent content;
    }
    public static class WorksheetContent {
        @Key("@src") public String src;
    }
    
    
    
    
}
