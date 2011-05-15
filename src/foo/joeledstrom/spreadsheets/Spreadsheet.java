package foo.joeledstrom.spreadsheets;

import java.io.IOException;
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.xml.atom.AtomFeedParser;
import com.google.api.client.util.Key;
import com.google.api.client.xml.XmlNamespaceDictionary;

import foo.joeledstrom.spreadsheets.SpreadsheetsService.FeedIterator;
import foo.joeledstrom.spreadsheets.SpreadsheetsService.SpreadsheetsException;
import foo.joeledstrom.spreadsheets.SpreadsheetsService.WiseUrl;




public class Spreadsheet {

    

    private String title;
    private String worksheetFeed;
    private final SpreadsheetsService service;


    public String getTitle() {
        return title;
    }

    
    Spreadsheet(SpreadsheetsService service, String title, String worksheetFeed) {
        this.title = title;
        this.worksheetFeed = worksheetFeed;
        this.service = service;
    }

    static final XmlNamespaceDictionary WORKSHEET_FEED_NS = new XmlNamespaceDictionary()
    .set("", "http://www.w3.org/2005/Atom")
    .set("openSearch", "http://a9.com/-/spec/opensearch/1.1/")
    .set("gs", "http://schemas.google.com/spreadsheets/2006")
    .set("gd", "http://schemas.google.com/g/2005")
    .set("app", "http://www.w3.org/2007/app");
    
    public FeedIterator<Worksheet> getWorksheets() throws IOException, SpreadsheetsException {
        return service.new FeedIterator<Worksheet>() {
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
