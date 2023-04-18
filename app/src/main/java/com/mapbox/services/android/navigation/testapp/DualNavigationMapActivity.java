package com.mapbox.services.android.navigation.testapp;

import static com.mapbox.services.android.navigation.testapp.NavigationSettings.ACCESS_TOKEN;
import static com.mapbox.services.android.navigation.testapp.NavigationSettings.STYLE_URL;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.transition.TransitionManager;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.engine.LocationEngine;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.services.android.navigation.testapp.R;
import com.mapbox.services.android.navigation.ui.v5.NavigationView;
import com.mapbox.services.android.navigation.ui.v5.NavigationViewOptions;
import com.mapbox.services.android.navigation.ui.v5.OnNavigationReadyCallback;
import com.mapbox.services.android.navigation.ui.v5.listeners.NavigationListener;
import com.mapbox.services.android.navigation.ui.v5.map.NavigationMapboxMap;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.ui.v5.route.OnRouteSelectionChangeListener;
import com.mapbox.services.android.navigation.v5.instruction.Instruction;
import com.mapbox.services.android.navigation.v5.location.replay.ReplayRouteLocationEngine;
import com.mapbox.services.android.navigation.v5.milestone.RouteMilestone;
import com.mapbox.services.android.navigation.v5.milestone.Trigger;
import com.mapbox.services.android.navigation.v5.milestone.TriggerProperty;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigationOptions;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;

import java.util.Objects;
import java.util.Timer;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

