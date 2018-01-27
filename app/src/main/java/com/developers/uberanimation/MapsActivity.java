package com.developers.uberanimation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.location.Location;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.developers.uberanimation.Remote.IGoogleApi;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.SquareCap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.ls.LSException;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;

import static com.google.android.gms.maps.model.JointType.ROUND;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = MapsActivity.class.getSimpleName();
    SupportMapFragment mapFragment;
    private GoogleMap mMap;
    private List<LatLng> polyLineList;
    private Marker marker;
    private float v;
    private double lat, lng;
    private Handler handler;
    private LatLng startPosition, endPosition;
    private int index, next;
    private Button button;
    private EditText destinationEditText;
    private String destination;
    private PolylineOptions polylineOptions, blackPolylineOptions;
    private Polyline blackPolyline, greyPolyLine;
    private LatLng mLocation;
    private LatLng sydney;

    IGoogleApi mService;




    Runnable drawPathRunnable = new Runnable() {
        @Override
        public void run() {
            if(index < polyLineList.size()-1){
                index++;
                next = index+1;
            }
            if(index < polyLineList.size()-1){
                startPosition = polyLineList.get(index);
                endPosition = polyLineList.get(index);
            }
            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0,1);
            valueAnimator.setDuration(3000); //3 sec
            valueAnimator.setInterpolator(new LinearInterpolator());
            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    v = valueAnimator.getAnimatedFraction();
                    lng = v*endPosition.longitude+(1-v)*startPosition.longitude;
                    lat = v*endPosition.latitude+(1-v)*startPosition.latitude;
                    LatLng newPos = new LatLng(lat,lng);
                    marker.setPosition(newPos);
                    marker.setAnchor(0.5f,0.5f);
                    marker.setRotation(getBearing(startPosition,newPos));
                    mMap.moveCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder().target(newPos).zoom(15.5f).build()));
                }
            });
            valueAnimator.start();
            handler.postDelayed(this,3000);
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        polyLineList = new ArrayList<>();
        button = findViewById(R.id.destination_button);
        destinationEditText =  findViewById(R.id.edittext_place);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                destination = destinationEditText.getText().toString();
                destination = destination.replace(" ", "+");
                mapFragment.getMapAsync(MapsActivity.this);
            }
        });
        mService = Common.getGoogleAPI();
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.setTrafficEnabled(false);
        mMap.setIndoorEnabled(false);
        mMap.setBuildingsEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);



        final double latitude = -11.8473063;
        double longitude = -77.09575459999996;

        // Add a marker in Home and move the camera
        sydney = new LatLng(-11.8473063,  -77.09575459999996);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Home"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder()
                .target(googleMap.getCameraPosition().target)
                .zoom(17)
                .bearing(30)
                .tilt(45)
                .build()));
        String requestUrl = null;
        try {
            requestUrl = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "mode=driving&"
                    + "transit_routing_preference=less_driving&"
                    + "origin=" + latitude + "," + longitude + "&"
                    + "destination=" + destination + "&"
                    + "key=" + getResources().getString(R.string.google_directions_key);
            Log.d(TAG, requestUrl);
            mService.getDataFromGoogleApi(requestUrl).enqueue(new Callback<String>() {
                @Override
                public void onResponse(Call<String> call, retrofit2.Response<String> response) {
                    try{
                        JSONObject jsonObject = new JSONObject(response.body().toString());
                        JSONArray jsonArray =  jsonObject.getJSONArray("routes");
                        for(int i=0;i<jsonArray.length();i++) {
                            JSONObject route = jsonArray.getJSONObject(i);
                            JSONObject poly = route.getJSONObject("overview_polyline");
                            String polyline = poly.getString("points");
                            polyLineList = decodePoly(polyline);
                        }

                        //adjusting bounds
                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                        for(LatLng latLng:polyLineList)
                            builder.include(latLng);
                        LatLngBounds bounds = builder.build();
                        CameraUpdate mCameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds,2);
                        mMap.animateCamera(mCameraUpdate);

                        polylineOptions= new PolylineOptions();
                        polylineOptions.color(Color.GRAY);
                        polylineOptions.width(5);
                        polylineOptions.startCap(new SquareCap());
                        polylineOptions.endCap( new SquareCap());
                        polylineOptions.jointType(JointType.ROUND);
                        polylineOptions.addAll(polyLineList);
                        greyPolyLine = mMap.addPolyline(polylineOptions);

                        blackPolylineOptions = new PolylineOptions();
                        blackPolylineOptions.color(Color.BLACK);
                        blackPolylineOptions.width(5);
                        blackPolylineOptions.startCap(new SquareCap());
                        blackPolylineOptions.endCap(new SquareCap());
                        blackPolylineOptions.jointType(JointType.ROUND);
                        blackPolyline = mMap.addPolyline(blackPolylineOptions);

                        mMap.addMarker(new MarkerOptions().position(polyLineList.get(polyLineList.size()-1)));

//                        animateMarkerNew(location, mMarker);
                        //Animator
                        ValueAnimator polyLineAnimator = ValueAnimator.ofInt(0,100);
                        polyLineAnimator.setDuration(2000); //2secundos
                        polyLineAnimator.setInterpolator(new LinearInterpolator());
                        polyLineAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                List<LatLng> points = greyPolyLine.getPoints();
                                int percentValue = (int)valueAnimator.getAnimatedValue();
                                int size = points.size();
                                int newPoints = (int)(size*(percentValue/100.0f));
                                List<LatLng> p =points.subList(0,newPoints);
                                blackPolyline.setPoints(p);
                            }
                        });
                        //add car marker
                        polyLineAnimator.start();
                        marker = mMap.addMarker(new MarkerOptions().position(sydney).flat(true)
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_car)));

                        //car moving
                        handler = new Handler();
                        index= -1;
                        next=1;
                        handler.postDelayed(drawPathRunnable,3000);
                    }catch ( Exception e){
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(Call<String> call, Throwable t) {
                    Toast.makeText(MapsActivity.this,""+t.getMessage(),Toast.LENGTH_SHORT).show();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    private List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }

        return poly;
    }

    private float getBearing(LatLng begin, LatLng end) {
        double lat = Math.abs(begin.latitude - end.latitude);
        double lng = Math.abs(begin.longitude - end.longitude);

        if (begin.latitude < end.latitude && begin.longitude < end.longitude)
            return (float) (Math.toDegrees(Math.atan(lng / lat)));
        else if (begin.latitude >= end.latitude && begin.longitude < end.longitude)
            return (float) ((90 - Math.toDegrees(Math.atan(lng / lat))) + 90);
        else if (begin.latitude >= end.latitude && begin.longitude >= end.longitude)
            return (float) (Math.toDegrees(Math.atan(lng / lat)) + 180);
        else if (begin.latitude < end.latitude && begin.longitude >= end.longitude)
            return (float) ((90 - Math.toDegrees(Math.atan(lng / lat))) + 270);
        return -1;
    }

    private void animateMarkerNew(final Location destination, final Marker marker) {

        if (marker != null) {

            final LatLng startPosition = marker.getPosition();
            final LatLng endPosition = new LatLng(destination.getLatitude(), destination.getLongitude());

            final float startRotation = marker.getRotation();
            final LatLngInterpolatorNew latLngInterpolator = new LatLngInterpolatorNew.LinearFixed();

            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1);
            valueAnimator.setDuration(3000); // duration 3 second
            valueAnimator.setInterpolator(new LinearInterpolator());
            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    try {
                        float v = animation.getAnimatedFraction();
                        LatLng newPosition = latLngInterpolator.interpolate(v, startPosition, endPosition);
                        marker.setPosition(newPosition);
                        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder()
                                .target(newPosition)
                                .zoom(15.5f)
                                .build()));

                        marker.setRotation(getBearing(startPosition, new LatLng(destination.getLatitude(), destination.getLongitude())));
                    } catch (Exception ex) {
                        //I don't care atm..
                    }
                }
            });
            valueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);

                    // if (mMarker != null) {
                    // mMarker.remove();
                    // }
                    // mMarker = googleMap.addMarker(new MarkerOptions().position(endPosition).icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_car)));

                }
            });
            valueAnimator.start();
        }
    }

    private interface LatLngInterpolatorNew {
        LatLng interpolate(float fraction, LatLng a, LatLng b);

        class LinearFixed implements LatLngInterpolatorNew {
            @Override
            public LatLng interpolate(float fraction, LatLng a, LatLng b) {
                double lat = (b.latitude - a.latitude) * fraction + a.latitude;
                double lngDelta = b.longitude - a.longitude;
                // Take the shortest path across the 180th meridian.
                if (Math.abs(lngDelta) > 180) {
                    lngDelta -= Math.signum(lngDelta) * 360;
                }
                double lng = lngDelta * fraction + a.longitude;
                return new LatLng(lat, lng);
            }
        }
    }
}
