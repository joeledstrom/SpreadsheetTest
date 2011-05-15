package foo.test.spreadsheettest;


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
import foo.joeledstrom.spreadsheets.SpreadsheetsService.TokenSupplier;
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
            TokenSupplier supplier = new TokenSupplier() {
                @Override
                public void invalidateToken(String token) {
                    AccountManager.get(SpreadsheetTestActivity.this).invalidateAuthToken("com.google", token);
                }
                @Override
                public String getToken(String authTokenType) {
                    try {
                        return AccountManager.get(SpreadsheetTestActivity.this).blockingGetAuthToken(account, "wise", false);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            };


            SpreadsheetsService service = new SpreadsheetsService("company-app-v2", supplier);


            

            try {
                FeedIterator<Spreadsheet> spreadsheetFeed = service.getSpreadsheets();
                // get all spreadsheets
                
                List<Spreadsheet> spreadsheets = spreadsheetFeed.getEntries(); // reads and parses the whole stream
                Spreadsheet firstSpreadsheet = spreadsheets.get(0);

                Log.e(tag, firstSpreadsheet.getTitle());

                FeedIterator<Worksheet> worksheets = firstSpreadsheet.getWorksheets(); 


                // this only reads and parses the first entry
                // might be useful for long spreadsheets like we do below (not really useful for worksheets tho, oh well)
                Worksheet sheet = worksheets.getNextEntry(); 
                worksheets.close(); 

                Log.e(tag, sheet.getTitle());

                FeedIterator<WorksheetRow> rows = sheet.getRows(null, null, true);

                while(true) {
                    WorksheetRow e = rows.getNextEntry();
                    if (e == null)
                        break;

                    Log.e(tag, e.getColumnNames().toString());
                    
                    //e.setValue("kaka", "05");
                    //e.commitChanges();
                }
                
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return null;
        }
    }

}