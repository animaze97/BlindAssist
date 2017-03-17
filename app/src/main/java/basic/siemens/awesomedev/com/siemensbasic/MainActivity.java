package basic.siemens.awesomedev.com.siemensbasic;

import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.gson.Gson;
import com.microsoft.projectoxford.vision.VisionServiceClient;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.microsoft.projectoxford.vision.contract.AnalysisResult;
import com.microsoft.projectoxford.vision.rest.VisionServiceException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

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
    private static final int REQUEST_RESOLVE_CONNECTION = 1005;


    private VisionServiceClient visionServiceClient = null;

    // Path for the current photo
    private String mCurrentPhotoPath = null;
    private Uri mCurrentPhotoUri = null;

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
        

        visionServiceClient = new VisionServiceRestClient(getString(R.string.api_key));

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
                //saveToDrive();
                new AnalyseTask().execute("");
                break;

            case REQUEST_RESOLVE_CONNECTION:
                if (resultCode == RESULT_OK) {
                    mClient.connect();
                }
                break;
        }
    }

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
                new getUrlTask().execute(driveFileResult.getDriveFile());
                return;
            }
            else{
                Toast.makeText(MainActivity.this, "Some fucking error", Toast.LENGTH_SHORT).show();
                return;
            }
        }
    };

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
                this.mCurrentPhotoUri = photoUri;
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

    private class getUrlTask extends AsyncTask<DriveFile , Void , String> {

        @Override
        protected String doInBackground(DriveFile... params) {
            DriveFile file = params[0];
            DriveResource.MetadataResult metadataResult = file.getMetadata(mClient).await();
            return metadataResult.getMetadata().getWebViewLink();
        }

        @Override
        protected void onPostExecute(String s) {
            Toast.makeText(MainActivity.this, "Link : " + s, Toast.LENGTH_SHORT).show();
        }
    }

    private class AnalyseTask extends AsyncTask<String,String,String> {

        @Override
        protected String doInBackground(String... params) {

            Bitmap mBitmap = ImageHelper.loadSizeLimitedBitmapFromUri(mCurrentPhotoPath,getContentResolver());

            Log.d(TAG, "doInBackground: " + mCurrentPhotoUri.toString());

            Gson gson = new Gson();
            String[] features = {"ImageType", "Color", "Faces", "Adult", "Categories"};
            String[] details = {};

            // Put the image into an input stream for detection.
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

            AnalysisResult v = null;
            try {
                v = visionServiceClient.analyzeImage(inputStream, features, details);
            } catch (VisionServiceException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String result = gson.toJson(v);
            Log.d("result", result);

            return result;
        }
    }
}
