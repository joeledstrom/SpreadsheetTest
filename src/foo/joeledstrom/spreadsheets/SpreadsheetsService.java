/*
 * Copyright (c) 2011 Joel Edström
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package foo.joeledstrom.spreadsheets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlpull.v1.XmlPullParserException;

import com.google.api.client.extensions.android2.AndroidHttp;
import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.xml.atom.AtomFeedParser;
import com.google.api.client.util.Key;
import com.google.api.client.xml.XmlNamespaceDictionary;


public class SpreadsheetsService {

    HttpRequestFactory wiseRequestFactory;
    HttpRequestFactory writelyRequestFactory;
    private final String applicationName;
    private String wiseToken;
    private String writelyToken;
    

    
    private void createRequestFactories() {
        HttpTransport transport = AndroidHttp.newCompatibleTransport();

        wiseRequestFactory = transport.createRequestFactory(new HttpRequestInitializer() {
            public void initialize(HttpRequest req) throws IOException {

                GoogleHeaders headers = new GoogleHeaders();
                headers.gdataVersion = "3";
                headers.setApplicationName(applicationName);
                headers.setGoogleLogin(wiseToken);

                req.headers = headers;
                req.enableGZipContent = true;
            }
        });
        
        writelyRequestFactory = transport.createRequestFactory(new HttpRequestInitializer() {
           public void initialize(HttpRequest req) throws IOException {
                
                GoogleHeaders headers = new GoogleHeaders();
                headers.gdataVersion = "3";
                headers.setApplicationName(applicationName);
                headers.setGoogleLogin(writelyToken);

                req.headers = headers;
                req.enableGZipContent = true;
            }
        });
    }

    
    public void setTokens(String writely, String wise) {
        writelyToken = writely;
        wiseToken = wise;
        
    }

    public SpreadsheetsService(String applicationName) {

        this.applicationName = applicationName;

        createRequestFactories();

        // from Google IO 2011 talk:
        // Note. enabling this causes OutOfMemoryError on large spreadsheets
        //Logger.getLogger("com.google.api.client.http").setLevel(Level.ALL);  
        // ALSO RUN FROM SHELL: adb shell setprop log.tag.HttpTransport DEBUG

    }

   

    public static class SpreadsheetsException extends Exception {
        private static final long serialVersionUID = 7081303654609337713L;

        SpreadsheetsException(String message) {
            super(message);
        }
    }
    
    public static class SpreadsheetsTokenExpiredException extends SpreadsheetsException {
        private static final long serialVersionUID = 6766168964540923877L;
        SpreadsheetsTokenExpiredException() {
            super("401 Token expired");
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
    
    

    // Converts exceptions
    abstract class Request<T> {
        
        abstract T run() throws IOException, XmlPullParserException;
        
        public final T execute() throws IOException, SpreadsheetsException {
            try {
                return run();
            } catch (HttpResponseException e) {
                if (e.getMessage().equals("401 Token expired")) {
                    throw new SpreadsheetsTokenExpiredException();
                } else { 
                    throw new SpreadsheetsHttpException(e.response.statusCode, e.response.statusMessage);
                }
            } catch (XmlPullParserException e) {
                throw new SpreadsheetsException(e.getMessage());
            } 
        }
       
    }
    
    @SuppressWarnings("rawtypes")
    public abstract class FeedIterator<T> {
        private boolean closed;
        
        protected AtomFeedParser feedParser;  // assign in init()
        protected String etag; // assign in init() 
        abstract void init() throws IOException, XmlPullParserException;
        abstract T parseOne() throws IOException, XmlPullParserException;

        FeedIterator() throws IOException, SpreadsheetsException {
            new Request<Void>() {
                public Void run() throws IOException, XmlPullParserException {
                    init();
                   
                    feedParser.parseFeed(); // hack to prevent NPE (bug in API?)
                    return null;
                }
            }.execute();
        }
     

        public T getNextEntry() throws IOException, SpreadsheetsException {
            return new Request<T>() {
                public T run() throws IOException, XmlPullParserException {
                    boolean success = false;
                    try {
                        T entry = parseOne();
                        
                        if (entry != null)
                            success = true;
                        
                        return entry;
                    } finally {
                        if (!success)
                            close();
                    }
                }
            }.execute();
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
                throw new IllegalStateException("getEntries() cant be called twice on the same FeedIterator");

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

   
    
    
    
    static final XmlNamespaceDictionary SPREADSHEET_FEED_NS = new XmlNamespaceDictionary()
    .set("", "http://www.w3.org/2005/Atom")
    .set("openSearch", "http://a9.com/-/spec/opensearch/1.1/")
    .set("gd", "http://schemas.google.com/g/2005");
    
    public FeedIterator<Spreadsheet> getSpreadsheets() throws IOException, SpreadsheetsException {
        return getSpreadsheets(null, null);
    }
    public FeedIterator<Spreadsheet> getSpreadsheets(String title) throws IOException, SpreadsheetsException {
        // note: even though this uses exact matching it may return multiple spreadsheets, titles are not unique on GoogleDocs.
        return getSpreadsheets(title, true);
    }
    public FeedIterator<Spreadsheet> getSpreadsheetsInexactMatch(String title) throws IOException, SpreadsheetsException {
        return getSpreadsheets(title, false);
    }
    
    public FeedIterator<Spreadsheet> getSpreadsheets(final String title, final Boolean exact) throws IOException, SpreadsheetsException {
        return new FeedIterator<Spreadsheet>() {
            public void init() throws IOException, XmlPullParserException {
                WiseUrl url = new WiseUrl("https://spreadsheets.google.com/feeds/spreadsheets/private/full");
                url.title = title;
                url.title_exact = exact;
                
                HttpResponse response = wiseRequestFactory.buildGetRequest(url).execute();

                feedParser = 
                    AtomFeedParser.create(response, SPREADSHEET_FEED_NS, SpreadsheetFeed.class, SpreadsheetEntry.class);
            }

            public Spreadsheet parseOne() throws IOException, XmlPullParserException {
                SpreadsheetEntry entry = (SpreadsheetEntry)feedParser.parseNextEntry();

                if (entry == null)
                    return null;

                return new Spreadsheet(SpreadsheetsService.this, entry.title, entry.content.src);
            }

        };
    }
    
    public void createSpreadsheet(String title, boolean hidden) throws IOException, SpreadsheetsException {
        
        final GenericUrl url = new GenericUrl("https://docs.google.com/feeds/default/private/full");
        
        final StringBuilder builder = new StringBuilder();
        builder.append("<?xml version='1.0' encoding='UTF-8'?>")
        .append("<entry xmlns=\"http://www.w3.org/2005/Atom\">")
        .append("<category scheme=\"http://schemas.google.com/g/2005#kind\" ")
        .append("term=\"http://schemas.google.com/docs/2007#spreadsheet\"/>");
        if (hidden) {
            builder.append("<category scheme=\"http://schemas.google.com/g/2005/labels\" ")
            .append("term=\"http://schemas.google.com/g/2005/labels#hidden\" label=\"hidden\"/>");
        }
        builder.append("<title>").append(Utils.encodeXML(title)).append("</title></entry>");
        
        new Request<Void>() {
            public Void run() throws IOException, XmlPullParserException {
                ByteArrayContent content = new ByteArrayContent(builder.toString());
                HttpRequest request = writelyRequestFactory.buildPostRequest(url, content);
                
                request.enableGZipContent = false;
                request.headers.contentType = "application/atom+xml";
                request.headers.acceptEncoding = null;
                request.headers.contentEncoding = null;
                
                HttpResponse response = request.execute();
                                 
                // can't find a way to use the response to find a link to the created
                // spreadsheet, as a Spreadsheet API link, so ignore() it for now.
                response.ignore(); 
                return null;
            }
        }.execute();
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
