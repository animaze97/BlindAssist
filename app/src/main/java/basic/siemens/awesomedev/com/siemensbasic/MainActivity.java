package basic.siemens.awesomedev.com.siemensbasic;

import android.accounts.AccountManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.jar.Manifest;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements  View.OnClickListener, /*EasyPermissions.PermissionCallbacks,*/
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String PREF_ACCOUNT_NAME = "accountName";

    private Button bPhotoButton = null;
    private TextView tvResultView = null;


    private GoogleAccountCredential mCredentials = null;
    private GoogleApiClient mClient = null;
    private Drive service = null;

    private static final String[] SCOPES = {DriveScopes.DRIVE};

    // Request Code for Camera Intent
    private static final int REQUEST_CAPTURE_IMAGE = 1000;
    private static final int REQUEST_ACCOUNT_PICKER = 1001;
    private static final int REQUEST_AUTHORIZATION = 1002;
    private static final int REQUEST_GOOGLE_PLAY_SERVICES = 1003;
    private static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1004;
    private static final int REQUEST_RESOLVE_CONNECTION = 1005;

    // Path for the current photo
    private String mCurrentPhotoPath = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind the views
        bPhotoButton = (Button) findViewById(R.id.b_take_photo);
        tvResultView = (TextView) findViewById(R.id.tv_result_caption);

        bPhotoButton.setOnClickListener(this);
        bPhotoButton.setEnabled(false);

        mClient = new GoogleApiClient.Builder(this).addApi(com.google.android.gms.drive.Drive.API)
                .addScope(com.google.android.gms.drive.Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mCredentials = GoogleAccountCredential.usingOAuth2(getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
        
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mClient != null){
            mClient.connect();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mClient!=null){
            mClient.disconnect();
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.b_take_photo) {
            // Launch camera intent
            dispatchTakePictureIntent();
        }
    }




    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_CAPTURE_IMAGE:
                Toast.makeText(this, "Captured the image", Toast.LENGTH_SHORT).show();
//                getResultsFromApi();
                saveToDrive();
                break;
/*
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    Toast.makeText(this, "This app requires google play services", Toast.LENGTH_SHORT).show();
                } else {
                    // Get the results from api
                }
                break;

            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredentials.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;*/

            case REQUEST_RESOLVE_CONNECTION:
                if (resultCode == RESULT_OK) {
                    mClient.connect();
                }
                break;
        }
    }

/*
    private void getResultsFromApi() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredentials.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!isDeviceOnline()) {
            Toast.makeText(this, "No Network Available", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Good to Go!", Toast.LENGTH_SHORT).show();
            saveToDrive();
        }
    }

    public void authorize(){

    }

*/

    private void saveToDrive() {
        com.google.android.gms.drive.Drive.DriveApi.newDriveContents(mClient).setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
            @Override
            public void onResult(@NonNull DriveApi.DriveContentsResult result) {
                if (!result.getStatus().isSuccess()){
                    Log.d(TAG, "onResult: Could not create contents");
                    return;
                }

                OutputStream outputStream = result.getDriveContents().getOutputStream();
                FileInputStream inputStream = null;

                try {
                    inputStream = new FileInputStream(mCurrentPhotoPath);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    byte[] buf = new byte[1024];
                    int n = 0;
                    while((n=inputStream.read(buf))!=-1){
                        byteArrayOutputStream.write(buf, 0 ,n);
                    }
                    byte[] photobytes = byteArrayOutputStream.toByteArray();
                    outputStream.write(photobytes);

                    outputStream.close();
                    outputStream = null;

                    inputStream.close();
                    inputStream = null;

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                String title = new File(mCurrentPhotoPath).getName();
                MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder().setMimeType("image/jpeg").setTitle(title).build();

                com.google.android.gms.drive.Drive.DriveApi.getRootFolder(mClient).createFile(mClient,metadataChangeSet,result.getDriveContents()).setResultCallback(fileCallback);
            }
        });
    }

    final private ResultCallback<DriveFolder.DriveFileResult> fileCallback = new ResultCallback<DriveFolder.DriveFileResult>() {
        @Override
        public void onResult(@NonNull DriveFolder.DriveFileResult driveFileResult) {
            if (driveFileResult.getStatus().isSuccess()){
                Toast.makeText(MainActivity.this, "File Successfully Created!!", Toast.LENGTH_SHORT).show();
                return;
            }
            else{
                Toast.makeText(MainActivity.this, "Some fucking error", Toast.LENGTH_SHORT).show();
                return;
            }
        }
    };
/*


    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(this, android.Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE).getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredentials.setSelectedAccountName(accountName);

                getResultsFromApi();

            } else {
                startActivityForResult(mCredentials.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
            }
        } else {
            EasyPermissions.requestPermissions(this, "The app needs to access your google account",
                    REQUEST_PERMISSION_GET_ACCOUNTS, android.Manifest.permission.GET_ACCOUNTS);
        }
    }

*/

    private void dispatchTakePictureIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (intent.resolveActivityInfo(getPackageManager(), PackageManager.GET_ACTIVITIES) != null) {
            // Create a file for the full sized photo
            File mPhoto = null;

            try {
                mPhoto = createImageFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (mPhoto != null) {
                Uri photoUri = FileProvider.getUriForFile(this, "com.example.android.fileprovider", mPhoto);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                // Dispatch the camera intent
                startActivityForResult(intent, REQUEST_CAPTURE_IMAGE);
            }
        }
    }

    private File createImageFile() throws IOException {

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFilename = "JPEG_" + timestamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFilename, ".jpg", storageDir);

        // Get the path
        mCurrentPhotoPath = image.getAbsolutePath();
        Log.d(TAG, "createImageFile: " + mCurrentPhotoPath);
        return image;

    }
/*
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {

    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {

    }


    private boolean isDeviceOnline() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(connectivityManager.getActiveNetwork());
        return (networkInfo != null && networkInfo.isConnected());
    }

    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability mAvailability = GoogleApiAvailability.getInstance();
        int availabilityStatusCode = mAvailability.isGooglePlayServicesAvailable(this);

        return availabilityStatusCode == ConnectionResult.SUCCESS;

    }

    private void acquireGooglePlayServices() {

        GoogleApiAvailability mAvailability = GoogleApiAvailability.getInstance();
        int availabilityStatusCode = mAvailability.isGooglePlayServicesAvailable(this);

        if (mAvailability.isUserResolvableError(availabilityStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(availabilityStatusCode);
        }
    }

    private void showGooglePlayServicesAvailabilityErrorDialog(int availabilityStatusCode) {
        GoogleApiAvailability mAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = mAvailability.getErrorDialog(this, availabilityStatusCode, REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }*/

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        bPhotoButton.setEnabled(true);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show();
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, REQUEST_RESOLVE_CONNECTION);
            } catch (IntentSender.SendIntentException e) {
                // Unable to resolve, message user appropriately
            }
        } else {
            GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), this, 0).show();
        }
    }
}
