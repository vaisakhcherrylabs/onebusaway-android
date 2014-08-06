/*
 * Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.joulespersecond.seattlebusbot.map.googlemapsv2;

import android.app.Activity;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.seattlebusbot.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StopOverlay implements GoogleMap.OnMarkerClickListener {
    private static final String TAG = "StopOverlay";

    private GoogleMap mMap;

    private StopContainer mStopContainer;

    private final Activity mActivity;

    private static final String NORTH = "N";
    private static final String NORTH_WEST = "NW";
    private static final String WEST = "W";
    private static final String SOUTH_WEST = "SW";
    private static final String SOUTH = "S";
    private static final String SOUTH_EAST = "SE";
    private static final String EAST = "E";
    private static final String NORTH_EAST = "NE";
    private static final int NUM_DIRECTIONS = 9; // 8 directions + undirected mStops

    private static BitmapDescriptor[] bus_stop_icons = new BitmapDescriptor[NUM_DIRECTIONS];

    public StopOverlay(Activity activity, GoogleMap map) {
        mActivity = activity;
        mMap = map;
        loadIcons();
        mMap.setOnMarkerClickListener(this);
    }

    public synchronized void setStops(List<ObaStop> stops) {
        if (mStopContainer == null) {
            mStopContainer = new StopContainer();
        }
        populate(stops);
    }

    private void populate(List<ObaStop> stops) {
        mStopContainer.populate(stops);
    }

    public synchronized int size() {
        if (mStopContainer != null) {
            return mStopContainer.size();
        } else {
            return 0;
        }
    }

    /**
     * Clears any stop markers from the map
     */
    public synchronized void clear() {
        if (mStopContainer != null) {
            mStopContainer.clear();
            mStopContainer = null;
        }
    }

    /**
     * Cache the BitmapDescriptors that hold the images used for icons
     */
    private static final void loadIcons() {
        bus_stop_icons[0] = BitmapDescriptorFactory.fromResource(R.drawable.bus_stop_n);
        bus_stop_icons[1] = BitmapDescriptorFactory.fromResource(R.drawable.bus_stop_nw);
        bus_stop_icons[2] = BitmapDescriptorFactory.fromResource(R.drawable.bus_stop_w);
        bus_stop_icons[3] = BitmapDescriptorFactory.fromResource(R.drawable.bus_stop_sw);
        bus_stop_icons[4] = BitmapDescriptorFactory.fromResource(R.drawable.bus_stop_s);
        bus_stop_icons[5] = BitmapDescriptorFactory.fromResource(R.drawable.bus_stop_se);
        bus_stop_icons[6] = BitmapDescriptorFactory.fromResource(R.drawable.bus_stop_e);
        bus_stop_icons[7] = BitmapDescriptorFactory.fromResource(R.drawable.bus_stop_ne);
        bus_stop_icons[8] = BitmapDescriptorFactory.fromResource(R.drawable.bus_stop_u);
    }

    /**
     * Returns the BitMapDescriptor for a particular bus stop icon, based on the stop direction
     *
     * @param direction Bus stop direction, obtained from ObaStop.getDirection() and defined in
     *                  contants in this class
     * @return BitmapDescriptor for the bus stop icon that should be used for that direction
     */
    private static BitmapDescriptor getBitmapDescriptorForBusStopDirection(String direction) {
        if (direction.equals(NORTH)) {
            return bus_stop_icons[0];
        } else if (direction.equals(NORTH_WEST)) {
            return bus_stop_icons[1];
        } else if (direction.equals(WEST)) {
            return bus_stop_icons[2];
        } else if (direction.equals(SOUTH_WEST)) {
            return bus_stop_icons[3];
        } else if (direction.equals(SOUTH)) {
            return bus_stop_icons[4];
        } else if (direction.equals(SOUTH_EAST)) {
            return bus_stop_icons[5];
        } else if (direction.equals(EAST)) {
            return bus_stop_icons[6];
        } else if (direction.equals(NORTH_EAST)) {
            return bus_stop_icons[7];
        } else {
            return bus_stop_icons[8];
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        long startTime = Long.MAX_VALUE, endTime = Long.MAX_VALUE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            startTime = SystemClock.elapsedRealtimeNanos();
        }
        
        ObaStop stop = mStopContainer.getStopFromMarker(marker);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            endTime = SystemClock.elapsedRealtimeNanos();
            Log.d(TAG, "Elapsed Time: " + (endTime - startTime) / 1E3 + "ms");
        }
        Log.d(TAG, "Stop: " + stop.getName());
        Toast.makeText(mActivity, "Stop: " + stop.getName(), Toast.LENGTH_SHORT).show();
        //ArrivalsListActivity.start(mActivity, stop);

        /**
         * TODO - if the tap on the marker causes the map to move, when zoomed out if the marker count
         * threshold is exceeded then the tapped marker could be deleted - need to cache and re-add
         * marker that was last tapped if the main marker cache is cleared
         */
        return false;
    }

    /**
     * Data structures to track what stops/sarkers are currently shown on the map
     */
    class StopContainer {

        /**
         * Stops-for-location REST API endpoint returns 100 markers per call by default
         * (see http://goo.gl/tzvrLb), so we'll support showing max results of around 2 calls before
         * we completely clear the map and start over.  Note that this is a fuzzy max, since we don't
         * want to clear the overlay in the middle of processing an API response and remove markers in
         * the current view
         */
        private static final int FUZZY_MAX_MARKER_COUNT = 200;

        /**
         * A cached set of markers currently shown on the map, up to roughly
         * FUZZY_MAX_MARKER_COUNT in size.  This is needed to add/remove markers from the map.
         */
        private HashMap<String, Marker> mMarkers;

        /**
         * A cached set of ObaStops that are currently shown on the map, up to roughly
         * FUZZY_MAX_MARKER_COUNT in size.  Since onMarkerClick() provides a marker, we need a
         * mapping of that marker to the ObaStop.
         */
        private HashMap<Marker, ObaStop> mStops;

        StopContainer() {
            mMarkers = new HashMap<String, Marker>();
            mStops = new HashMap<Marker, ObaStop>();
        }

        synchronized void populate(List<ObaStop> stops) {
            if (mMarkers.size() >= FUZZY_MAX_MARKER_COUNT) {
                // We've exceed our max, so clear the current marker cache and start over
                removeMarkersFromMap();
                mMarkers.clear();
                mStops.clear();
                Log.d(TAG, "Exceed max marker cache of " + FUZZY_MAX_MARKER_COUNT + ", clearing cache");
            }

            int count = 0;

            for (ObaStop stop : stops) {
                if (!mMarkers.containsKey(stop.getId())) {
                    addMarkerToMap(stop);
                    count++;
                }
            }

            Log.d(TAG, "Added " + count + " markers, total markers = " + mMarkers.size());
        }

        /**
         * Places a marker on the map for this stop, and adds it to our marker HashMap
         *
         * @param stop ObaStop that should be shown on the map
         */
        void addMarkerToMap(ObaStop stop) {
            Marker m = mMap.addMarker(new MarkerOptions()
                            .position(MapHelpV2.makeLatLng(stop.getLocation()))
                            .icon(getBitmapDescriptorForBusStopDirection(stop.getDirection()))
                            .flat(true)
            );
            mMarkers.put(stop.getId(), m);
            mStops.put(m, stop);
        }

        ObaStop getStopFromMarker(Marker marker) {
            return mStops.get(marker);
        }

        /**
         * Remove all markers from the Google Map
         */
        void removeMarkersFromMap() {
            for (Map.Entry<String, Marker> entry : mMarkers.entrySet()) {
                entry.getValue().remove();
            }
        }

        /**
         * Clears any stop markers from the map
         */
        synchronized void clear() {
            if (mMarkers != null) {
                // Clear all markers from the map
                removeMarkersFromMap();

                // Clear the data structures
                mMarkers.clear();
                mMarkers = null;
            }
            if (mStops != null) {
                mStops.clear();
                mStops = null;
            }
        }

        synchronized int size() {
            return mMarkers.size();
        }
    }

