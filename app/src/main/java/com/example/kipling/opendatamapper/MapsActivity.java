package com.example.kipling.opendatamapper;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.maps.android.heatmaps.WeightedLatLng;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static android.content.ContentValues.TAG;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {


    // Mapas
    private GoogleMap mMap;
    private boolean mapon = false;
    private boolean record = false;
    ArrayList<WeightedLatLng> geolist = new ArrayList<WeightedLatLng>();
    List<String> IDandInOutPhase;
    HeatmapTileProvider mProvider;
    TileOverlay mOverlay;
    private Marker marker;


    // Bluetooth
    EditText bluetoothSend;
    private BluetoothAdapter bluetoothAdapter;
    private Set<BluetoothDevice> pairedDevices;

    private static UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private OutputStream mmOutStream;
    private InputStream mmInStream;

    private BluetoothSocket mmSocket;
    private byte[] mmBuffer; // mmBuffer store for the stream
    private TextView textView;

    private Handler mHandler; // handler that gets info from Bluetooth service



    private boolean newdata = false;

    // Saving files

    File output_file;


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.splashScreenTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

        // Create file
        File myfolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File mediaStorageDir = new File(myfolder, "OpenEMapp");

        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.
        String state = Environment.getExternalStorageState(mediaStorageDir);
        Log.d("TAG ","State: "+state);
        //Toast.makeText(getApplicationContext(), "State: "+state ,Toast.LENGTH_LONG).show();

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("TAG", "failed to create directory");
                Toast.makeText(getApplicationContext(), "failed to create directory",Toast.LENGTH_LONG).show();
            }else{
                Log.d("TAG", "Can be created");
                Toast.makeText(getApplicationContext(), "Can be created" ,Toast.LENGTH_LONG).show();
            }
        }else{
            Log.d("TAG", "already exists");
            Toast.makeText(getApplicationContext(), "already exists",Toast.LENGTH_LONG).show();
        }

        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            output_file = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".csv");
            FileWriter writer = new FileWriter(output_file,true);

            writer.append("Latitude,Longitude,Accuracy,Time,InPhase,OutPhase "+"\n");
            writer.flush();
            writer.close();

        } catch (Exception e) {
            e.printStackTrace();
        }


        //// Location
        // Acquire a reference to the system Location Manager
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                Log.d("TAG","onLocationChanged");

                // Called when a new location is found by the network location provider.
                if(mapon & record){
                    uiOut(location);
                }

            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        // Register the listener with the Location Manager to receive location updates
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            Intent gpsOptionsIntent = new Intent(
                    android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(gpsOptionsIntent);
            return;
        }
        Log.d("TAG","requestLocationUpdates");
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);


        textView = (TextView) findViewById(R.id.textView);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mmSocket = null;



        on();
        connector();
        th.start();


    }



    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mapon = true;

    }



    private static final int ONE_SECOND = 1000;

    /** Determines whether one Location InPhase is better than the current Location fix
     * @param location  The new Location that you want to evaluate
     * @param currentBestLocation  The current Location fix, to which you want to compare the new one
     */
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > ONE_SECOND;
        boolean isSignificantlyOlder = timeDelta < -ONE_SECOND;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }




    Location currentLocation;
    double InPhase = 0;
    double IDdata = 0;
    double OutPhase = 0;

    void uiOut(Location coords){

        if(isBetterLocation(coords,currentLocation)){
            currentLocation = coords;
        }



        double lat = currentLocation.getLatitude();
        double lon = currentLocation.getLongitude();
        float accuracy = currentLocation.getAccuracy();
        Log.d("TAG",lat+","+lon);
        LatLng next = new LatLng(lat, lon);



        if(newdata){

            Log.d("TAG","YES NEW DATA");

            String currentTime = new SimpleDateFormat("HHmmss").format(new Date());
            String topyboard = Double.toString(lat) + ',' + Double.toString(lon) + ',' + currentTime;

            String name = "BTM-G";
            byte[] bytes = ('G'+topyboard + name).getBytes();
            Log.d("TAG","to send: " + topyboard+name);
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("TAG",""+e);
                Toast.makeText(getApplicationContext(), "Send failed" ,Toast.LENGTH_LONG).show();
            }

            //WeightedLatLng tempWLL = new WeightedLatLng(next,InPhase);




            String output = Double.toString(lat)+ ',' + Double.toString(lon)+ ','+ Double.toString(accuracy) + ',' + currentTime+',' + InPhase+',' + OutPhase;

            Log.d("TAG",output);
            try {
                marker = mMap.addMarker(new MarkerOptions().position(next)
                        .title("" + InPhase).flat(true).icon(BitmapDescriptorFactory.defaultMarker(2*(float)(InPhase))));
            }catch (Exception e){
                Log.d("TAG","Does not compute!");
            }

            try {
                Log.d("TAG","TO FILE?  "+output);

                FileWriter writer = new FileWriter(output_file,true);

                writer.append(output+"\n");
                writer.flush();
                writer.close();

            } catch (Exception e) {
                e.printStackTrace();
            }



            //addHeatMap(tempWLL);
            newdata = false;

        }

        if(geolist.size()%10 == 0) {
            mMap.moveCamera(CameraUpdateFactory.newLatLng(next));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(18));
        }

    }







    public void start(View v){


        String name = "BTM-U";
        byte[] bytes = name.getBytes();
        Log.d("TAG","Pressed: "+name);
        try {
            mmOutStream.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("TAG",""+e);
            Toast.makeText(getApplicationContext(), "Send failed" ,Toast.LENGTH_LONG).show();
        }



        record = true;
        Toast.makeText(getApplicationContext(), "Start" ,Toast.LENGTH_LONG).show();
    }

    public void stop(View v){
        record = false;
        try {
            mOverlay.remove();
            geolist.clear();
        }catch (Exception e){}

        String name = "BTM-F";
        byte[] bytes = name.getBytes();
        Log.d("TAG","Pressed: "+name);
        try {
            mmOutStream.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("TAG",""+e);
            Toast.makeText(getApplicationContext(), "Send failed" ,Toast.LENGTH_LONG).show();
        }



        Toast.makeText(getApplicationContext(), "Stop" ,Toast.LENGTH_LONG).show();
    }













    // Bluetooth



    // This is to turn on the bluetooth adapter if it is not already on
    public void on(){
        if (!bluetoothAdapter.isEnabled()) {
            Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnOn, 0);
            Toast.makeText(getApplicationContext(), "Turned on",Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getApplicationContext(), "Already on", Toast.LENGTH_LONG).show();
        }
    }


    // Call this to turn off the bluetooth adapter (not used)
    public void off(View v){
        bluetoothAdapter.disable();
        Toast.makeText(getApplicationContext(), "Turned off" ,Toast.LENGTH_LONG).show();
    }



    // If connection is not established on app startup (onCreate) try again with this method
    public void connect(View v){


        try{
            String name = "CONNECTED";
            byte[] bytes = name.getBytes();
            mmOutStream.write(bytes);
        }catch (IOException e){
            Toast.makeText(getApplicationContext(), "Connecting..." ,Toast.LENGTH_LONG).show();
            connector();


        }
    }


    public void connector(){

        OutputStream tmpOut = null;
        InputStream tmpIn = null;

        // Get list of paired devices

        BluetoothSocket tmp = null;

        String dname;


        pairedDevices = bluetoothAdapter.getBondedDevices();
        BluetoothDevice device = null;
        if(pairedDevices.size() >0) {
            for (BluetoothDevice bt : pairedDevices) {
                Log.d("TAG", bt.getName());
                dname = bt.getName();
                if (dname.equals("HC-05")) {
                    device = bt;
                    Log.d("TAG", "HC-05 PARED!!!");
                    //Toast.makeText(getApplicationContext(), device.getName(), Toast.LENGTH_LONG).show();


                } else {
                    Log.d("TAG", "Not HC-05");
                }

            }

            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);

            } catch (IOException e) {
                Log.d("TAG", "Socket's listen() method failed", e);
                Toast.makeText(getApplicationContext(), "Error 1" ,Toast.LENGTH_LONG).show();
            }
            mmSocket = tmp;


            bluetoothAdapter.cancelDiscovery();



            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();


                Log.d("TAG", "Socket connected!!!!!");
                Toast.makeText(getApplicationContext(), "Connected" ,Toast.LENGTH_LONG).show();
            } catch (IOException connectException) {}



            try {
                tmpIn = mmSocket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }


            try {

                tmpOut = mmSocket.getOutputStream();


            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
                Toast.makeText(getApplicationContext(), "Error 2" ,Toast.LENGTH_LONG).show();
            }

            mmOutStream = tmpOut;
            mmInStream = tmpIn;



        }else{
            Log.d("TAG", "No devices");
            Toast.makeText(getApplicationContext(), "HC-05 is not pared", Toast.LENGTH_LONG).show();
        }




    }


    // thread to listen to the input data from HC05 (not perfect)
    Thread th = new Thread(new Runnable() {
        public void run() {


            mmBuffer = new byte[4096];
            int numBytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    if(mmInStream.available()>2) {
                        //Log.d("TAG", "YES Data");

                        // Read from the InputStream.
                        numBytes = mmInStream.read(mmBuffer);



                        final String readMessage = new String(mmBuffer, 0, numBytes);
                        Log.d("TAG",readMessage);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                textView.setText(readMessage);
                            }
                        });

                        newdata = true;
                        Log.d("TAG","New data");




                        try{

                            IDandInOutPhase = Arrays.asList(readMessage.split("\\s*,\\s*"));
                            IDdata = Double.parseDouble(IDandInOutPhase.get(0));
                            InPhase = Double.parseDouble(IDandInOutPhase.get(1));
                            OutPhase = Double.parseDouble(IDandInOutPhase.get(2));

                            newdata = true;
                        }catch (Exception e){
                            Log.d("TAG", "Shit data ");
                        }



                    }else{
                        SystemClock.sleep(100);
                        //Log.d("TAG", "No Data");
                    }





                } catch (IOException e) {
                    Log.d("TAG", "Input stream was disconnected", e);
                    break;
                }
            }


        }
    });


}
