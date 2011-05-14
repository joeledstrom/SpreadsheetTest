package foo.joeledstrom.spreadsheets;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xmlpull.v1.XmlPullParserException;

import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.xml.atom.AtomFeedParser;
import com.google.api.client.util.Key;
import com.google.api.client.xml.GenericXml;
import com.google.api.client.xml.XmlNamespaceDictionary;

import foo.joeledstrom.spreadsheets.SpreadsheetsService.FeedResponse;
import foo.joeledstrom.spreadsheets.SpreadsheetsService.WiseUrl;

public class WorksheetRow {

    static final XmlNamespaceDictionary LIST_FEED_NS = new XmlNamespaceDictionary()
    .set("", "http://www.w3.org/2005/Atom")
    .set("openSearch", "http://a9.com/-/spec/opensearch/1.1/")
    .set("gs", "http://schemas.google.com/spreadsheets/2006")
    .set("gd", "http://schemas.google.com/g/2005")
    .set("app", "http://www.w3.org/2007/app");


    private final SpreadsheetsService service;
    private Map<String, String> cells;
    private String id;

    public Map<String, String> getCells() {
        return cells;
    }



    WorksheetRow(SpreadsheetsService service, String id, Map<String, String> cells) {
        this.service = service;
        this.id = id;
        this.cells = cells;
    }


    static FeedResponse<WorksheetRow> get(final SpreadsheetsService service, final String listFeed, 
            final String sq, final String orderby, final boolean reverse) {

        return service.new FeedResponse<WorksheetRow>() {


            public void init() throws IOException, XmlPullParserException {
                WiseUrl url = new WiseUrl(listFeed);

                url.sq = sq;
                url.orderby = orderby;
                url.reverse = reverse;

                HttpResponse response = service.wiseRequestFactory.buildGetRequest(url).execute();

                feedParser =
                    AtomFeedParser.create(response, LIST_FEED_NS, ListFeed.class, ListEntry.class);

            }
            @SuppressWarnings("rawtypes")
            public WorksheetRow parseOne() throws IOException, XmlPullParserException {
                ListEntry entry = (ListEntry)feedParser.parseNextEntry();

                if (entry == null)
                    return null;

                Map<String, String> cells = new HashMap<String,String>();

                for (Map.Entry<String, Object> e : entry.entrySet()) {
                    String key = e.getKey();

                    if (key.startsWith("gsx:")) {
                        String value;
                        try {
                            value = ((Map)((List)e.getValue()).get(0)).get("text()").toString();
                        } catch (Exception ex) {
                            throw new XmlPullParserException("XML gsx elements changed format");
                        }
                        cells.put(key.substring(4), value);
                    }
                }

                return new WorksheetRow(service, entry.id, cells);
            }
        };
    }

    public static class ListFeed {
        @Key("entry") public List<String> entries;
    }

    public static class ListEntry extends GenericXml {
        @Key public String id;
    }

}
