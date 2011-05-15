package foo.joeledstrom.spreadsheets;

import java.io.IOException;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.xmlpull.v1.XmlPullParserException;

import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.xml.atom.AtomFeedParser;
import com.google.api.client.http.xml.atom.AtomParser;
import com.google.api.client.util.Key;
import com.google.api.client.xml.GenericXml;
import com.google.api.client.xml.XmlNamespaceDictionary;

import foo.joeledstrom.spreadsheets.SpreadsheetsService.FeedIterator;
import foo.joeledstrom.spreadsheets.SpreadsheetsService.SpreadsheetsException;
import foo.joeledstrom.spreadsheets.SpreadsheetsService.WiseUrl;


public class Worksheet {
    


    private String title;
    private String listFeed;
    private final SpreadsheetsService service;
    private AtomParser atomParser;

    Worksheet(SpreadsheetsService service, String title, String listFeed) {
        this.title = title;
        this.listFeed = listFeed;
        this.service = service;
    }


    public String getTitle() {
        return title;
    }

    
    public Set<String> getColumnNames() throws IOException, SpreadsheetsException {
        FeedIterator<WorksheetRow> rows = getRows();
        WorksheetRow firstEntry = rows.getNextEntry();
        rows.close(); 
        
        return firstEntry.getColumnNames();
    }
    
    public WorksheetRow addRow(Map <String, String> values) throws IOException, SpreadsheetsException {
        if (atomParser == null) {
            atomParser = new AtomParser();
            atomParser.namespaceDictionary = LIST_FEED_NS;
        }
        
        final StringBuilder builder = new StringBuilder()
            .append("<entry xmlns=\"http://www.w3.org/2005/Atom\" xmlns:gsx=\"http://schemas.google.com/spreadsheets/2006/extended\">");
        Formatter formatter = new Formatter(builder, Locale.US); 
        for (Map.Entry<String, String> value : values.entrySet()) 
            formatter.format("<gsx:%1$s>%2$s</gsx:%1$s>", value.getKey(), value.getValue());
        builder.append("</entry>");
        
        return service.new Request<WorksheetRow>() {
            public WorksheetRow run() throws IOException, XmlPullParserException {
                WiseUrl url = new WiseUrl(listFeed);
                HttpContent content = new ByteArrayContent(builder.toString());    
                HttpRequest request = service.wiseRequestFactory.buildPostRequest(url, content);
                request.enableGZipContent = false;
                
                GoogleHeaders headers = (GoogleHeaders)request.headers;
                headers.contentType = "application/atom+xml";
                headers.acceptEncoding = null;
                headers.contentEncoding = null;
                
                HttpResponse response = request.execute();
        
                
                ListEntry entry = atomParser.parse(response, ListEntry.class);
                
                return new WorksheetRow(service, entry.etag, entry.id, entry.getEditUrl(), entry.getValues());
            }
        }.execute();
    }

    static final XmlNamespaceDictionary LIST_FEED_NS = new XmlNamespaceDictionary()
    .set("", "http://www.w3.org/2005/Atom")
    .set("openSearch", "http://a9.com/-/spec/opensearch/1.1/")
    .set("gs", "http://schemas.google.com/spreadsheets/2006")
    .set("gd", "http://schemas.google.com/g/2005")
    .set("app", "http://www.w3.org/2007/app");
    
    public FeedIterator<WorksheetRow> getRows() throws IOException, SpreadsheetsException {
        return getRows(null, null, false);
    }
    public FeedIterator<WorksheetRow> getRows(final String sq, final String orderby, final boolean reverse) 
            throws IOException, SpreadsheetsException {
        return service.new FeedIterator<WorksheetRow>() {
            public void init() throws IOException, XmlPullParserException {
                WiseUrl url = new WiseUrl(listFeed);

                url.sq = sq;
                if (orderby != null)
                    url.orderby = "column:" + orderby;
                url.reverse = reverse;

                HttpResponse response = service.wiseRequestFactory.buildGetRequest(url).execute();

                feedParser =
                    AtomFeedParser.create(response, LIST_FEED_NS, ListFeed.class, ListEntry.class);

            }
            public WorksheetRow parseOne() throws IOException, XmlPullParserException {
                ListEntry entry = (ListEntry)feedParser.parseNextEntry();

                if (entry == null)
                    return null;

                return new WorksheetRow(service, entry.etag, entry.id, entry.getEditUrl(), entry.getValues());
            }
        };
    }
    
    

    public static class ListFeed {
        @Key("entry") public List<String> entries;
    }

    public static class ListEntry extends GenericXml {
        @Key public String id;
        @Key("@gd:etag") String etag;
        @Key("link") private List<ListLink> links;
        
        @SuppressWarnings("rawtypes")
        public Map<String, String> getValues() throws XmlPullParserException {
            Map<String, String> values = new HashMap<String,String>();

            for (Map.Entry<String, Object> e : entrySet()) {
                String key = e.getKey();

                if (key.startsWith("gsx:")) {
                    String value;
                    try {
                        Object textOfElement = ((Map)((List)e.getValue()).get(0)).get("text()");
                        
                        value = textOfElement == null ? "" : textOfElement.toString();

                    } catch (Exception ex) {
                        throw new XmlPullParserException("List feed entry structure changed (gsx)");
                    }
                    values.put(key.substring(4), value);
                }
            }
            
            return values;
        }
        
        public String getEditUrl() throws XmlPullParserException {
            String editUrl = null;
            for (ListLink link : links) {
                if (link.rel.equals("edit")) {
                    editUrl = link.href;
                }
            }
            if (editUrl == null) 
                throw new XmlPullParserException("List feed entry structure changed (edit url)");
            
            return editUrl;
        }
    }
    
    public static class ListLink {
        @Key("@rel") public String rel;
        @Key("@href") public String href;
    }

}
