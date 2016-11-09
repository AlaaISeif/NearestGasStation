package com.example.yourgasstation;

import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static com.example.yourgasstation.R.color.colorApp;

public class GetNearbyPlacesData extends AsyncTask<Object, String, String> {
    String googlePlacesData;
    GoogleMap mMap;
    String url;
    List<HashMap<String, String>> nearbyPlacesList = null;
    ArrayList<Double> DistList = new ArrayList<>();
    private static LatLng CurrentLocation;

    @Override
    protected String doInBackground(Object... params) {
        try {
            Log.d("GetNearbyPlacesData", "doInBackground entered");
            mMap = (GoogleMap) params[0];
            url = (String) params[1];
            DownloadUrl downloadUrl = new DownloadUrl();
            googlePlacesData = downloadUrl.readUrl(url);
            Log.d("GooglePlacesReadTask", "doInBackground Exit");
        } catch (Exception e) {
            Log.d("GooglePlacesReadTask", e.toString());
        }
        return googlePlacesData;
    }

    @Override
    protected void onPostExecute(String result) {
        Log.d("GooglePlacesReadTask", "onPostExecute Entered");
        DataParser dataParser = new DataParser();
        nearbyPlacesList = dataParser.parse(result);
        //ShowNearbyPlaces(nearbyPlacesList, CurrentLocation );
        Log.d("GooglePlacesReadTask", "onPostExecute Exit");
    }

    protected void ShowNearbyPlaces(List<HashMap<String, String>> nearbyPlacesList, LatLng CurrentLoc) {
        CurrentLocation = CurrentLoc;
        LatLng latLng = null;
        for (int i = 0; i < nearbyPlacesList.size(); i++) {
            Log.d("onPostExecute", "Entered into showing locations");
            MarkerOptions markerOptions = new MarkerOptions();
            HashMap<String, String> googlePlace = nearbyPlacesList.get(i);
            double lat = Double.parseDouble(googlePlace.get("lat"));
            double lng = Double.parseDouble(googlePlace.get("lng"));
            String placeName = googlePlace.get("place_name");
            String vicinity = googlePlace.get("vicinity");
            latLng = new LatLng(lat, lng);
            markerOptions.position(latLng);
            markerOptions.title(placeName + " : " + vicinity);
            mMap.addMarker(markerOptions)
                    .setIcon(BitmapDescriptorFactory.fromResource(R.drawable.gas_station));
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
        }

    }
    public double rad(double x) {return x*Math.PI/180;}

    public LatLng find_closest_marker(List <HashMap<String,String>> PlacesList ) {
        double lat = CurrentLocation.latitude;
        double lng = CurrentLocation.longitude;
        int closest =-1;
        double EarthRadius = 6371; // radius of earth in km

        for( int i=0; i<PlacesList.size(); i++ ) {
            HashMap<String, String> googlePlace = PlacesList.get(i);
            double latVal = Double.parseDouble(googlePlace.get("lat"));
            double lngVal = Double.parseDouble(googlePlace.get("lng"));
            double dLat  = rad(latVal - lat);
            double dLong = rad(lngVal - lng);
            double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                    Math.cos(rad(lat)) * Math.cos(rad(lat)) * Math.sin(dLong/2) * Math.sin(dLong/2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
            double d = EarthRadius * c;
            DistList.add( d);
            if ( closest == -1 || d < DistList.get(closest) ) {
                closest = i;
            }
        }

        return new LatLng(Double.parseDouble(PlacesList.get(closest).get("lat"))
                ,Double.parseDouble(PlacesList.get(closest).get("lng")));
    }
    private class DrawTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... url) {
            String data = "";
            try {
                HttpConnection http = new HttpConnection();
                data = http.readUrl(url[0]);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            new ParserTask().execute(result);
        }
    }

    private class ParserTask extends
            AsyncTask<String, Integer, List<List<HashMap<String, String>>>> {

        @Override
        protected List<List<HashMap<String, String>>> doInBackground(
                String... jsonData) {

            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;

            try {
                jObject = new JSONObject(jsonData[0]);
                PathJSONParser parser = new PathJSONParser();
                routes = parser.parse(jObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return routes;
        }

        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> routes) {
            ArrayList<LatLng> points = null;
            PolylineOptions polyLineOptions = null;

            // traversing through routes
            for (int i = 0; i < routes.size(); i++) {
                points = new ArrayList<LatLng>();
                polyLineOptions = new PolylineOptions();
                List<HashMap<String, String>> path = routes.get(i);

                for (int j = 0; j < path.size(); j++) {
                    HashMap<String, String> point = path.get(j);

                    double lat = Double.parseDouble(point.get("lat"));
                    double lng = Double.parseDouble(point.get("lng"));
                    LatLng position = new LatLng(lat, lng);

                    points.add(position);
                }

                polyLineOptions.addAll(points).width(8).color(Color.argb(200,250,217,42));
            }

            if (polyLineOptions != null)
                mMap.addPolyline(polyLineOptions);
        }
    }

    public void DrawPath(Location source, LatLng Destination) {
        DrawTask drawTask = new DrawTask();
        String waypoints = "waypoints=optimize:true|" + source.getLatitude() + "," + source.getLongitude() + "|" + Destination.latitude + "," + Destination.longitude;

        String sensor = "sensor=false";

        String params = "&" + waypoints + "&" + sensor;
        String output = "json";
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + "origin=" + source.getLatitude() + "," + source.getLongitude()
                + "&destination=" + Destination.latitude + "," + Destination.longitude + params;
        drawTask.execute(url);
    }
}

