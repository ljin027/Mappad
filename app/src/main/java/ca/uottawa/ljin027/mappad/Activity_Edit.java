package ca.uottawa.ljin027.mappad;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

/**
 * This class is implemented for CSI5175 Assignment 2.
 * User uses this activity to edit note. The title is typed in and the location is selected by
 * clicking the update button. When being clicked, the current location of marker in the Google Maps
 * Fragment will be read. The modified contents are sent back to the List Activity when user click
 * the done button in the menu or the back button of the on-screen buttons. The activity also saves
 * the notes when the user switches out of it, in which case the List Activity does not have a
 * chance to run.
 * The note content is not needed for the bonus of the assignment. But the code is still kept.
 *
 * This is the URLs of Android activity life circle:
 * http://developer.android.com/training/basics/activity-lifecycle/starting.html
 *
 * @author      Ling Jin
 * @version     1.0
 * @since       04/03/2015
 */
public class Activity_Edit extends ActionBarActivity implements OnMapReadyCallback {
    /**
     * Debugging String constant
     */
    private final String TAG = "<<<<< Activity Edit >>>>>";
    /**
     * Views references
     */
    private EditText mView_Title = null;
    //private EditText mView_Content = null;
    private TextView mView_Latitude = null;
    private TextView mView_Longitude = null;
    private TextView mView_RefLatitude = null;
    private TextView mView_RefLongitude = null;
    private TextView mView_GoogleHint = null;
    /**
     * Indicator for whether the Activity is killed as the result of pressing the menu button or the
     * back button. If not, save the note on internal storage and try to upload it.
     */
    private boolean mControlledFinish = false;
    /**
     * We ignore the camera change message for the first time.
     * The text color of the latitude and longitude views will changed if the camera focus is
     * changed.
     */
    private boolean mFirstCameraChange = true;
    /**
     * Indicator of the supporting of the location service
     */
    private boolean mGoogleServiceAvailable = true;
    /**
     * Data of the current note
     */
    private int mPosition;
    private String mTitle;
    private final String  mContent = "";
    private double mLatitude;
    private double mLongitude;
    /**
     * The referential location
     */
    private double mNewLatitude;
    private double mNewLongitude;

    /**
     * Initialize views, button clicking listener and Google Maps service
     * @param savedInstanceState saved state, do not need to be processed as rotation forbidden
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize views
        setContentView(R.layout.activity_edit);
        // Set the action bar style
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setTitle(R.string.edit_title);
        actionBar.setIcon(R.drawable.ic_pad);
        // Store the view references
        mView_Title = (EditText) findViewById(R.id.note_title);
        //mView_Content = (EditText) findViewById(R.id.note_content);
        mView_Latitude = (TextView) findViewById(R.id.textView_Latitude);
        mView_Longitude = (TextView) findViewById(R.id.textView_Longitude);
        mView_RefLatitude = (TextView) findViewById(R.id.textView_RefLatitude);
        mView_RefLongitude = (TextView) findViewById(R.id.textView_RefLongitude);
        mView_GoogleHint = (TextView) findViewById(R.id.textView_GoogleHint);
        // Press the button to read the location from the map marker
        // The location stored in the note will not change when the user is browsing the map
        ImageButton mButton_Update = (ImageButton) findViewById(R.id.button_Update);
        mButton_Update.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                mLatitude = mNewLatitude;
                mLongitude = mNewLongitude;
                mView_Latitude.setTextColor(getResources().getColor(R.color.black));
                mView_Longitude.setTextColor(getResources().getColor(R.color.black));
                fillCoordinates();
            }
        });
        // Start the Google Maps service if the Google Play services are supported
        detectGoogleService();
        if(mGoogleServiceAvailable) {
            FragmentManager fragmentManager = getFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            MapFragment mapFragment = new MapFragment();
            fragmentTransaction.add(R.id.locating, mapFragment);
            fragmentTransaction.commit();
            mapFragment.getMapAsync(this);
        }
        // Read bundle and initialize data in views
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mPosition = extras.getInt(NoteManager.EXTRA_INDEX);
            mTitle = extras.getString(NoteManager.EXTRA_TITLE);
            //mContent = extras.getString(NoteManager.EXTRA_CONTENT);
            mLatitude = extras.getDouble(NoteManager.EXTRA_LATITUDE);
            mLongitude = extras.getDouble(NoteManager.EXTRA_LONGITUDE);
        } else {
            // Should not happen
            Log.d(TAG, "Receive a null Bundle, please check!");
        }
        mNewLatitude = mLatitude;
        mNewLongitude = mLongitude;
        if(mPosition == NoteManager.NEW_NODE_POSITION) {
            Log.d(TAG, "Activity received wrong intent, notes Passing Failure !");
        } else {
            fillViews();
        }
    }

    /**
     * Send back an Intent to the list Activity when the back button is clicked
     */
    @Override
    public void onBackPressed() {
        Log.d(TAG, "Back button pressed");
        controlledFinish();
        finish();
    }

    /**
     * Read the user input to when the Activity is paused, this can also be done in the onStop
     * method.
     */
    @Override
    protected void onPause() {
        super.onPause();
        readInput();
    }

