package com.example.yourgasstation;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.example.yourgasstation.R.id.send;


public class UserProblem extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks
        , GoogleApiClient.OnConnectionFailedListener, LocationListener, ConnectivityReceiver.ConnectivityReceiverListener {

    // GoogleSheet
    public static final MediaType FORM_DATA_TYPE
            = MediaType.parse("application/x-www-form-urlencoded; charset=utf-8");
    //URL derived from form URL
    public static final String URL = "https://docs.google.com/forms/d/e/1FAIpQLSe5ZCDTRWShwUBMAFFGnDWzzvnjowj1SUXUqS9fas4x4wpX4g/formResponse";

    //input element ids found from the live form page
    public static final String PROBLEM_KEY = "entry.1353616566";
    public static final String PLACE_KEY = "entry.668386161";
    public static final String PHOTO_KEY = "entry.52017663";

    //UI Elements
    EditText Problem;
    TextView addressTextView;
    Button Send;
    ImageButton getLocationButton;
    ImageButton capturePhoto;
    ImageButton cancelPhotoButton;
    ImageView capturedPhotoImageView;

    CoordinatorLayout problemImage;

    //Tags
    private static final int TAKEN_PHOTO_REQUEST_CODE = 1400;
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 1000;

    //variables
    String probDesc, locAdd;
    int getLocationButtonSwitcher; //to change from and to the (get location) and (cancel) icons
    boolean isSent;


    //location
    double latitude;
    double longitude;
    private Location mLastLocation;
    private static GoogleApiClient mGoogleApiClient;
    private boolean mRequestingLocationUpdates = false;
    private LocationRequest mLocationRequest;
    private static int UPDATE_INTERVAL = 0; // 0 sec
    private static int FASTEST_INTERVAL = 0; // 0 sec
    private static int DISPLACEMENT = 5; // 5 meters

    //Image
    Bitmap rawPhotoBitmap;
    Bitmap photoReducedSizeBitmap;
    String imageFileLocation;
    String base64String = null;
    ProgressBar progressBar;
    String ImageFileName;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_problem);

        //find view by id..
        addressTextView = (TextView) findViewById(R.id.address_text_view);
        Problem = (EditText) findViewById(R.id.desc);
        getLocationButton = (ImageButton) findViewById(R.id.get_location_button);
        capturePhoto = (ImageButton) findViewById(R.id.capture_photo);
        cancelPhotoButton = (ImageButton) findViewById(R.id.take_photo);
        progressBar = (ProgressBar) findViewById(R.id.progressBarSend);
        capturedPhotoImageView = (ImageView) findViewById(R.id.taken_photo);

        if (checkPlayServices()) {

            // Building the GoogleApi client
            buildGoogleApiClient();

            createLocationRequest();
        }


        Send = (Button) findViewById(R.id.send);

        //Toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // back button pressed
                Intent i = new Intent(UserProblem.this, GoogleMaps.class);
                startActivity(i);
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }


    }

    @Override
    protected void onResume() {
        super.onResume();
        // GasStationApp.getInstance().setConnectivityListener(UserProblem.this);
        checkPlayServices();

        // Resuming the periodic location updates
        if (mGoogleApiClient.isConnected() && mRequestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopLocationUpdates();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    protected synchronized void buildGoogleApiClient() {

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API).build();
    }

    protected void createLocationRequest() {

        mLocationRequest = new LocationRequest();

        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setSmallestDisplacement(DISPLACEMENT);
    }

    private boolean checkPlayServices() {

        int resultCode = GooglePlayServicesUtil
                .isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Toast.makeText(getApplicationContext(),
                        "This device is not supported.", Toast.LENGTH_LONG)
                        .show();
                finish();
            }
            return false;
        }
        return true;
    }

    protected void startLocationUpdates() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);

    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        Toast.makeText(getApplicationContext(), "Connection failed: ConnectionResult.getErrorCode() = "
                + connectionResult.getErrorCode(), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onConnected(Bundle arg0) {

        // Once connected with google api, get the location
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //run time permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                onLocationButtonPressed(getLocationButton);
            } else getLocation();

        } else getLocation();

        if (mRequestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    @Override
    public void onConnectionSuspended(int arg0) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        // Assign the new location
        mLastLocation = location;

        Toast.makeText(getApplicationContext(), "Location changed!",
                Toast.LENGTH_SHORT).show();

        // Displaying the new location on UI
        getLocation();
    }

    //location button functionality
    public void onLocationButtonPressed(View view) {

        addressTextView.setError(null);
        //if division remainder equal zero get location and show cancel button
        if (getLocationButtonSwitcher % 2 == 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                //run time permission
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                            , Manifest.permission.ACCESS_COARSE_LOCATION
                            , Manifest.permission.INTERNET
                    }, 1);
                    return;
                    //if permission is granted get location
                } else getLocation();

                //if android version is less than 6.0
            } else getLocation();

        }//end button switcher

        //if division remainder not equal zero show get location button
        else if (getLocationButtonSwitcher % 2 != 0) {
            Toast.makeText(getApplicationContext(), "Canceled", Toast.LENGTH_SHORT).show();
            addressTextView.setText("");
            getLocationButtonSwitcher++;
            getLocationButton.setBackgroundResource(R.drawable.ic_get_location);
        }
    }//end onLocationButtonPressed

    public void getLocation() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        //if there is no internet connection
        if (!checkConnection()) {
            Toast.makeText(getApplicationContext(), "Please check your internet connection", Toast.LENGTH_SHORT).show();
        }

        //if it can get location then get latitude and longitude
        else if (mLastLocation != null) {

            latitude = mLastLocation.getLatitude();
            longitude = mLastLocation.getLongitude();

            Geocoder geocoder = new Geocoder(getBaseContext(), Locale.getDefault());
            List<Address> list;
            try {
                list = geocoder.getFromLocation(latitude, longitude, 1);//33.64600, 72.96115
                //     address = list.get(0);
                String temp, Addresss = "";


                for (int index = 0; index < 5; index++) {

                    if (index == 4) {

                        temp = list.get(0).getAddressLine(index);
                        Addresss += temp;
                    } else {

                        temp = list.get(0).getAddressLine(index);
                        Addresss += temp + ",";
                    }
                }


                addressTextView.setText(Addresss);

                getLocationButton.setBackgroundResource(R.drawable.ic_cancel);
                getLocationButtonSwitcher++;


            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            //if can not get location
            showGPSAlert();
        }

    }

    public void showGPSAlert() {

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(UserProblem.this);
        alertDialog.setTitle("GPS settings");
        alertDialog.setMessage("GPS is not enabled. Do you want to go to settings menu?");

        alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                UserProblem.this.startActivity(intent);
            }
        });

        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });
        alertDialog.show();
    }

    //Take photo button functionality
    public void onCaptureImageButtonPressed(View view) {


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //run time permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, 2);
                return;
                //if permission is granted get location
            } else takePhoto();

            //if android version is less than 6.0
        } else takePhoto();

    }

    public void cancelCapturedPhoto(View view) {
        Toast.makeText(getApplicationContext(), "Canceled", Toast.LENGTH_SHORT).show();

        photoReducedSizeBitmap = null;
        rawPhotoBitmap = null;
        capturedPhotoImageView.setImageBitmap(photoReducedSizeBitmap);
        problemImage.setVisibility(View.GONE);
    }

    public void takePhoto() {

        Intent callCameraIntent = new Intent();
        callCameraIntent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
        File imageFile = null;

        try {

            imageFile = createImageFile();

        } catch (IOException e) {
            e.printStackTrace();
        }

        callCameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(imageFile));
        startActivityForResult(callCameraIntent, TAKEN_PHOTO_REQUEST_CODE);
        problemImage = (CoordinatorLayout) findViewById(R.id.problem_image);
        problemImage.setVisibility(View.VISIBLE);
    }

    public File createImageFile() throws IOException {

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        ImageFileName = "IMAGE_" + timeStamp + "_";
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

        File image = File.createTempFile(ImageFileName, ".jpg", storageDirectory);
        imageFileLocation = image.getAbsolutePath();

        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == TAKEN_PHOTO_REQUEST_CODE && resultCode == RESULT_OK) {
            setReducedImageSize();
        } else {
            problemImage.setVisibility(View.GONE);
        }
    }

    public void setReducedImageSize() {

        int targetImageViewWidth = capturedPhotoImageView.getWidth();
        int targetImageViewHeight = capturedPhotoImageView.getHeight();

        BitmapFactory.Options bmOptions = new BitmapFactory.Options();

        int cameraImageWidth = bmOptions.outWidth;
        int cameraImageHeight = bmOptions.outHeight;

        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFileLocation, bmOptions);

        int scaleFactor = Math.min(cameraImageWidth / targetImageViewWidth, cameraImageHeight / targetImageViewHeight);
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inJustDecodeBounds = false;

        photoReducedSizeBitmap = BitmapFactory.decodeFile(imageFileLocation, bmOptions);
        capturedPhotoImageView.setImageBitmap(photoReducedSizeBitmap);
        // captureImageButtonSwitcher++;
    }

    public String convertImageToString(Bitmap bitmap) {

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] bytes = stream.toByteArray();

        String imageString = Base64.encodeToString(bytes, Base64.DEFAULT);
        return imageString;
    }

    class ConvertImageAsync extends AsyncTask {

        @Override
        protected void onPreExecute() {
            Send.setVisibility(View.INVISIBLE);
            progressBar.setVisibility(View.VISIBLE);

        }

        @Override
        protected String doInBackground(Object[] objects) {

            rawPhotoBitmap = BitmapFactory.decodeFile(imageFileLocation);
            base64String = convertImageToString(rawPhotoBitmap);
            return base64String;
        }

        @Override
        protected void onPostExecute(Object o) {
            //     Toast.makeText(getApplicationContext(),"Done",Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.INVISIBLE);
            Send.setVisibility(View.VISIBLE);
            PostDataTask postDataTask = new PostDataTask();

            //execute asynctask
            postDataTask.execute(URL, Problem.getText().toString(),
                    addressTextView.getText().toString(),
                    base64String);

        }
    }

    //send button functionality
    public void send(View view) {

        //if there is a photo convert it before sending
        if (photoReducedSizeBitmap != null) {

            new ConvertImageAsync().execute();


        } else { //send report without photo

            //  new sendReportAsync().execute();
            //Create an object for PostDataTask AsyncTask
            PostDataTask postDataTask = new PostDataTask();

            //execute asynctask
            postDataTask.execute(URL, Problem.getText().toString(),
                    addressTextView.getText().toString(),
                    capturedPhotoImageView.toString()
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode) {
            case 1:
                //if permission is granted get location
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLocation();
                    break;
                }

            case 2://if permission is granted take picture
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhoto();
                    break;
                }
        }
    }

    @Override
    public void onNetworkConnectionChanged(boolean isConnected) {
        if (isConnected == true) {
            Toast.makeText(this, "Internet is connected", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Internet is disconnected", Toast.LENGTH_SHORT).show();

        }

    }

    private boolean checkConnection() {
        boolean isConnected = ConnectivityReceiver.isConnected();
        return isConnected;
    }

    //// Connecting with google spreadsheet

    //AsyncTask to send data as a http POST request
    private class PostDataTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... contactData) {
            Boolean result = true;
            String url = contactData[0];
            String prob = contactData[1];
            String loc = contactData[2];
            String photo = contactData[3];
            String postBody = "";

            try {
                //all values must be URL encoded to make sure that special characters like & | ",etc.
                //do not cause problems
                postBody = PROBLEM_KEY + "=" + URLEncoder.encode(prob, "UTF-8") +
                        "&" + PLACE_KEY + "=" + URLEncoder.encode(loc, "UTF-8") +
                        "&" + PHOTO_KEY + "=" + URLEncoder.encode(photo, "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                result = false;
            }

            try {
                //Create OkHttpClient for sending request
                OkHttpClient client = new OkHttpClient();
                //Create the request body with the help of Media Type
                RequestBody body = RequestBody.create(FORM_DATA_TYPE, postBody);
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .build();
                //Send the request
                Response response = client.newCall(request).execute();
                return result;

            } catch (IOException exception) {
                ;
                result = false;
            }
            return result;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            //Print Success or failure message accordingly
            Toast.makeText(UserProblem.this, result ? "Message successfully sent!" : "There was some error in sending message. Please try again after some time.", Toast.LENGTH_LONG).show();
        }

    }


}