public class DualNavigationMapActivity extends AppCompatActivity implements OnNavigationReadyCallback, ProgressChangeListener,
        NavigationListener, Callback<DirectionsResponse>, OnMapReadyCallback, MapboxMap.OnMapClickListener, MapboxMap.OnMapLongClickListener, OnRouteSelectionChangeListener
{

    private static final int CAMERA_ANIMATION_DURATION = 1000;
    private static final int DEFAULT_CAMERA_ZOOM = 16;
    private ConstraintLayout dualNavigationMap;
    private NavigationView navigationView;
    private MapView mapView;
    private ProgressBar loading;
    private FloatingActionButton launchNavigationFab;
    private Point origin = Point.fromLngLat(-122.423579, 37.761689);
    private Point destination = Point.fromLngLat(-122.426183, 37.760872);
    private DirectionsRoute route;
    private boolean isNavigationRunning;
    //    private LocationLayerPlugin locationLayer;
//    private LocationEngine locationEngine;
    private NavigationMapRoute mapRoute;
    private MapboxMap mapboxMap;
    private Marker currentMarker;
    private boolean locationFound;
    private ConstraintSet navigationMapConstraint;
    private ConstraintSet navigationMapExpandedConstraint;
    private boolean[] constraintChanged;

    private LocationComponent locationComponent;
    private NavigationMapRoute navigationMapRoute;
    private ReplayRouteLocationEngine locationEngine = new ReplayRouteLocationEngine();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dual_navigation_map);
        initializeViews(savedInstanceState);
        navigationView.initialize(this);
        navigationMapConstraint = new ConstraintSet();
        navigationMapConstraint.clone(dualNavigationMap);
        navigationMapExpandedConstraint = new ConstraintSet();
        navigationMapExpandedConstraint.clone(this, R.layout.activity_dual_navigation_expand_map);

        constraintChanged = new boolean[] {false};
        launchNavigationFab.setOnClickListener(v -> {
            expandCollapse();
            launchNavigation();
        });
    }

    @SuppressLint("MissingInflatedId")
    private void initializeViews(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_dual_navigation_map);
        dualNavigationMap = findViewById(R.id.dualNavigationMap);
        mapView = findViewById(R.id.mapView);
        navigationView = findViewById(R.id.navigationView);
        loading = findViewById(R.id.loading);
        launchNavigationFab = findViewById(R.id.launchNavigation);
        navigationView.onCreate(savedInstanceState);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    private void expandCollapse() {
        TransitionManager.beginDelayedTransition(dualNavigationMap);
        ConstraintSet constraint;
        if (constraintChanged[0]) {
            constraint = navigationMapConstraint;
        } else {
            constraint = navigationMapExpandedConstraint;
        }
        constraint.applyTo(dualNavigationMap);
        constraintChanged[0] = !constraintChanged[0];
    }

    private void launchNavigation() {
        launchNavigationFab.hide();
        navigationView.setVisibility(View.VISIBLE);
        NavigationMapboxMap navigationMapboxMap = navigationView.retrieveNavigationMapboxMap();
        NavigationViewOptions.Builder options = NavigationViewOptions.builder()
                .navigationListener(this)
                .directionsRoute(route);
        MapboxNavigationOptions options2 = MapboxNavigationOptions.builder()
//                .accessToken(MAPBOX_ACCESS_TOKEN)
                .build();
        MapboxNavigation mapboxNavigation = new MapboxNavigation(this);
        mapboxNavigation.addMilestone(new RouteMilestone.Builder()
                .setIdentifier(1001)
                .setInstruction(new BeginRouteInstruction())
                .setTrigger(
                        Trigger.all(
                                Trigger.lt(TriggerProperty.STEP_INDEX, 3),
                                Trigger.gt(TriggerProperty.STEP_DISTANCE_TOTAL_METERS, 200),
                                Trigger.gte(TriggerProperty.STEP_DISTANCE_TRAVELED_METERS, 75)
                        )
                ).build());
        locationEngine.assign(route);
        mapboxNavigation.setLocationEngine(locationEngine);
        assert navigationMapboxMap != null;
        navigationMapboxMap.addProgressChangeListener(mapboxNavigation);
        navigationView.startNavigation(options.build());
    }

    private static class BeginRouteInstruction extends Instruction {

        @Override
        public String buildInstruction(RouteProgress routeProgress) {
            return "Have a safe trip!";
        }
    }

    private boolean validRouteResponse(Response<DirectionsResponse> response) {
        return response.body() != null && !response.body().routes().isEmpty();
    }


    private void updateLoadingTo(boolean isVisible) {
        if (isVisible) {
            loading.setVisibility(View.VISIBLE);
        } else {
            loading.setVisibility(View.INVISIBLE);
        }
    }

    private void fetchRoute(Point origin, Point destination) {
        NavigationRoute builder = NavigationRoute.builder(this)
                .accessToken(ACCESS_TOKEN)
                .origin(origin)
                .destination(destination)
                .alternatives(true)
                .build();
        builder.getRoute(this);
    }

    private void initMapRoute() {
        mapRoute = new NavigationMapRoute(mapView, mapboxMap);
        mapRoute.setOnRouteSelectionChangeListener(this);
//        mapRoute.addProgressChangeListener(new MapboxNavigation(this));
    }

