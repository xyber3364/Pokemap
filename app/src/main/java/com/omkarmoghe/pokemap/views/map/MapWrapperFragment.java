package com.omkarmoghe.pokemap.views.map;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.TimeUtils;
import android.support.v7.app.AlertDialog;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.omkarmoghe.pokemap.R;
import com.omkarmoghe.pokemap.controllers.MarkerRefreshController;
import com.omkarmoghe.pokemap.controllers.app_preferences.PokemapAppPreferences;
import com.omkarmoghe.pokemap.controllers.app_preferences.PokemapSharedPreferences;
import com.omkarmoghe.pokemap.controllers.map.LocationManager;
import com.omkarmoghe.pokemap.helpers.MapHelper;
import com.omkarmoghe.pokemap.helpers.RemoteImageLoader;
import com.omkarmoghe.pokemap.models.events.CatchablePokemonEvent;
import com.omkarmoghe.pokemap.models.events.ClearMapEvent;
import com.omkarmoghe.pokemap.models.events.GymsEvent;
import com.omkarmoghe.pokemap.models.events.MarkerExpired;
import com.omkarmoghe.pokemap.models.events.MarkerUpdate;
import com.omkarmoghe.pokemap.models.events.PokestopsEvent;
import com.omkarmoghe.pokemap.models.events.SearchInPosition;
import com.omkarmoghe.pokemap.models.map.GymMarkerExtended;
import com.omkarmoghe.pokemap.models.map.PokemonMarkerExtended;
import com.omkarmoghe.pokemap.models.map.PokestopMarkerExtended;
import com.omkarmoghe.pokemap.models.map.SearchParams;
import com.omkarmoghe.pokemap.util.PokemonIdUtils;
import com.pokegoapi.api.map.fort.Pokestop;
import com.pokegoapi.api.map.pokemon.CatchablePokemon;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import POGOProtos.Enums.TeamColorOuterClass;
import POGOProtos.Map.Fort.FortDataOuterClass;

import POGOProtos.Enums.PokemonIdOuterClass;