    /**
     * Save the note to internal storage if the activity is stopped and the list Activity will not
     * run. Also upload the notes file to AWS S3 Server.
     */
    @Override
    protected void onStop() {
        super.onStop();
        if(!mControlledFinish) {
            if (NoteManager.saveChanges(mPosition, mTitle, mContent, mLatitude, mLongitude)
                    == NoteManager.NEED_SYNCHRONIZE) {
                AWSManager.upload();
            }
        }
    }


    /**
     * Create the option menu, includes one buttons for finishing editing
     * @param menu a menu instance
     * @return always true
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_edit, menu);
        return true;
    }

    /**
     * Process the clicking of the done button
     * @param item the menu item being clicked
     * @return result from super method
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_done) {
            Log.d(TAG, "Finish button pressed");
            controlledFinish();
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Initialize map camera and set OnCameraChangeListener.
     * @param map GoogleMap instance
     */
    @Override
    public void onMapReady(GoogleMap map) {
        final GoogleMap mMap = map;
        // Focus the camera to the passed location
        mMap.getUiSettings().setZoomGesturesEnabled(true);
        mMap.setMyLocationEnabled(true);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLatitude, mLongitude), Activity_Map.DEFAULT_CAMERA_ZOOM));
        // Change the position of the location marker when the camera changes
        final BitmapDescriptor locatingIcon = BitmapDescriptorFactory.fromResource(R.drawable.ic_locating);
        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            public void onCameraChange(CameraPosition arg0) {
                mMap.clear();
                mMap.addMarker(new MarkerOptions().position(arg0.target).icon(locatingIcon));
                mNewLatitude = arg0.target.latitude;
                mNewLongitude = arg0.target.longitude;
                // Turn the latitude and longitude text color to grey to indicate they are invalid
                if(!mFirstCameraChange) {
                    mView_Latitude.setTextColor(getResources().getColor(R.color.grey));
                    mView_Longitude.setTextColor(getResources().getColor(R.color.grey));
                }
                mFirstCameraChange = false;
                // Update the contents of the referential coordinates
                fillRefCoordinates();
            }
        });

        Log.d(TAG, "Google map is ready");
    }

    /**
     * Fill the contents of the views
     */
    private void fillViews() {
        if (mPosition != NoteManager.NEW_NODE_POSITION) {
            if(mTitle.compareTo(NoteManager.DEFAULT_TITLE) == 0)
                mTitle = "";
            mView_Title.setText(mTitle);
            //mView_Content.setText(mContent);
            fillCoordinates();
        }
    }

    /**
     * @param latitude latitude in float
     * @return String format of the latitude
     */
    private String getLatitude(double latitude) {
        if(latitude > 0) {
            return String.format("%8.5f° N", latitude);
        } else {
            return String.format("%8.5f° S", -latitude);
        }
    }

    /**
     * @param longitude longitude in float
     * @return String format of the longitude
     */
    private String getLongitude(double longitude) {
        if(longitude > 0) {
            return String.format("%8.5f° E", longitude);
        } else {
            return String.format("%8.5f° W", -longitude);
        }
    }

    /**
     * Fill the contents of the coordinate views
     */
    private void fillCoordinates() {
        mView_Latitude.setText(getLatitude(mLatitude));
        mView_Longitude.setText(getLongitude(mLongitude));
    }

    /**
     * Fill the contents of the reference coordinate views
     */
    private void fillRefCoordinates() {
        mView_RefLatitude.setText(getLatitude(mNewLatitude));
        mView_RefLongitude.setText(getLongitude(mNewLongitude));
    }

    /**
     * Construct a bundle of the note items
     * @return the bundle of the Intent extra
     */
    Bundle makeBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt(NoteManager.EXTRA_INDEX, mPosition);
        bundle.putString(NoteManager.EXTRA_TITLE, mTitle);
        bundle.putString(NoteManager.EXTRA_CONTENT, mContent);
        bundle.putDouble(NoteManager.EXTRA_LATITUDE, mLatitude);
        bundle.putDouble(NoteManager.EXTRA_LONGITUDE, mLongitude);
        return bundle;
    }

    /**
     * Finish the Activity under the control, return the edit result to list Activity
     */
    private void controlledFinish() {
        readInput();
        Intent mIntent = new Intent();
        mIntent.putExtras(makeBundle());
        setResult(RESULT_OK, mIntent);
        mControlledFinish = true;
    }

    /**
     * Read user input
     */
    private void readInput() {
        mTitle = mView_Title.getText().toString();
        //mContent = mView_Content.getText().toString();
    }

    /**
     * Detect whether the location service is available on the cell phone
     * Use a striking hint to show the invalid Google Play Services
     */
    void detectGoogleService() {
        int status= GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if(status == ConnectionResult.SUCCESS) {
            mGoogleServiceAvailable = true;
            mView_GoogleHint.setVisibility(View.GONE);
        } else {
            mGoogleServiceAvailable = false;
            mView_GoogleHint.setVisibility(View.VISIBLE);
            String errorMsg = "Google Play Service Error :\n";
            errorMsg += GooglePlayServicesUtil.getErrorString(status);
            errorMsg += "\nDisable All Google Maps Functions";
            mView_GoogleHint.setText(errorMsg);
        }
    }
}
