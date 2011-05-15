package foo.joeledstrom.spreadsheets;

import java.io.IOException;
import java.util.Formatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.xmlpull.v1.XmlPullParserException;

import android.util.Log;

import com.google.api.client.googleapis.GoogleHeaders;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.xml.atom.AtomParser;

import foo.joeledstrom.spreadsheets.SpreadsheetsService.SpreadsheetsException;
import foo.joeledstrom.spreadsheets.SpreadsheetsService.WiseUrl;
import foo.joeledstrom.spreadsheets.Worksheet.ListEntry;

public class WorksheetRow {


    private final SpreadsheetsService service;
    private Map<String, String> values;
    private String editUrl;
    private boolean dirty;
    private String id;
    private String eTag;

    
    public String getValue(String columnName) {
        return values.get(columnName);
    }
    
    public void setValue(String columnName, String value) {
        dirty = true;
        values.put(columnName, value);
    }

    public Set<String> getColumnNames() {
        return values.keySet();
    }

    public boolean commitChanges() throws IOException, SpreadsheetsException {
        return commitChanges(true);
    }
    
    public void applyChanges() throws IOException, SpreadsheetsException {
        commitChanges(false);
    }
    
    private boolean commitChanges(final boolean useETag) throws IOException, SpreadsheetsException {
        
        final StringBuilder builder = new StringBuilder();
        Formatter formatter = new Formatter(builder, Locale.US); 
        
        builder.append("<entry xmlns=\"http://www.w3.org/2005/Atom\" " +
                "xmlns:gsx=\"http://schemas.google.com/spreadsheets/2006/extended\">");
        
        
        for (Map.Entry<String, String> value : values.entrySet()) 
            formatter.format("<gsx:%1$s>%2$s</gsx:%1$s>", value.getKey(), value.getValue());
        builder.append("</entry>");
       
        WorksheetRow updatedRow;
        try {
            updatedRow = service.new Request<WorksheetRow>() {
                public WorksheetRow run() throws IOException, XmlPullParserException {
                    
                    AtomParser atomParser = new AtomParser();
                    atomParser.namespaceDictionary = Worksheet.LIST_FEED_NS;
                    
                    WiseUrl url = new WiseUrl(editUrl);
                    HttpContent content = new ByteArrayContent(builder.toString());        

                    HttpRequest request = service.wiseRequestFactory.buildPutRequest(url, content);
                    request.enableGZipContent = false;
                    
                    GoogleHeaders headers = (GoogleHeaders)request.headers;
                    headers.contentType = "application/atom+xml";
                    headers.acceptEncoding = null;
                    headers.contentEncoding = null;
                    
                    if (useETag)
                        headers.ifMatch = eTag;
                    else
                        headers.ifMatch = "*";
            
                    HttpResponse response = request.execute();
                    
                    ListEntry entry = atomParser.parse(response, ListEntry.class);
                    
                    return new WorksheetRow(service, entry.etag, entry.id, entry.getEditUrl(), entry.getValues());
           
                }
            }.execute();
            
            editUrl = updatedRow.editUrl;
            eTag = updatedRow.eTag;
            id = updatedRow.id;
            values = updatedRow.values;
            dirty = false;
            
            return true;
        } catch (SpreadsheetsException e) {
            if (e.getMessage().equals("412 Precondition Failed")) {
                return false;
            } else {
                throw e;
            }
        }
    }

    WorksheetRow(SpreadsheetsService service, String eTag, String id, String editUrl, Map<String, String> values) {
        this.service = service;
        this.id = id;
        this.editUrl = editUrl;
        this.values = values;
        this.eTag = eTag;
    }


    

}