/**
 * A simple {@link Fragment} subclass.
 * <p>
 * Use the {@link MapWrapperFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MapWrapperFragment extends Fragment implements OnMapReadyCallback,
        GoogleMap.OnMapLongClickListener,
        ActivityCompat.OnRequestPermissionsResultCallback, GoogleMap.OnMarkerClickListener, GoogleMap.OnMapClickListener {

    private static final int LOCATION_PERMISSION_REQUEST = 19;
    private static final String TAG = "MapWrapperFragment";

    private LocationManager locationManager;

    private PokemapAppPreferences mPref;
    private View mView;
    private SupportMapFragment mSupportMapFragment;
    private GoogleMap mGoogleMap;
    private Location mLocation = null;
    private Marker userSelectedPositionMarker = null;
    private PokemonMarkerExtended mSelectedMarker;
    private Location currentCenter = new Location("0,0");
    private Map<String, GymMarkerExtended> gymsList = new HashMap<>();
    Map<Integer, String> gymTeamImageUrls = new HashMap<>();
    String lurePokeStopImageUrl = "http://i.imgur.com/2BI3Cqv.png";
    String pokeStopImageUrl = "http://i.imgur.com/pmLrx3R.png";

    private List<Circle> userSelectedPositionCircles = new ArrayList<>();
    private Map<String, PokemonMarkerExtended> markerList = new HashMap<>();
    private Map<String, PokestopMarkerExtended> pokestopsList = new HashMap<>();

    private Set<PokemonIdOuterClass.PokemonId> showablePokemonIDs = new HashSet<>();

    public static Snackbar pokeSnackbar;
    public static int pokemonFound = 0;
    public static int positionNum = 0;

    public MapWrapperFragment() {

        gymTeamImageUrls.put(TeamColorOuterClass.TeamColor.NEUTRAL_VALUE, "http://i.imgur.com/If3mHMM.png");
        gymTeamImageUrls.put(TeamColorOuterClass.TeamColor.BLUE_VALUE, "http://i.imgur.com/ElM6sqb.png");
        gymTeamImageUrls.put(TeamColorOuterClass.TeamColor.RED_VALUE, "http://i.imgur.com/wO13iJ0.png");
        gymTeamImageUrls.put(TeamColorOuterClass.TeamColor.YELLOW_VALUE, "http://i.imgur.com/F8Jq1dc.png");
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment MapWrapperFragment.
     */
    public static MapWrapperFragment newInstance() {
        MapWrapperFragment fragment = new MapWrapperFragment();
        Bundle args = new Bundle();

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        mPref = new PokemapSharedPreferences(getContext());
        showablePokemonIDs = mPref.getShowablePokemonIDs();
    }

    @Override
    public void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);
        updateMarkers();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        locationManager = LocationManager.getInstance(getContext());
        locationManager.register(new LocationManager.Listener() {
            @Override
            public void onLocationChanged(Location location) {
                if (mLocation == null) {
                    mLocation = location;
                    initMap();
                } else {
                    mLocation = location;
                }
            }

            @Override
            public void onLocationFetchFailed(@Nullable ConnectionResult connectionResult) {
                showLocationFetchFailed();
            }
        });
        // Inflate the layout for this fragment if the view is not null
        if (mView == null)
            mView = inflater.inflate(R.layout.fragment_map_wrapper, container, false);
        else {

        }

        // build the map
        if (mSupportMapFragment == null) {
            mSupportMapFragment = SupportMapFragment.newInstance();
            getChildFragmentManager().beginTransaction().replace(R.id.map, mSupportMapFragment).commit();
            mSupportMapFragment.setRetainInstance(true);
        }

        if (mGoogleMap == null) {
            mSupportMapFragment.getMapAsync(this);
        }

        FloatingActionButton locationFab = (FloatingActionButton) mView.findViewById(R.id.location_fab);
        locationFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mLocation != null && mGoogleMap != null) {
                    mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(mLocation.getLatitude(), mLocation.getLongitude()), 15));
                } else {
                    showLocationFetchFailed();
                }
            }
        });

        mView.findViewById(R.id.closeSuggestions).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mView.findViewById(R.id.layoutSuggestions).setVisibility(View.GONE);
            }
        });

        return mView;
    }

    private void initMap() {
        pokeSnackbar = Snackbar.make(getView(), "", Snackbar.LENGTH_LONG);
        if (mLocation != null && mGoogleMap != null) {
            if (ContextCompat.checkSelfPermission(mView.getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(mView.getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                new AlertDialog.Builder(getActivity())
                        .setTitle(getString(R.string.enable_location_permission_title))
                        .setMessage(getString(R.string.enable_location_permission_message))
                        .setPositiveButton(getString(R.string.button_ok), null)
                        .show();
                return;
            }
            mGoogleMap.setMyLocationEnabled(true);

            LatLng currentLatLngLocation = new LatLng(mLocation.getLatitude(), mLocation.getLongitude());
            mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    currentLatLngLocation, 15));

            //Run the initial scan at the current location reusing the long click function
            SearchInPosition sip = new SearchInPosition();
            sip.setPosition(currentLatLngLocation);
            sip.setSteps(1);
            EventBus.getDefault().post(sip);
        } else {
            showLocationFetchFailed();
        }
    }

    private void clearMarkers() {
        if (mGoogleMap != null) {
            if (markerList != null && !markerList.isEmpty()) {
                for (Iterator<Map.Entry<String, PokemonMarkerExtended>> pokemonIterator = markerList.entrySet().iterator(); pokemonIterator.hasNext(); ) {
                    Map.Entry<String, PokemonMarkerExtended> pokemonEntry = pokemonIterator.next();
                    pokemonEntry.getValue().getMarker().remove();
                    pokemonIterator.remove();
                }
            }
            if (pokestopsList != null && !pokestopsList.isEmpty()) {
                for (Iterator<Map.Entry<String, PokestopMarkerExtended>> pokestopIterator = pokestopsList.entrySet().iterator(); pokestopIterator.hasNext(); ) {
                    Map.Entry<String, PokestopMarkerExtended> pokestopEntry = pokestopIterator.next();
                    pokestopEntry.getValue().getMarker().remove();
                    pokestopIterator.remove();
                }
            }

            if (gymsList != null && !gymsList.isEmpty()) {
                for (Iterator<Map.Entry<String, GymMarkerExtended>> gymIterator = gymsList.entrySet().iterator(); gymIterator.hasNext(); ) {
                    Map.Entry<String, GymMarkerExtended> gymEntry = gymIterator.next();
                    gymEntry.getValue().getMarker().remove();
                    gymIterator.remove();
                }
            }

            if (userSelectedPositionCircles != null && !userSelectedPositionCircles.isEmpty()) {
                for (Circle circle : userSelectedPositionCircles) {
                    circle.remove();
                }
                userSelectedPositionCircles.clear();
            }
        }

    }

    private void updateMarkers() {
        if (mGoogleMap != null) {
            if (markerList != null && !markerList.isEmpty()) {
                for (Iterator<Map.Entry<String, PokemonMarkerExtended>> pokemonIterator = markerList.entrySet().iterator(); pokemonIterator.hasNext(); ) {
                    Map.Entry<String, PokemonMarkerExtended> pokemonEntry = pokemonIterator.next();
                    CatchablePokemon catchablePokemon = pokemonEntry.getValue().getCatchablePokemon();
                    Marker marker = pokemonEntry.getValue().getMarker();

                    if (!showablePokemonIDs.contains(catchablePokemon.getPokemonId())) {
                        marker.remove();
                        pokemonIterator.remove();
                    } else {
                        long millisLeft = catchablePokemon.getExpirationTimestampMs() - System.currentTimeMillis();
                        if (millisLeft < 0) {
                            marker.remove();
                            pokemonIterator.remove();
                        } else {
                            marker.setSnippet(getExpirationBreakdown(millisLeft));
                            if (marker.isInfoWindowShown()) {
                                marker.showInfoWindow();
                            }
                        }
                    }
                }
            }
            if (pokestopsList != null && !pokestopsList.isEmpty() && mPref.getShowPokestops()) {

                for (Iterator<Map.Entry<String, PokestopMarkerExtended>> pokestopIterator = pokestopsList.entrySet().iterator(); pokestopIterator.hasNext(); ) {
                    Map.Entry<String, PokestopMarkerExtended> pokestopEntry = pokestopIterator.next();
                    final Pokestop pokestop = pokestopEntry.getValue().getPokestop();
                    final Marker marker = pokestopEntry.getValue().getMarker();

                    int markerSize = getResources().getDimensionPixelSize(R.dimen.pokestop_marker);

                    RemoteImageLoader.load(
                        pokestop.hasLurePokemon() ? lurePokeStopImageUrl : pokeStopImageUrl,
                        markerSize, markerSize,
                        getActivity(),
                        new RemoteImageLoader.Callback() {
                            @Override
                            public void onFetch(Bitmap bitmap) {

                                BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap);
                                marker.setIcon(bitmapDescriptor);
                                marker.setZIndex(pokestop.hasLurePokemon() ? 1.0f : 0.5f);
                            }
                        }
                    );
                }
            } else if (pokestopsList != null && !pokestopsList.isEmpty() && !mPref.getShowPokestops()) {
                for (Iterator<Map.Entry<String, PokestopMarkerExtended>> pokestopIterator = pokestopsList.entrySet().iterator(); pokestopIterator.hasNext(); ) {
                    Map.Entry<String, PokestopMarkerExtended> pokestopEntry = pokestopIterator.next();
                    Marker marker = pokestopEntry.getValue().getMarker();
                    marker.remove();
                    pokestopIterator.remove();
                }
            }

            if (gymsList != null && !gymsList.isEmpty() && mPref.getShowGyms()) {

                for (Iterator<Map.Entry<String, GymMarkerExtended>> gymIterator = gymsList.entrySet().iterator(); gymIterator.hasNext(); ) {
                    Map.Entry<String, GymMarkerExtended> gymEntry = gymIterator.next();
                    final FortDataOuterClass.FortData gym = gymEntry.getValue().getGym();
                    final Marker marker = gymEntry.getValue().getMarker();

                    int markerSize = getResources().getDimensionPixelSize(R.dimen.gym_marker);

                    RemoteImageLoader.load(
                        gymTeamImageUrls.get(gym.getOwnedByTeam().getNumber()),
                        markerSize, markerSize,
                        getActivity(),
                        new RemoteImageLoader.Callback() {
                            @Override
                            public void onFetch(Bitmap  bitmap) {

                                BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap);
                                marker.setIcon(bitmapDescriptor);
                            }
                        }
                    );
                }
            } else if(gymsList != null && !gymsList.isEmpty() && !mPref.getShowGyms()){
                for (Iterator<Map.Entry<String, GymMarkerExtended>> gymIterator = gymsList.entrySet().iterator(); gymIterator.hasNext(); ) {
                    Map.Entry<String, GymMarkerExtended> gymEntry = gymIterator.next();
                    Marker marker = gymEntry.getValue().getMarker();
                    marker.remove();
                    gymIterator.remove();
                }
            }


            if (!mPref.getShowScannedPlaces() && userSelectedPositionCircles != null && !userSelectedPositionCircles.isEmpty()) {
                for (Circle circle : userSelectedPositionCircles) {
                    circle.remove();
                }
                userSelectedPositionCircles.clear();
            }
        }
    }

    private float getDistanceInMeters(double lat1, double lon1, double lat2, double lon2) {

        Location loc1 = new Location("");
        loc1.setLatitude(lat1);
        loc1.setLongitude(lon1);

        Location loc2 = new Location("");
        loc2.setLatitude(lat2);
        loc2.setLongitude(lon2);

        return loc1.distanceTo(loc2);
    }

    private void setPokestopsMarkers(final PokestopsEvent event) {
        if (mGoogleMap != null) {

            int markerSize = getResources().getDimensionPixelSize(R.dimen.pokestop_marker);
            Collection<Pokestop> pokestops = event.getPokestops();

            if(pokestops != null && mPref.getShowPokestops()) {
                Set<String> markerKeys = pokestopsList.keySet();

                for (final Pokestop pokestop : pokestops) {

                    // radial boxing
                    float distanceFromCenterInMeters = getDistanceInMeters(
                        event.getLatitude(), event.getLongitude(),
                        pokestop.getLatitude(), pokestop.getLongitude()
                    );

                    if (!markerKeys.contains(pokestop.getId()) && distanceFromCenterInMeters <= 370) {

                            RemoteImageLoader.load(
                            pokestop.hasLurePokemon() ? lurePokeStopImageUrl : pokeStopImageUrl,
                            markerSize, markerSize,
                            getActivity(),
                            new RemoteImageLoader.Callback() {
                                @Override
                                public void onFetch(Bitmap bitmap) {

                                    BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap);

                                    Marker marker = mGoogleMap.addMarker(new MarkerOptions()
                                        .position(new LatLng(pokestop.getLatitude(), pokestop.getLongitude()))
                                        .title(getString(R.string.pokestop))
                                        .icon(bitmapDescriptor)
                                        .zIndex(MapHelper.LAYER_POKESTOPS)
                                        .alpha(pokestop.hasLurePokemon() ? 1.0f : 0.5f)
                                        .anchor(0.5f, 0.5f));

                                    //adding pokemons to list to be removed on next search
                                    pokestopsList.put(pokestop.getId(), new PokestopMarkerExtended(pokestop, marker));
                                }
                            }
                        );
                    }
                }
            }
            updateMarkers();

        } else {
            showMapNotInitializedError();
        }
    }

    private void setGymsMarkers(final GymsEvent event){
        if (mGoogleMap != null) {

            int markerSize = getResources().getDimensionPixelSize(R.dimen.gym_marker);
            Collection<FortDataOuterClass.FortData> gyms = event.getGyms();

            if(gyms != null && mPref.getShowGyms()) {

                Set<String> markerKeys = gymsList.keySet();

                for (final FortDataOuterClass.FortData gym : gyms) {

                    // radial boxing
                    float distanceFromCenterInMeters = getDistanceInMeters(
                            event.getLatitude(), event.getLongitude(),
                            gym.getLatitude(), gym.getLongitude()
                    );

                    if (!markerKeys.contains(gym.getId()) && distanceFromCenterInMeters <= 370) {

                        RemoteImageLoader.load(
                            gymTeamImageUrls.get(gym.getOwnedByTeam().getNumber()),
                            markerSize, markerSize,
                            getActivity(),
                            new RemoteImageLoader.Callback() {
                                @Override
                                public void onFetch(Bitmap bitmap) {

                                    BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap);

                                    Marker marker = mGoogleMap.addMarker(new MarkerOptions()
                                        .position(new LatLng(gym.getLatitude(), gym.getLongitude()))
                                        .title(getString(R.string.gym))
                                        .icon(bitmapDescriptor)
                                        .zIndex(MapHelper.LAYER_GYMS)
                                        .anchor(0.5f, 0.5f));

                                    // adding gyms to list to be removed on next search
                                    gymsList.put(gym.getId(), new GymMarkerExtended(gym, marker));
                                }
                            }
                        );
                    }
                }
            }
            updateMarkers();

        } else {
            showMapNotInitializedError();
        }
    }

    private void setPokemonMarkers(final List<CatchablePokemon> pokeList){
        positionNum++;
        int markerSize = getResources().getDimensionPixelSize(R.dimen.pokemon_marker);
        if (mGoogleMap != null) {

            Set<String> markerKeys = markerList.keySet();
            for (final CatchablePokemon poke : pokeList) {

                if(!markerKeys.contains(poke.getSpawnPointId())) {

                    // checking if we need to show this pokemon
                    PokemonIdOuterClass.PokemonId pokemonId = poke.getPokemonId();
                    if (showablePokemonIDs.contains(pokemonId)) {

                        RemoteImageLoader.load(
                            "http://serebii.net/pokemongo/pokemon/"+PokemonIdUtils.getCorrectPokemonImageId(pokemonId.getNumber())+".png",
                            markerSize, markerSize,
                            getActivity(),
                            new RemoteImageLoader.Callback() {
                                @Override
                                public void onFetch(Bitmap bitmap) {

                                    BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap);

                                    //Setting marker since we got image
                                    //int resourceID = getResources().getIdentifier("p" + poke.getPokemonId().getNumber(), "drawable", getActivity().getPackageName());
                                    final Marker marker = mGoogleMap.addMarker(new MarkerOptions()
                                            .position(new LatLng(poke.getLatitude(), poke.getLongitude()))
                                            .title(PokemonIdUtils.getLocalePokemonName(getContext(), poke.getPokemonId().name()))
                                            .icon(bitmapDescriptor)
                                            .zIndex(MapHelper.LAYER_POKEMONS)
                                            .anchor(0.5f, 0.5f));

                                    //adding pokemons to list to be removed on next search
                                    Log.d(TAG, "onFetch: Thread = " + Thread.currentThread());
                                    PokemonMarkerExtended markerExtended = new PokemonMarkerExtended(poke, marker);
                                    markerList.put(poke.getSpawnPointId(), markerExtended);
                                    MarkerRefreshController.getInstance().postMarker(markerExtended);

                                }
                            }
                        );

                        //Increase founded pokemon counter
                        pokemonFound++;
                    }
                }
            }
            if (getView() != null) {
                if (positionNum != LOCATION_PERMISSION_REQUEST) {
                    pokeSnackbar.setText(getString(R.string.toast_still_searching, pokemonFound));
                    pokeSnackbar.show();

                } else {
                    String text = pokemonFound > 0 ? getString(R.string.pokemon_found_new, pokemonFound) : getString(R.string.pokemon_found_none);
                    pokeSnackbar.setText(text);
                    pokeSnackbar.show();
                }
            }
            updateMarkers();
        } else {
            showMapNotInitializedError();
        }
    }

    private void removeExpiredMarker(final PokemonMarkerExtended pokemonMarker) {
        Property<Marker, Float> property = Property.of(Marker.class, Float.class, "alpha");
        ObjectAnimator animator = ObjectAnimator.ofFloat(pokemonMarker.getMarker(), property, 1f, 0f);
        animator.setDuration(400);
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                pokemonMarker.getMarker().remove();
                markerList.remove(pokemonMarker);
            }

            @Override
            public void onAnimationCancel(Animator animator) {
                pokemonMarker.getMarker().remove();
                markerList.remove(pokemonMarker);
            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        animator.start();
    }


    private void showMapNotInitializedError() {
        if(getView() != null){
            Snackbar.make(getView(), getString(R.string.toast_map_not_initialized), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void showLocationFetchFailed() {
        if(getView() != null){
            Snackbar.make(getView(),getString(R.string.toast_no_location), Snackbar.LENGTH_SHORT).show();
        }
    }

    private String getExpirationBreakdown(long millis) {
        if(millis < 0) {
            return getString(R.string.pokemon_expired);
        }

        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        return getString(R.string.expiring_in, minutes, seconds);
    }

    /**
     * Called whenever a CatchablePokemonEvent is posted to the bus. Posted when new catchable pokemon are found.
     *
     * @param event The event information
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(CatchablePokemonEvent event) {
        setPokemonMarkers(event.getCatchablePokemon());
        drawCatchedPokemonCircle(event.getLat(), event.getLongitude());
        if(positionNum == 1) {
            currentCenter.setLatitude(event.getLat());
            currentCenter.setLongitude(event.getLongitude());
        }
        if(positionNum == LOCATION_PERMISSION_REQUEST)
        {
            if (mGoogleMap != null) {

                if (mPref.getShowScannedPlaces()) {

                    double radiusInMeters = ((SearchParams.DEFAULT_RADIUS *3)-(27*3))*2;//((3*100)-(27*3))*2
                    int strokeColor = 0x4400CCFF; // outline
                    int shadeColor = 0x4400CCFF; // fill

                    CircleOptions circleOptions = new CircleOptions().center(new LatLng(currentCenter.getLatitude(), currentCenter.getLongitude())).radius(radiusInMeters).fillColor(shadeColor).strokeColor(strokeColor).strokeWidth(8);
                    clearCatchedPokemonCircle();
                    userSelectedPositionCircles.add(mGoogleMap.addCircle(circleOptions));
                }
            }
        }
    }

    /**
     * Called whenever a ClearMapEvent is posted to the bus. Posted when the user wants to clear map of any pokemon or marker.
     *
     * @param event The event information
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ClearMapEvent event) {
        clearMarkers();
        MarkerRefreshController.getInstance().clear();
    }

    /**
     * Called whenever a PokestopsEvent is posted to the bus. Posted when new pokestops are found.
     *
     * @param event The event information
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PokestopsEvent event) {
        setPokestopsMarkers(event);
    }

    /**
     * Called whenever a PokestopsEvent is posted to the bus. Posted when new gyms are found.
     *
     * @param event The event information
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(GymsEvent event) {

        setGymsMarkers(event);
    }

    /**
     * Called whenever a MarkerUpdate is posted to the bus. Posted by {@link MarkerRefreshController} when
     * expired markers need to be removed.
     *
     * @param event
     */

     @Subscribe(threadMode = ThreadMode.MAIN)
     public void onEvent(MarkerExpired event){
         removeExpiredMarker(event.getData());
     }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(MarkerUpdate event){
        Marker marker = mSelectedMarker.getMarker();
        if(marker.isInfoWindowShown()){
            long time = mSelectedMarker.getCatchablePokemon().getExpirationTimestampMs()
                    - System.currentTimeMillis();
            marker.setSnippet(getExpirationBreakdown(time));
            marker.showInfoWindow();
        }
    }



    private void clearCatchedPokemonCircle() {

        //Check and eventually remove old marker
        if (userSelectedPositionMarker != null && userSelectedPositionCircles != null) {
            userSelectedPositionMarker.remove();
            for (Circle circle : userSelectedPositionCircles) {
                circle.remove();
            }
            userSelectedPositionCircles.clear();
        }
    }

    private void drawCatchedPokemonCircle(double latitude, double longitude) {

        if (mGoogleMap != null) {

            if (mPref.getShowScannedPlaces()) {

                double radiusInMeters = 100.0;
                int shadeColor = 0x2200CCFF; // fill
                CircleOptions circleOptions = new CircleOptions()
                        .center(new LatLng(latitude, longitude))
                        .radius(radiusInMeters).fillColor(shadeColor)
                        .strokeColor(Color.TRANSPARENT)
                        .zIndex(MapHelper.LAYER_SCANNED_LOCATIONS);
                userSelectedPositionCircles.add(mGoogleMap.addCircle(circleOptions));
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mGoogleMap = googleMap;

        UiSettings settings = mGoogleMap.getUiSettings();
        settings.setCompassEnabled(true);
        settings.setTiltGesturesEnabled(true);
        settings.setMyLocationButtonEnabled(false);
        //Handle long click
        mGoogleMap.setOnMapLongClickListener(this);
        mGoogleMap.setOnMapClickListener(this);
        mGoogleMap.setOnMarkerClickListener(this);
        //Disable for now coz is under FAB
        settings.setMapToolbarEnabled(false);
    }

    @Override
    public void onMapLongClick(LatLng position) {

        clearCatchedPokemonCircle();

        //Draw user position marker with circle
        drawMarker(position);

        //Sending event to MainActivity
        SearchInPosition sip = new SearchInPosition();
        sip.setPosition(position);
        sip.setSteps(mPref.getSteps());
        EventBus.getDefault().post(sip);

        mView.findViewById(R.id.layoutSuggestions).setVisibility(View.GONE);
    }

    private void drawMarker(LatLng position){
        if (mGoogleMap != null) {

            userSelectedPositionMarker = mGoogleMap.addMarker(new MarkerOptions()
                    .position(position)
                    .title(getString(R.string.position_picked))
                    .icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(getContext().getResources(),
                            R.drawable.ic_my_location_white_24dp)))
                    .zIndex(MapHelper.LAYER_MY_SEARCH)
                    .anchor(0.5f, 0.5f));
        } else {
            showMapNotInitializedError();
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        marker.showInfoWindow();
        for (Map.Entry<String, PokemonMarkerExtended> pm : markerList.entrySet()) {
            if(pm.getValue().getMarker().equals(marker)){
                mSelectedMarker = pm.getValue();
                long duration = mSelectedMarker.getCatchablePokemon().getExpirationTimestampMs()
                        - System.currentTimeMillis();
                MarkerRefreshController.getInstance().startTimer(duration);
                break;
            }
        }
        return true;
    }

    @Override
    public void onMapClick(LatLng latLng) {
        mSelectedMarker = null;
        MarkerRefreshController.getInstance().stopTimer();
    }
}