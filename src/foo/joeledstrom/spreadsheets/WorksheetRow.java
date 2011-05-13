package foo.joeledstrom.spreadsheets;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.xml.atom.AtomFeedParser;
import com.google.api.client.util.Key;
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
	
	
	public final String foo;
	public final String id;

	
	private final SpreadsheetsService service;

	public WorksheetRow(SpreadsheetsService service, String id, String foo) {
		this.service = service;
		this.id = id;
		this.foo = foo;
		
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
			public WorksheetRow parseOne() throws IOException, XmlPullParserException {
				ListEntry entry = (ListEntry)feedParser.parseNextEntry();
				
				if (entry == null)
					return null;

				// This is what I want to get working:
				//List<ListCell> cells = entry.cells;
				//ListCell listCell = cells.get(0);
				//return new WorksheetRow(service, entry.id, listCell.value);
				
				// Do this instead, so that the app runs
				return new WorksheetRow(service, entry.id, "");
			}
		};
	}
	
	public static class ListFeed {
		@Key("entry") public List<String> entries;
	}
	
	public static class ListEntry {
		// this doesn't work:
		@Key("*[namespace-uri()='http://schemas.google.com/spreadsheets/2006/extended']") public List<ListCell> cells;
		
		// this works:
		@Key public String id;
	}
	
	public static class ListCell {
		@Key("text()") public String value;
	}
}
