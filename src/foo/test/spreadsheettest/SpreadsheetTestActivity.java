package foo.test.spreadsheettest;


import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import foo.joeledstrom.spreadsheets.Spreadsheet;
import foo.joeledstrom.spreadsheets.SpreadsheetsService;
import foo.joeledstrom.spreadsheets.SpreadsheetsService.FeedIterator;
import foo.joeledstrom.spreadsheets.Worksheet;
import foo.joeledstrom.spreadsheets.WorksheetRow;

public class SpreadsheetTestActivity extends Activity {
    private Button button1;
    private Account account;

    private static final String tag = "SpreadsheetTestActivity";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        button1 = (Button)findViewById(R.id.button1);

        button1.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                AccountManager.get(SpreadsheetTestActivity.this)
                .getAuthTokenByFeatures("com.google","wise", null, SpreadsheetTestActivity.this, 
                        null, null, doneCallback, null);

            }
        });

    }

    private AccountManagerCallback<Bundle> doneCallback = new AccountManagerCallback<Bundle>() {
        public void run(AccountManagerFuture<Bundle> arg0) {

            Bundle b;
            try {
                b = arg0.getResult();

                String name = b.getString(AccountManager.KEY_ACCOUNT_NAME);
                String type = b.getString(AccountManager.KEY_ACCOUNT_TYPE);

                account = new Account(name, type);

                new Task().execute();
                

            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }
    };


    class Task extends AsyncTask<Void, Void, Void> {
        public Void doInBackground(Void... params) {

            SpreadsheetsService service = new SpreadsheetsService("company-app-v2");

            try {
                String writelyToken = AccountManager.get(SpreadsheetTestActivity.this).blockingGetAuthToken(account, "writely", true);
                String wiseToken = AccountManager.get(SpreadsheetTestActivity.this).blockingGetAuthToken(account, "wise", true);
                
                service.setTokens(writelyToken, wiseToken);
                
                service.createSpreadsheet("new spreadsheet 1", true);
                FeedIterator<Spreadsheet> spreadsheetFeed = service.getSpreadsheets();
                // get all spreadsheets
                
                List<Spreadsheet> spreadsheets = spreadsheetFeed.getEntries(); // reads and parses the whole stream
                Spreadsheet firstSpreadsheet = spreadsheets.get(0);

                
                Worksheet sheet = firstSpreadsheet.addWorksheet("Test sheet 1", Arrays.asList(new String[] {"date", "content", "whatever"}));
                
                
                sheet.addRow(new HashMap<String, String>() {{
                	put("date", "5324");
                    put("content", "testing !");
                }});
                
                WorksheetRow row1 = sheet.addRow(new HashMap<String, String>() {{
                	put("date", "43636544");
                    put("content", "another value");
                }});
                
                sheet.addRow(new HashMap<String, String>() {{
                    put("date", "234");
                    put("content", " escapes: < & ");
                }});
                
                
                // changes the row, overwriting any conflicting changes. (if any)
                // (use commitChanges() to check if there are conflicts)
                row1.setValue("content", "changed this row!");
                row1.applyChanges();
                
                // to save memory when working with really large data sets: 
                // we can stream a row at a time using FeedIterator
                // we can also supply a query to getRows to limit our results to matching rows.
                FeedIterator<WorksheetRow> iter = sheet.getRows("date > 1000", "date", false);
                WorksheetRow row1_alt = null;
                
                while (true) {
                	WorksheetRow r = iter.getNextEntry();
                	
                	if (r == null)
                		break;
                	
                	Log.v(tag, r.getValue("date") + " " + r.getValue("content"));
                	
                	if (r.getValue("date").equals("5324"))
                		r.applyDelete(); // remove row
                	
                }
                
                

                Log.e(tag, "DONE");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return null;
        }
    }

}