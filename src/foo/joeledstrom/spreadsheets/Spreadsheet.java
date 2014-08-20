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
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.xml.atom.AtomFeedParser;
import com.google.api.client.http.xml.atom.AtomParser;
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

                return new Worksheet(service, entry.id, entry.title, entry.getListFeed(), entry.getCellsFeed(), entry.getEditUrl(), entry.rowCount);
            }

        };
    }
    
    public Worksheet addWorksheet(final String name, final List<String> columnNames) throws IOException, SpreadsheetsException {
     
        // TODO: validate columnNames (and name?)
        
        final AtomParser atomParser = new AtomParser();
        atomParser.namespaceDictionary = WORKSHEET_FEED_NS;
        
        
        final StringBuilder builder = new StringBuilder();
            
        builder.append("<entry xmlns=\"http://www.w3.org/2005/Atom\" xmlns:gs=\"http://schemas.google.com/spreadsheets/2006\">")
        .append("<title>").append(name).append("</title>") 
        .append("<gs:rowCount>2</gs:rowCount>")
        .append("<gs:colCount>").append(columnNames.size()).append("</gs:colCount>")
        .append("</entry>");
        
        
         Worksheet sheet = service.new Request<Worksheet>() {
            public Worksheet run() throws IOException, XmlPullParserException {
                WiseUrl url = new WiseUrl(worksheetFeed);
                HttpContent content = new ByteArrayContent(builder.toString());    
                HttpRequest request = service.wiseRequestFactory.buildPostRequest(url, content);
                request.enableGZipContent = false;
                
                GoogleHeaders headers = (GoogleHeaders)request.headers;
                headers.contentType = "application/atom+xml";
                headers.acceptEncoding = null;
                headers.contentEncoding = null;
                
                HttpResponse response = request.execute();
        
                
                WorksheetEntry entry = atomParser.parse(response, WorksheetEntry.class);
                
                return new Worksheet(service, entry.id, entry.title, entry.getListFeed(), entry.getCellsFeed(), entry.getEditUrl(), entry.rowCount);
            }
        }.execute();
        
        sheet.setColumns(columnNames);
        
        return sheet; // no longer empty
    }

    public static class WorksheetFeed {
        @Key("entry") public List<WorksheetEntry> entries;
    }

    public static class WorksheetEntry {
        @Key public String title;
        @Key public String id;
        @Key("gs:rowCount") public String rowCount;
        @Key("link") List<WorksheetLink> links;
        @Key WorksheetContent content;
        
        public String getCellsFeed() throws XmlPullParserException {
            return getLinkHrefByRel("http://schemas.google.com/spreadsheets/2006#cellsfeed");
        }
        public String getListFeed() throws XmlPullParserException {
            return content.src;
        }
        public String getEditUrl() throws XmlPullParserException {
            return getLinkHrefByRel("edit");
        }
        private String getLinkHrefByRel(String rel) throws XmlPullParserException {
            String href = null;
            for (WorksheetLink link : links) {
                if (link.rel.equals(rel)) {
                    href = link.href;
                }
            }
            if (href == null) 
                throw new XmlPullParserException("Worksheet feed entry structure incorrect (links (rel/href))");
            
            return href;
        }
    }
    public static class WorksheetContent {
        @Key("@src") public String src;
    }
    public static class WorksheetLink {
        @Key("@rel") public String rel;
        @Key("@href") public String href;
    }

}