//    @Override
//    public boolean onTrackballEvent(MotionEvent event, MapView view) {
//        final int action = event.getAction();
//        OverlayItem next = null;
//        //Log.d(TAG, "MotionEvent: " + event);
//
//        if (action == MotionEvent.ACTION_MOVE) {
//            final float xDiff = event.getX();
//            final float yDiff = event.getY();
//            // Up
//            if (yDiff <= -1) {
//                next = findNext(getFocus(), true, true);
//            }
//            // Down
//            else if (yDiff >= 1) {
//                next = findNext(getFocus(), true, false);
//            }
//            // Right
//            else if (xDiff >= 1) {
//                next = findNext(getFocus(), false, true);
//            }
//            // Left
//            else if (xDiff <= -1) {
//                next = findNext(getFocus(), false, false);
//            }
//            if (next != null) {
//                setFocus(next);
//                view.postInvalidate();
//            }
//        } else if (action == MotionEvent.ACTION_UP) {
//            final OverlayItem focus = getFocus();
//            if (focus != null) {
//                ArrivalsListActivity.start(mActivity, ((StopOverlayItem) focus).getStop());
//            }
//        }
//        return true;
//    }

//    @Override
//    public boolean onKeyDown(int keyCode, KeyEvent event, MapView view) {
//        //Log.d(TAG, "KeyEvent: " + event);
//        OverlayItem next = null;
//        switch (keyCode) {
//            case KeyEvent.KEYCODE_DPAD_UP:
//                next = findNext(getFocus(), true, true);
//                break;
//            case KeyEvent.KEYCODE_DPAD_DOWN:
//                next = findNext(getFocus(), true, false);
//                break;
//            case KeyEvent.KEYCODE_DPAD_RIGHT:
//                next = findNext(getFocus(), false, true);
//                break;
//            case KeyEvent.KEYCODE_DPAD_LEFT:
//                next = findNext(getFocus(), false, false);
//                break;
//            case KeyEvent.KEYCODE_DPAD_CENTER:
//                final OverlayItem focus = getFocus();
//                if (focus != null) {
//                    ArrivalsListActivity.start(mActivity, ((StopOverlayItem) focus).getStop());
//                }
//                break;
//            default:
//                return false;
//        }
//        if (next != null) {
//            setFocus(next);
//            view.postInvalidate();
//        }
//        return true;
//    }