//    private void initLocationLayer() {
//        locationLayer = new LocationLayerPlugin(mapView, mapboxMap, locationEngine);
//        locationLayer.setRenderMode(RenderMode.COMPASS);
//    }

    private void initLocationEngine() {
//        locationEngine = new LocationEngine
//        locationEngine = new LocationEngineProvider(this).obtainBestLocationEngineAvailable();
//        locationEngine.setPriority(HIGH_ACCURACY);
//        locationEngine.setInterval(0);
//        locationEngine.setFastestInterval(1000);
//        locationEngine.addLocationEngineListener(this);
//        locationEngine.activate();

//        if (locationEngine.getLastLocation() != null) {
//            Location lastLocation = locationEngine.getLastLocation();
//            onLocationChanged(lastLocation);
//            origin = Point.fromLngLat(lastLocation.getLongitude(), lastLocation.getLatitude());
//        }
    }

    private void setCurrentMarkerPosition(LatLng position) {
        if (position != null) {
            if (currentMarker == null) {
//                MarkerViewOptions markerViewOptions = new MarkerViewOptions()
//                        .position(position);
//                currentMarker = mapboxMap.addMarker(markerViewOptions);
            } else {
                currentMarker.setPosition(position);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        navigationView.onStart();
        mapView.onStart();
//        if (locationLayer != null) {
//            locationLayer.onStart();
//        }
    }

    @Override
    public void onResume() {
        super.onResume();
        navigationView.onResume();
        mapView.onResume();
        if (locationEngine != null) {
//            locationEngine.addLocationEngineListener(this);
//            if (!locationEngine.isConnected()) {
//                locationEngine.activate();
//            }
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        navigationView.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onBackPressed() {
        if (!navigationView.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        navigationView.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        navigationView.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onPause() {
        super.onPause();
        navigationView.onPause();
        mapView.onPause();
        if (locationEngine != null) {
//            locationEngine.removeLocationEngineListener(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        navigationView.onStop();
        mapView.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        navigationView.onDestroy();
        mapView.onDestroy();
        if (locationEngine != null) {
//            locationEngine.removeLocationUpdates(this);
//            locationEngine.deactivate();
        }
    }

    @Override
    public boolean onMapLongClick(@NonNull LatLng point) {
        destination = Point.fromLngLat(point.getLongitude(), point.getLatitude());
        updateLoadingTo(true);
        setCurrentMarkerPosition(point);
        if (origin != null) {
            fetchRoute(origin, destination);
        }
        return true;
    }

    @Override
    public void onMapReady(@NonNull MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;
        mapboxMap.setStyle(new Style.Builder().fromUri(STYLE_URL), new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                enableLocationComponent(style);
                initMapRoute();
                fetchRoute(origin, destination);
            }
        });
        this.mapboxMap.addOnMapClickListener(this);
    }

    @SuppressLint("MissingPermission")
    private void enableLocationComponent(Style style) {
        // Get an instance of the component
        locationComponent = mapboxMap.getLocationComponent();

        if (locationComponent != null) {
            // Activate with a built LocationComponentActivationOptions object
            locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(this, style).build()
            );

            // Enable to make component visible
            locationComponent.setLocationComponentEnabled(true);

            // Set the component's camera mode
            locationComponent.setCameraMode(CameraMode.TRACKING_GPS_NORTH);

            // Set the component's render mode
            locationComponent.setRenderMode(RenderMode.GPS);

            locationComponent.setLocationEngine(locationEngine);
        }
    }

    @Override
    public void onNavigationReady(boolean isRunning) {
        isNavigationRunning = isRunning;
    }

    @Override
    public void onCancelNavigation() {
        navigationView.stopNavigation();
        expandCollapse();
    }

    @Override
    public void onNavigationFinished() {

    }

    @Override
    public void onNavigationRunning() {

    }

    @Override
    public void onNewPrimaryRouteSelected(DirectionsRoute directionsRoute) {
        route = directionsRoute;
    }

    @Override
    public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
        if (validRouteResponse(response)) {
            updateLoadingTo(false);
            launchNavigationFab.show();
            route = response.body().routes().get(0);
            mapRoute.addRoutes(response.body().routes());
            if (isNavigationRunning) {
                launchNavigation();
            }
        }
    }

    @Override
    public void onFailure(Call<DirectionsResponse> call, Throwable t) {

    }

    @Override
    public boolean onMapClick(@NonNull LatLng point) {
        destination = Point.fromLngLat(point.getLongitude(), point.getLatitude());
        updateLoadingTo(true);
        setCurrentMarkerPosition(point);
        if (origin != null) {
            fetchRoute(origin, destination);
        }
        return false;
    }

    @Override
    public void onProgressChange(Location location, RouteProgress routeProgress) {
        origin = Point.fromLngLat(location.getLongitude(), location.getLatitude());
        fetchRoute(origin, destination);
    }
}