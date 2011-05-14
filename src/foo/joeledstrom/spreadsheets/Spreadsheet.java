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



public class Spreadsheet {

    static final XmlNamespaceDictionary SPREADSHEET_FEED_NS = new XmlNamespaceDictionary()
        .set("", "http://www.w3.org/2005/Atom")
        .set("openSearch", "http://a9.com/-/spec/opensearch/1.1/")
        .set("gd", "http://schemas.google.com/g/2005");

    private String title;
    private String worksheetFeed;
    private final SpreadsheetsService service;


    public String getTitle() {
        return title;
    }

    public FeedResponse<Worksheet> getWorksheets() {
        return Worksheet.get(service, worksheetFeed);
    }

    Spreadsheet(SpreadsheetsService service, String title, String worksheetFeed) {
        this.title = title;
        this.worksheetFeed = worksheetFeed;
        this.service = service;
    }

    static FeedResponse<Spreadsheet> get(final SpreadsheetsService service, final String title) {
        return service.new FeedResponse<Spreadsheet>() {
            public void init() throws IOException, XmlPullParserException {
                WiseUrl url = new WiseUrl("https://spreadsheets.google.com/feeds/spreadsheets/private/full");

                if (title != null) {
                    url.title = title;
                    url.title_exact = true;
                }

                HttpResponse response = service.wiseRequestFactory.buildGetRequest(url).execute();

                feedParser = 
                    AtomFeedParser.create(response, SPREADSHEET_FEED_NS, SpreadsheetFeed.class, SpreadsheetEntry.class);
            }

            public Spreadsheet parseOne() throws IOException, XmlPullParserException {
                SpreadsheetEntry entry = (SpreadsheetEntry)feedParser.parseNextEntry();

                if (entry == null)
                    return null;

                return new Spreadsheet(service, entry.title, entry.content.src);
            }

        };
    }

    public static class SpreadsheetFeed {
        @Key("entry") public List<SpreadsheetEntry> entries;

    }
    public static class SpreadsheetEntry {
        @Key public String title;
        @Key public SpreadsheetContent content;
    }
    public static class SpreadsheetContent {
        @Key("@src") public String src;
    }

}