//    boolean setFocusById(String id) {
//        final int size = size();
//        for (int i = 0; i < size; ++i) {
//            StopOverlayItem item = (StopOverlayItem) getItem(i);
//            if (id.equals(item.getStop().getId())) {
//                setFocus(item);
//                return true;
//            }
//        }
//        return false;
//    }
//
//    String getFocusedId() {
//        final OverlayItem focus = getFocus();
//        if (focus != null) {
//            return ((StopOverlayItem) focus).getStop().getId();
//        }
//        return null;
//    }

//    @Override
//    protected boolean onTap(int index) {
//        final OverlayItem item = getItem(index);
//        if (item.equals(getFocus())) {
//            ObaStop stop = mStops.get(index);
//            ArrivalsListActivity.start(mActivity, stop);
//        } else {
//            setFocus(item);
//            // fix odd behavior where previously selected item is not re-highlighted
//            setLastFocusedIndex(-1);
//        }
//        return true;
//    }

    // The find next routines find the closest item along the specified axis.

//    OverlayItem findNext(OverlayItem initial, boolean lat, boolean positive) {
//        if (initial == null) {
//            return null;
//        }
//        final int size = size();
//        final GeoPoint initialPoint = initial.getPoint();
//        OverlayItem min = initial;
//        int minDist = Integer.MAX_VALUE;
//
//        for (int i = 0; i < size; ++i) {
//            OverlayItem item = getItem(i);
//            GeoPoint point = item.getPoint();
//            final int distX = point.getLongitudeE6() - initialPoint.getLongitudeE6();
//            final int distY = point.getLatitudeE6() - initialPoint.getLatitudeE6();
//
//            // We have to eliminate anything that's going in the wrong direction,
//            // or doesn't change in the correct axis (including the initial point)
//            if (lat) {
//                if (positive) {
//                    // Distance must be positive.
//                    if (distY <= 0) {
//                        continue;
//                    }
//                }
//                // Distance must to be negative.
//                else if (distY >= 0) {
//                    continue;
//                }
//            } else {
//                if (positive) {
//                    // Distance must be positive
//                    if (distX <= 0) {
//                        continue;
//                    }
//                }
//                // Distance must be negative
//                else if (distX >= 0) {
//                    continue;
//                }
//            }
//
//            final int distSq = distX * distX + distY * distY;
//
//            if (distSq < minDist) {
//                min = item;
//                minDist = distSq;
//            }
//        }
//        return min;
//    }
}
