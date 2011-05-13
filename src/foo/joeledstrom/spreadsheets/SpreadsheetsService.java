package foo.joeledstrom.spreadsheets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlpull.v1.XmlPullParserException;

import com.google.api.client.extensions.android2.AndroidHttp;
import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.xml.atom.AtomFeedParser;
import com.google.api.client.util.Key;

public class SpreadsheetsService {

	HttpRequestFactory wiseRequestFactory;
	HttpRequestFactory writelyRequestFactory;
	private final String applicationName;
	private final TokenSupplier tokenSupplier;
	
	private void createRequestFactories() {
		HttpTransport transport = AndroidHttp.newCompatibleTransport();
		
		wiseRequestFactory = transport.createRequestFactory(new HttpRequestInitializer() {
			String wiseToken;
			
			public void initialize(HttpRequest req) throws IOException {
				
				if (wiseToken == null)
					wiseToken = tokenSupplier.getToken("wise"); 
				
				GoogleHeaders headers = new GoogleHeaders();
				headers.gdataVersion = "3";
				headers.setApplicationName(applicationName);
				headers.setGoogleLogin(wiseToken);
				
				req.headers = headers;
				req.enableGZipContent = true;
			}
		});
		
		writelyRequestFactory = transport.createRequestFactory(new HttpRequestInitializer() {
			String writelyToken;

			public void initialize(HttpRequest req) throws IOException {
				if (writelyToken == null)
					writelyToken = tokenSupplier.getToken("writely");
				
				
				GoogleHeaders headers = new GoogleHeaders();
				headers.gdataVersion = "3";
				headers.setApplicationName(applicationName);
				headers.setGoogleLogin(writelyToken);
				
				req.headers = headers;
				req.enableGZipContent = true;
			}
		});
	}
	
	public interface TokenSupplier {	
		public String getToken(String authTokenType);
		public void invalidateToken(String token);
	}
	
	public SpreadsheetsService(String applicationName, TokenSupplier tokenSupplier) {
		
		this.applicationName = applicationName;
		this.tokenSupplier = tokenSupplier;
		
		createRequestFactories();
		
		// from Google IO 2011 talk:
		Logger.getLogger("com.google.api.client.http").setLevel(Level.ALL);
		// ALSO RUN FROM SHELL: adb shell setprop log.tag.HttpTransport DEBUG
			
	}
	
	public FeedResponse<Spreadsheet> getSpreadsheets() {
		return getSpreadsheets(null);
	}
	public FeedResponse<Spreadsheet> getSpreadsheets(String title) {
		return Spreadsheet.get(this, title);
	}
	
	public static class SpreadsheetsException extends Exception {
		private static final long serialVersionUID = 7081303654609337713L;

		SpreadsheetsException(String message) {
			super(message);
		}
	}
	
	
	public static class SpreadsheetsHttpException extends SpreadsheetsException {
		private static final long serialVersionUID = -7988015274337294180L;
		private String statusMessage;
		private int statusCode;
		
		SpreadsheetsHttpException(String message) {
			super(message);
		}
		
		SpreadsheetsHttpException(int statusCode, String statusMessage) {
			this(Integer.toString(statusCode) + " " + statusMessage);
			this.statusCode = statusCode;
			this.statusMessage = statusMessage;
		}
		
		public int getStatusCode() {
			return statusCode;
		}
		
		public String getStatusMessage() {
			return statusMessage;
		}
		
	}
	
	
	public abstract class FeedResponse<T> {
		
		private boolean initDone;
		private boolean closed;
		abstract void init() throws IOException, XmlPullParserException;
		abstract T parseOne() throws IOException, XmlPullParserException;
		
		@SuppressWarnings("rawtypes")
		AtomFeedParser feedParser;
		
		private final void runInit() throws IOException, XmlPullParserException {
			if (!initDone) {
				init();
				initDone = true;
				
				// hack to prevent NPE (bug in API?)
				if (feedParser != null)
					feedParser.parseFeed();
			}
		}
		
		public final T getNextEntry() throws IOException, SpreadsheetsException {
			try {
				runInit();
				T entry = parseOne();
				
				if (entry == null) {
					close();
				}
				
				return entry;
				
			} catch (HttpResponseException e) {
				if (e.getMessage() == "401 Token expired") {
					tokenSupplier.invalidateToken("wise");
					tokenSupplier.invalidateToken("writely");
					createRequestFactories();
					
					//TODO: protect against infinite loop (stack overflow)
					return getNextEntry(); 
				} else {
					throw new SpreadsheetsHttpException(e.response.statusCode, e.response.statusMessage);
				}
			} catch (XmlPullParserException e) {
				throw new SpreadsheetsException(e.getMessage());
			} finally {
				close();
			}
		}
		
		public final void close() {
			try {
					closed = true;
					feedParser.close();
			} catch (IOException e) 
				{} // really ignore this
			
		}
		
		public final List<T> getEntries() throws IOException, SpreadsheetsException {
			if (closed)
				throw new IllegalStateException("getEntries() cant be called twice on the same FeedResponse");
			
			List<T> list = new ArrayList<T>();
			
			while (true) {
				T entry = getNextEntry();
				if (entry == null)
					break;
				list.add(entry);
			}
			
			return list;
		}
	}
	static class WiseUrl extends GenericUrl {
		@Key String title;
		@Key("title-exact") Boolean title_exact;
		@Key String fields;
		@Key String sq;
		@Key String orderby;
		@Key Boolean reverse;

			WiseUrl(String url) {
				super(url);
		    }
	}

}
