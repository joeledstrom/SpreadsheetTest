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
import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    

    private SpreadsheetsService service;
    private String id;
    private String title;
    private String listFeed;
    private String cellsFeed;
    
    private AtomParser atomParser;

    Worksheet(SpreadsheetsService service, String id, String title, String listFeed, String cellsFeed) {
        this.service = service;
        this.id = id;
        this.title = title;
        this.listFeed = listFeed;
        this.cellsFeed = cellsFeed;
    }


    public String getTitle() {
        return title;
    }
    
    public String getId() {
        return id;
    }

    
    public Set<String> getColumns() throws IOException, SpreadsheetsException {
        FeedIterator<WorksheetRow> rows = getRows();
        WorksheetRow firstEntry = rows.getNextEntry();
        rows.close(); 
        
        return firstEntry.getColumnNames();
    }
    
  
    
    static final XmlNamespaceDictionary CELLS_FEED_NS = new XmlNamespaceDictionary()
    .set("", "http://www.w3.org/2005/Atom")
    .set("openSearch", "http://a9.com/-/spec/opensearch/1.1/")
    .set("gs", "http://schemas.google.com/spreadsheets/2006")
    .set("gd", "http://schemas.google.com/g/2005")
    .set("app", "http://www.w3.org/2007/app")
    .set("batch", "http://schemas.google.com/gdata/batch");
    
    
    static class RowUploadToken {
        public RowUploadToken(int row, List<String> cells) {
            this.row = row;
            this.cells = cells;
        }
        public final int row;
        public final List<String> cells; 
        int cellsUploaded;
    } 
    
    // was't planning on making these public because it can't really guarantee that uploads were done correctly (no etag support)
    Collection<RowUploadToken> batchUpload(final Iterable<RowUploadToken> rows) throws IOException, SpreadsheetsException {
        
        final Map<String, RowUploadToken> transfersInFlight = new HashMap<String, RowUploadToken>();
        final StringBuilder builder = new StringBuilder();
       
        builder.append("<feed xmlns=\"http://www.w3.org/2005/Atom\" ")
        .append("xmlns:batch=\"http://schemas.google.com/gdata/batch\" ")
        .append("xmlns:gs=\"http://schemas.google.com/spreadsheets/2006\">")
        .append("<id>").append(cellsFeed).append("</id>");
        
        for (RowUploadToken token : rows) {

            for (int i = 0; i < token.cells.size(); i++) {
     
                // i thought adding the UUID below would fix the "Feed processing was interrupted."
                // "a response has already been sent for batch operation update id=XXXXXX" errors.
                // but doesn't seem like it           
                String batchId = token.hashCode() + "_" + i + UUID.randomUUID();
                
                builder.append("<entry>")
                .append("<batch:id>").append(batchId).append("</batch:id>")
                .append("<batch:operation type=\"update\"/>")
                .append("<id>").append(cellsFeed).append("/R"+token.row+"C"+(i+1)).append("</id>")
                .append("<link rel=\"edit\" type=\"application/atom+xml\" ")
                .append("href=\"").append(cellsFeed).append("/R"+token.row+"C"+(i+1)).append("\"/>")
                .append("<gs:cell row=\"").append(token.row)
                .append("\" col=\"").append(i+1)
                .append("\" inputValue=\"").append(token.cells.get(i)).append("\"/>")
                .append("</entry>");
            }
            token.cellsUploaded = 0;  // reset this because the user may upload the same token again, after failure
            transfersInFlight.put(token.hashCode() + "", token);
            
        }
        builder.append("</feed>");
        
        service.new Request<Void>() {
            public Void run() throws IOException, XmlPullParserException {
                WiseUrl url = new WiseUrl(cellsFeed + "/batch");
                HttpContent content = new ByteArrayContent(builder.toString());
                HttpRequest request = service.wiseRequestFactory.buildPostRequest(url, content);
                request.enableGZipContent = false;
                
                GoogleHeaders headers = (GoogleHeaders)request.headers;
                headers.contentType = "application/atom+xml";
                headers.acceptEncoding = null;
                headers.contentEncoding = null;
                headers.ifMatch = "*";
                
                HttpResponse response = request.execute();
                
                AtomFeedParser<CellsFeed, CellsEntry> feedParser = 
                    AtomFeedParser.create(response, CELLS_FEED_NS, CellsFeed.class, CellsEntry.class);
                
                try {
                    feedParser.parseFeed(); 
                    
                    while (true) {
                        CellsEntry entry = feedParser.parseNextEntry();
                        if (entry == null || entry.batchId == null) {
                            break;
                        }
                        
                        int divider = entry.batchId.indexOf("_");
                        String perRowBatchId = entry.batchId.substring(0, divider);
                        
                        RowUploadToken token = transfersInFlight.get(perRowBatchId);
                        token.cellsUploaded++;
                        
                        if (token.cellsUploaded == token.cells.size()) {
                            // row completed
                            if (entry.batchStatus.code.equals("200")) // successfully
                                transfersInFlight.remove(perRowBatchId); 
                        }
                    }
                    
                } finally {
                    try {
                        feedParser.close();
                    } catch (IOException e) 
                    {} // really ignore this
                }
                
                return null;
            }
        }.execute();
     
        
        // return the failed transfers
        return transfersInFlight.values();
    }
    
  
    
    public void setColumns(List<String> columnNames) throws IOException, SpreadsheetsException {
        List<RowUploadToken> rows = Arrays.asList(new RowUploadToken(1, columnNames));
        
        
        Collection<RowUploadToken> failedUploads = batchUpload(rows);
        
        int retryCounter = 0;
        while (failedUploads.size() != 0) {
            if(retryCounter > 3)
                throw new SpreadsheetsException("setColumns(..) FAILURE");
            
            
            failedUploads = batchUpload(failedUploads);
            retryCounter++;
        
        }      
    }
    
    public WorksheetRow addRow(Map<String, String> values) throws IOException, SpreadsheetsException {
        if (atomParser == null) {
            atomParser = new AtomParser();
            atomParser.namespaceDictionary = LIST_FEED_NS;
        }
        
        final StringBuilder builder = new StringBuilder();
            
        builder.append("<entry xmlns=\"http://www.w3.org/2005/Atom\" xmlns:gsx=\"http://schemas.google.com/spreadsheets/2006/extended\">");
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
        return getRows(sq, orderby, reverse, null);
    }
    public FeedIterator<WorksheetRow> getRows(final String sq, final String orderby, 
                                              final boolean reverse, final FeedIterator<WorksheetRow> lastQuery) 
                                              throws IOException, SpreadsheetsException {
        
        try {
            return service.new FeedIterator<WorksheetRow>() {
                public void init() throws IOException, XmlPullParserException {
                    boolean abortedBecauseNotModified = true;
                    HttpResponse response = null;
                    try {
                        WiseUrl url = new WiseUrl(listFeed);
        
                        url.sq = sq;
                        if (orderby != null)
                            url.orderby = "column:" + orderby;
                        url.reverse = reverse;
        
                        HttpRequest request = service.wiseRequestFactory.buildGetRequest(url);
                        
                        if (lastQuery != null) {
                            request.headers.ifNoneMatch = lastQuery.etag;
                        }
                        
                        response = request.execute();
                      
                        etag =  response.headers.etag;
                        feedParser =
                            AtomFeedParser.create(response, LIST_FEED_NS, ListFeed.class, ListEntry.class);
                        
                        abortedBecauseNotModified = false;
                    } finally {
                        if (abortedBecauseNotModified && response != null)
                            response.ignore(); // clear up resources if we dont need the content of the response
                    }
                }
                public WorksheetRow parseOne() throws IOException, XmlPullParserException {
                    ListEntry entry = (ListEntry)feedParser.parseNextEntry();
    
                    if (entry == null)
                        return null;
    
                    return new WorksheetRow(service, entry.etag, entry.id, entry.getEditUrl(), entry.getValues());
                }
            };
        } catch (SpreadsheetsException e) {
            if (e.getMessage().equals("304 Not Modified")) {
                return null;
            } else {
                throw e;
            }
        }
    }
    
    
    // model classes for ListFeed
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
                        throw new XmlPullParserException("List feed entry structure incorrect (gsx)");
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
                throw new XmlPullParserException("List feed entry structure incorrect (edit url)");
            
            return editUrl;
        }
    }
    public static class ListLink {
        @Key("@rel") public String rel;
        @Key("@href") public String href;
    }
    
    
    // model classes for CellFeed used in batchUpload(..) 
    public static class CellsFeed {
        @Key("entry") public List<CellsEntry> entries;
    }
    public static class CellsEntry {
        @Key("batch:id") public String batchId;
        @Key("batch:status") public CellsBatchStatus batchStatus;
    }
    public static class CellsBatchStatus {
        @Key("@code") public String code;
    }
}
