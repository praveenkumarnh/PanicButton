package com.apb.beacon;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.apb.beacon.common.AppUtil;
import com.apb.beacon.data.PBDatabase;
import com.apb.beacon.model.Page;
import com.apb.beacon.model.ServerResponse;
import com.apb.beacon.parser.JsonParser;
import com.apb.beacon.wizard.WizardActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import roboguice.activity.RoboActivity;
import roboguice.inject.ContentView;

import static com.apb.beacon.ApplicationSettings.getLocalDataInsertion;
import static com.apb.beacon.ApplicationSettings.isFirstRun;
import static com.apb.beacon.ApplicationSettings.setLocalDataInsertion;
import com.crashlytics.android.Crashlytics;

@ContentView(R.layout.welcome_screen)
public class HomeActivity extends RoboActivity {
    public static final int SPLASH_TIME = 1000;

    ProgressDialog pDialog;
//    JsonParser jsonParser;

    String pageId;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Crashlytics.start(this);
        /*
        hard-code initial data to the database.
         */
        checkIfDataInitializationNeeded();

        int wizardState = ApplicationSettings.getWizardState(this);
        if (wizardState == AppConstants.WIZARD_FLAG_HOME_NOT_COMPLETED) {
            pageId = "home-not-configured";
        } else if (wizardState == AppConstants.WIZARD_FLAG_HOME_NOT_CONFIGURED_ALARM) {
            pageId = "home-not-configured-alarm";
        } else if (wizardState == AppConstants.WIZARD_FLAG_HOME_NOT_CONFIGURED_DISGUISE) {
            pageId = "home-not-configured-disguise";
        } else if (wizardState == AppConstants.WIZARD_FLAG_HOME_READY) {
            pageId = "home-ready";
        }

        checkIfUpdateNeeded();

    }

    private void checkIfDataInitializationNeeded(){
        if (!getLocalDataInsertion(HomeActivity.this)) {
            initializeLocalData();
            setLocalDataInsertion(HomeActivity.this, true);
        }
    }

    private void checkIfUpdateNeeded(){
        long lastRunTimeInMillis = ApplicationSettings.getLastRunTimeInMillis(this);
        if (!AppUtil.isToday(lastRunTimeInMillis) && AppUtil.hasInternet(HomeActivity.this)) {
            Log.e(">>>>", "last run not today");
            new GetUpdate().execute();
        }
        else{
            if (isFirstRun(HomeActivity.this)) {
                scheduleTimer();
            } else {
                startFacade();
            }
        }
    }

    private void startFacade() {
        startActivity(new Intent(this, CalculatorActivity.class));
    }

    private void scheduleTimer() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                Intent i = new Intent(HomeActivity.this, WizardActivity.class);
                i.putExtra("page_id", pageId);
                startActivity(i);
//                startActivity(new Intent(HomeActivity.this, WizardActivity.class));
            }
        }, SPLASH_TIME);
    }


    private class GetUpdate extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pDialog = ProgressDialog.show(HomeActivity.this, "Panic Button", "Checking for updates...", true, false);
        }

        @Override
        protected Void doInBackground(Void... params) {
            String url = AppConstants.BASE_URL;
            JsonParser jsonParser = new JsonParser();
            ServerResponse response = jsonParser.retrieveServerData(AppConstants.HTTP_REQUEST_TYPE_GET, url, null, null, null);
            if (response.getStatus() == 200) {
                Log.d(">>>><<<<", "success in retrieving server-response for url = " + url);
                ApplicationSettings.setLastRunTimeInMillis(HomeActivity.this, System.currentTimeMillis());          // if we can retrieve a single data, we change it up-to-date

                try {
                    JSONObject responseObj = response.getjObj();
                    JSONObject mobObj = responseObj.getJSONObject("mobile");
                    JSONArray dataArray = mobObj.getJSONArray("data");
                    insertJsonDataToLocalDB(dataArray);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void response) {
            super.onPostExecute(response);
            if (pDialog.isShowing())
                pDialog.dismiss();

            if (isFirstRun(HomeActivity.this)) {
                Intent i = new Intent(HomeActivity.this, WizardActivity.class);
                i.putExtra("page_id", pageId);
                startActivity(i);
            } else {
                startActivity(new Intent(HomeActivity.this, CalculatorActivity.class));
            }
        }
    }

    private void insertJsonDataToLocalDB(JSONArray dataArray){
        List<Page> pageList = Page.parsePages(dataArray);

        PBDatabase dbInstance = new PBDatabase(HomeActivity.this);
        dbInstance.open();

        for(int i = 0; i< pageList.size(); i++){
            dbInstance.insertOrUpdateWizardPage(pageList.get(i));
        }
        dbInstance.close();
    }


    private void initializeLocalData(){
        try {
            JSONObject jsonObj = new JSONObject(loadJSONFromAsset());
            JSONObject mobileObj = jsonObj.getJSONObject("mobile");
            int version = mobileObj.getInt("version");
            Log.e(">>>>>", "current version = " + version);

            JSONArray dataArray = mobileObj.getJSONArray("data");
            insertJsonDataToLocalDB(dataArray);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    public String loadJSONFromAsset() {
        String json = null;
        try {
            InputStream is = getAssets().open("mobile.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }

}