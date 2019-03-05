/*===============================================================================
Copyright (c) 2016-2018 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2015 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/


package com.vuforia.engine.SampleApplication;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.WindowManager;

import com.vuforia.CameraDevice;
import com.vuforia.Device;
import com.vuforia.FUSION_PROVIDER_TYPE;
import com.vuforia.INIT_ERRORCODE;
import com.vuforia.INIT_FLAGS;
import com.vuforia.State;
import com.vuforia.Vuforia;
import com.vuforia.Vuforia.UpdateCallbackInterface;
import com.vuforia.engine.Video.R;

import java.lang.ref.WeakReference;


public class SampleApplicationSession implements UpdateCallbackInterface
{
    
    private static final String LOGTAG = "SampleAppSession";
    
    // Reference to the current activity
    private WeakReference<Activity> mActivityRef;
    private WeakReference<SampleApplicationControl> mSessionControlRef;
    
    // Flags
    private boolean mStarted = false;
    private boolean mCameraRunning = false;
    
    // The async tasks to initialize the Vuforia Engine:
    private InitVuforiaTask mInitVuforiaTask;
    private InitTrackerTask mInitTrackerTask;
    private LoadTrackerTask mLoadTrackerTask;
    private StartVuforiaTask mStartVuforiaTask;
    private ResumeVuforiaTask mResumeVuforiaTask;
    
    // An object used for synchronizing Vuforia Engine initialization, dataset loading
    // and the Android onDestroy() life cycle event. If the application is
    // destroyed while a data set is still being loaded, then we wait for the
    // loading operation to finish before shutting down Vuforia Engine:
    private final Object mLifecycleLock = new Object();
    
    // Vuforia Engine initialization flags:
    private int mVuforiaFlags = 0;
    
    // Holds the camera configuration to use upon resuming
    private int mCamera = CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT;
    

    public SampleApplicationSession(SampleApplicationControl sessionControl)
    {
        mSessionControlRef = new WeakReference<>(sessionControl);
    }
    
    
    // Initializes Vuforia Engine and sets up preferences.
    public void initAR(Activity activity, int screenOrientation)
    {
        SampleApplicationException vuforiaException = null;
        mActivityRef = new WeakReference<>(activity);
        
        if ((screenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR)
            && (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO))
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
        
        // Use an OrientationChangeListener here to capture all orientation changes.  Android
        // will not send an Activity.onConfigurationChanged() callback on a 180 degree rotation,
        // ie: Left Landscape to Right Landscape.  Vuforia Engine needs to react to this change and the
        // SampleApplicationSession needs to update the Projection Matrix.
        OrientationEventListener orientationEventListener = new OrientationEventListener(mActivityRef.get())
        {
            @Override
            public void onOrientationChanged(int i)
            {
                int activityRotation = mActivityRef.get().getWindowManager().getDefaultDisplay().getRotation();
                if(mLastRotation != activityRotation)
                {
                    mLastRotation = activityRotation;
                }
            }

            int mLastRotation = -1;
        };
        
        if(orientationEventListener.canDetectOrientation())
            orientationEventListener.enable();

        // Apply screen orientation
        mActivityRef.get().setRequestedOrientation(screenOrientation);
        
        // As long as this window is visible to the user, keep the device's
        // screen turned on and bright:
        mActivityRef.get().getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        mVuforiaFlags = INIT_FLAGS.GL_20;
        
        // Initialize Vuforia Engine asynchronously to avoid blocking the
        // main (UI) thread.
        //
        // NOTE: This task instance must be created and invoked on the
        // UI thread and it can be executed only once!
        if (mInitVuforiaTask != null)
        {
            String logMessage = "Cannot initialize SDK twice";
            vuforiaException = new SampleApplicationException(
                SampleApplicationException.VUFORIA_ALREADY_INITIALIZATED,
                logMessage);
            Log.e(LOGTAG, logMessage);
        }
        
        if (vuforiaException == null)
        {
            try
            {
                mInitVuforiaTask = new InitVuforiaTask();
                mInitVuforiaTask.execute();
            } catch (Exception e)
            {
                String logMessage = "Initializing Vuforia Engine failed";
                vuforiaException = new SampleApplicationException(
                    SampleApplicationException.INITIALIZATION_FAILURE,
                    logMessage);
                Log.e(LOGTAG, logMessage);
            }
        }
        
        if (vuforiaException != null)
        {
            mSessionControlRef.get().onInitARDone(vuforiaException);
        }
    }


    // Sets the fusion provider type for DeviceTracker optimization
    // This setting only affects the Tracker if the DeviceTracker is used.
    // By default, the provider type is set to FUSION_OPTIMIZE_MODEL_TARGETS_AND_SMART_TERRAIN
    public boolean setFusionProviderType(int providerType)
    {
        int provider =  Vuforia.getActiveFusionProvider();

        if ((provider & ~providerType) != 0)
        {
            if (Vuforia.setAllowedFusionProviders(providerType) == FUSION_PROVIDER_TYPE.FUSION_PROVIDER_INVALID_OPERATION)
            {
                Log.e(LOGTAG,"Failed to set fusion provider type: " + providerType);
                return false;
            }
        }

        Log.d(LOGTAG, "Successfully set fusion provider type: " + providerType);

        return true;
    }
    
    
    // Starts Vuforia Engine, initialize and starts the camera and start the trackers
    private void startCameraAndTrackers(int camera) throws SampleApplicationException
    {
        String error;
        if(mCameraRunning)
        {
        	error = "Camera already running, unable to open again";
        	Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }
        
        mCamera = camera;
        if (!CameraDevice.getInstance().init(camera))
        {
            error = "Unable to open camera device: " + camera;
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }
               
        if (!CameraDevice.getInstance().selectVideoMode(
            CameraDevice.MODE.MODE_DEFAULT))
        {
            error = "Unable to set video mode";
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }
        
        if (!CameraDevice.getInstance().start())
        {
            error = "Unable to start camera device: " + camera;
            Log.e(LOGTAG, error);
            throw new SampleApplicationException(
                SampleApplicationException.CAMERA_INITIALIZATION_FAILURE, error);
        }
        
        mSessionControlRef.get().doStartTrackers();
        
        mCameraRunning = true;
        
        if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO))
        {
            if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO))
                CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
        }
    }


    public void startAR(int camera)
    {
        mCamera = camera;
        SampleApplicationException vuforiaException = null;

        try {
            mStartVuforiaTask = new StartVuforiaTask();
            mStartVuforiaTask.execute();
        }
        catch (Exception e)
        {
            String logMessage = "Failed to start Vuforia Engine.";
            Log.e(LOGTAG, logMessage);

            vuforiaException = new SampleApplicationException(
                    SampleApplicationException.CAMERA_INITIALIZATION_FAILURE,
                    logMessage);
        }

        if (vuforiaException != null)
        {
            // Send exception to the application and call initDone
            // to stop initialization process
            mSessionControlRef.get().onInitARDone(vuforiaException);
        }
    }
    
    
    // Stops any ongoing initialization, stops Vuforia Engine
    public void stopAR() throws SampleApplicationException
    {
        // Cancel potentially running tasks
        if (mInitVuforiaTask != null
            && mInitVuforiaTask.getStatus() != InitVuforiaTask.Status.FINISHED)
        {
            mInitVuforiaTask.cancel(true);
            mInitVuforiaTask = null;
        }
        
        if (mLoadTrackerTask != null
            && mLoadTrackerTask.getStatus() != LoadTrackerTask.Status.FINISHED)
        {
            mLoadTrackerTask.cancel(true);
            mLoadTrackerTask = null;
        }
        
        mInitVuforiaTask = null;
        mLoadTrackerTask = null;
        
        mStarted = false;
        
        stopCamera();
        
        // Ensure that all asynchronous operations to initialize Vuforia
        // Engine and loading the tracker datasets do not overlap:
        synchronized (mLifecycleLock)
        {
            
            boolean unloadTrackersResult;
            boolean deinitTrackersResult;
            
            // Destroy the tracking data set:
            unloadTrackersResult = mSessionControlRef.get().doUnloadTrackersData();
            
            // Deinitialize the trackers:
            deinitTrackersResult = mSessionControlRef.get().doDeinitTrackers();
            
            // Deinitialize Vuforia Engine:
            Vuforia.deinit();
            
            if (!unloadTrackersResult)
                throw new SampleApplicationException(
                    SampleApplicationException.UNLOADING_TRACKERS_FAILURE,
                    "Failed to unload trackers\' data");
            
            if (!deinitTrackersResult)
                throw new SampleApplicationException(
                    SampleApplicationException.TRACKERS_DEINITIALIZATION_FAILURE,
                    "Failed to deinitialize trackers");
            
        }
    }
    
    
    // Pauses Vuforia Engine and stops the camera
    public void pauseAR() throws SampleApplicationException
    {
        if (mStarted)
        {
            stopCamera();
        }
        
        Vuforia.onPause();
    }
    
    
    // Callback called every cycle
    @Override
    public void Vuforia_onUpdate(State s)
    {
        mSessionControlRef.get().onVuforiaUpdate(s);
    }
    
    
    // Manages the configuration changes
    public void onConfigurationChanged()
    {
        if (mStarted)
        {
            Device.getInstance().setConfigurationChanged();
        }
    }
    
    
    // Methods to be called to handle lifecycle
    public void onResume()
    {
        if (mResumeVuforiaTask == null
                || mResumeVuforiaTask.getStatus() == ResumeVuforiaTask.Status.FINISHED)
        {
            // onResume() will sometimes be called twice depending on the screen lock mode
            // This will prevent redundant AsyncTasks from being executed
            SampleApplicationException vuforiaException = null;

            try {
                mResumeVuforiaTask = new ResumeVuforiaTask();
                mResumeVuforiaTask.execute();
            }
            catch (Exception e)
            {
                String logMessage = "Failed to resume Vuforia Engine.";
                Log.e(LOGTAG, logMessage);

                vuforiaException = new SampleApplicationException(
                        SampleApplicationException.INITIALIZATION_FAILURE,
                        logMessage);
            }

            if (vuforiaException != null)
            {
                // Send exception to the application and call initDone
                // to stop initialization process
                mSessionControlRef.get().onInitARDone(vuforiaException);
            }
        }
    }
    
    
    public void onPause()
    {
        Vuforia.onPause();
    }
    
    
    public void onSurfaceChanged(int width, int height)
    {
        Vuforia.onSurfaceChanged(width, height);
    }
    
    
    public void onSurfaceCreated()
    {
        Vuforia.onSurfaceCreated();
    }
    
    // An async task to initialize Vuforia Engine asynchronously.
    private class InitVuforiaTask extends AsyncTask<Void, Integer, Boolean>
    {
        // Initialize with invalid value:
        private int mProgressValue = -1;
        
        
        protected Boolean doInBackground(Void... params)
        {
            // Prevent the onDestroy() method to overlap with initialization:
            synchronized (mLifecycleLock)
            {
                Vuforia.setInitParameters(mActivityRef.get(), mVuforiaFlags, "");
                
                do
                {
                    // Vuforia.init() blocks until an initialization step is
                    // complete, then it proceeds to the next step and reports
                    // progress in percents (0 ... 100%).
                    // If Vuforia.init() returns -1, it indicates an error.
                    // Initialization is done when progress has reached 100%.
                    mProgressValue = Vuforia.init();
                    
                    // Publish the progress value:
                    publishProgress(mProgressValue);
                    
                    // We check whether the task has been canceled in the
                    // meantime (by calling AsyncTask.cancel(true)).
                    // and bail out if it has, thus stopping this thread.
                    // This is necessary as the AsyncTask will run to completion
                    // regardless of the status of the component that
                    // started is.
                } while (!isCancelled() && mProgressValue >= 0
                    && mProgressValue < 100);

                return (mProgressValue > 0);
            }
        }
        
        
        protected void onProgressUpdate(Integer... values)
        {
            // Do something with the progress value "values[0]", e.g. update
            // splash screen, progress bar, etc.
        }
        
        
        protected void onPostExecute(Boolean result)
        {
            // Done initializing Vuforia Engine, proceed to next application
            // initialization status:

            Log.d(LOGTAG, "InitVuforiaTask.onPostExecute: execution "
                    + (result ? "successful" : "failed"));

            SampleApplicationException vuforiaException = null;
            
            if (result)
            {
                try {
                    mInitTrackerTask = new InitTrackerTask();
                    mInitTrackerTask.execute();
                }
                catch (Exception e)
                {
                    String logMessage = "Failed to initialize tracker data.";
                    Log.e(LOGTAG, logMessage);

                    vuforiaException = new SampleApplicationException(
                            SampleApplicationException.TRACKERS_INITIALIZATION_FAILURE,
                            logMessage);
                }
            }
            else
            {
                String logMessage;

                // NOTE: Check if initialization failed because the device is
                // not supported. At this point the user should be informed
                // with a message.
                logMessage = getInitializationErrorString(mProgressValue);

                // Log error:
                Log.e(LOGTAG, "InitVuforiaTask.onPostExecute: " + logMessage
                        + " Exiting.");

                // Send exception to the application and call initDone
                // to stop initialization process
                vuforiaException = new SampleApplicationException(
                        SampleApplicationException.INITIALIZATION_FAILURE,
                        logMessage);
            }

            if (vuforiaException != null)
            {
                // Send exception to the application and call initDone
                // to stop initialization process
                mSessionControlRef.get().onInitARDone(vuforiaException);
            }
        }
    }

    // An async task to resume Vuforia Engine asynchronously
    private class ResumeVuforiaTask extends AsyncTask<Void, Void, Void>
    {
        protected Void doInBackground(Void... params)
        {
            // Prevent the concurrent lifecycle operations:
            synchronized (mLifecycleLock)
            {
                Vuforia.onResume();
            }

            return null;
        }

        protected void onPostExecute(Void result)
        {
            Log.d(LOGTAG, "ResumeVuforiaTask.onPostExecute");

            // We may start the camera only if the Vuforia Engine has already been initialized
            if (mStarted)
            {
                if (!mCameraRunning)
                {
                    startAR(mCamera);
                }
                else
                {
                    mSessionControlRef.get().onVuforiaStarted();
                }

                mSessionControlRef.get().onVuforiaResumed();
            }
        }
    }

    // An async task to initialize trackers asynchronously
    private class InitTrackerTask extends AsyncTask<Void, Integer, Boolean>
    {
        protected  Boolean doInBackground(Void... params)
        {
            synchronized (mLifecycleLock)
            {
                // Load the tracker data set:
                return mSessionControlRef.get().doInitTrackers();
            }
        }

        protected void onPostExecute(Boolean result)
        {

            SampleApplicationException vuforiaException = null;
            Log.d(LOGTAG, "InitTrackerTask.onPostExecute: execution "
                    + (result ? "successful" : "failed"));

            if (result)
            {
                try {
                    mLoadTrackerTask = new LoadTrackerTask();
                    mLoadTrackerTask.execute();
                }
                catch (Exception e)
                {
                    String logMessage = "Failed to load tracker data.";
                    Log.e(LOGTAG, logMessage);

                    vuforiaException = new SampleApplicationException(
                            SampleApplicationException.TRACKERS_INITIALIZATION_FAILURE,
                            logMessage);
                }
            }
            else
            {
                String logMessage = "Failed to initialize trackers.";
                Log.e(LOGTAG, logMessage);

                // Error initializing trackers
                vuforiaException = new SampleApplicationException(
                        SampleApplicationException.TRACKERS_INITIALIZATION_FAILURE,
                        logMessage);
            }

            if (vuforiaException != null)
            {
                // Send exception to the application and call initDone
                // to stop initialization process
                mSessionControlRef.get().onInitARDone(vuforiaException);
            }
        }
    }
    
    // An async task to load the tracker data asynchronously.
    private class LoadTrackerTask extends AsyncTask<Void, Integer, Boolean>
    {
        protected Boolean doInBackground(Void... params)
        {
            // Prevent the onDestroy() method to overlap:
            synchronized (mLifecycleLock)
            {
                // Load the tracker data set:
                return mSessionControlRef.get().doLoadTrackersData();
            }
        }
        
        
        protected void onPostExecute(Boolean result)
        {
            
            SampleApplicationException vuforiaException = null;
            
            Log.d(LOGTAG, "LoadTrackerTask.onPostExecute: execution "
                + (result ? "successful" : "failed"));
            
            if (!result)
            {
                String logMessage = "Failed to load tracker data.";
                // Error loading dataset
                Log.e(LOGTAG, logMessage);
                vuforiaException = new SampleApplicationException(
                    SampleApplicationException.LOADING_TRACKERS_FAILURE,
                    logMessage);
            } else
            {
                // Hint to the virtual machine that it would be a good time to
                // run the garbage collector:
                //
                // NOTE: This is only a hint. There is no guarantee that the
                // garbage collector will actually be run.
                System.gc();
                
                Vuforia.registerCallback(SampleApplicationSession.this);
                
                mStarted = true;
            }
            
            // Done loading the tracker, update application status, send the
            // exception to check errors
            mSessionControlRef.get().onInitARDone(vuforiaException);
        }
    }

    // An async task to start the camera and trackers
    private class StartVuforiaTask extends AsyncTask<Void, Void, Boolean>
    {
        SampleApplicationException vuforiaException = null;

        protected Boolean doInBackground(Void... params)
        {
            // Prevent the concurrent lifecycle operations:
            synchronized (mLifecycleLock)
            {
                try {
                    startCameraAndTrackers(mCamera);
                }
                catch (SampleApplicationException e)
                {
                    Log.e(LOGTAG, "StartVuforiaTask.doInBackground: Could not start AR with exception: " + e);
                    vuforiaException = e;
                }
            }

            return true;
        }

        protected void onPostExecute(Boolean result)
        {
            Log.d(LOGTAG, "StartVuforiaTask.onPostExecute: execution "
                    + (result ? "successful" : "failed"));

            if (vuforiaException != null)
            {
                // Send exception to the application and call initDone
                // to stop initialization process
                mSessionControlRef.get().onInitARDone(vuforiaException);
            }

            mSessionControlRef.get().onVuforiaStarted();
        }
    }
    
    
    // Returns the error message for each error code
    private String getInitializationErrorString(int code)
    {
        if (code == INIT_ERRORCODE.INIT_DEVICE_NOT_SUPPORTED)
            return mActivityRef.get().getString(R.string.INIT_ERROR_DEVICE_NOT_SUPPORTED);
        if (code == INIT_ERRORCODE.INIT_NO_CAMERA_ACCESS)
            return mActivityRef.get().getString(R.string.INIT_ERROR_NO_CAMERA_ACCESS);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_MISSING_KEY)
            return mActivityRef.get().getString(R.string.INIT_LICENSE_ERROR_MISSING_KEY);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_INVALID_KEY)
            return mActivityRef.get().getString(R.string.INIT_LICENSE_ERROR_INVALID_KEY);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT)
            return mActivityRef.get().getString(R.string.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT)
            return mActivityRef.get().getString(R.string.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_CANCELED_KEY)
            return mActivityRef.get().getString(R.string.INIT_LICENSE_ERROR_CANCELED_KEY);
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH)
            return mActivityRef.get().getString(R.string.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH);
        else
        {
            return mActivityRef.get().getString(R.string.INIT_LICENSE_ERROR_UNKNOWN_ERROR);
        }
    }
    
    
    public void stopCamera()
    {
        if (mCameraRunning)
        {
            mSessionControlRef.get().doStopTrackers();
            mCameraRunning = false;
            CameraDevice.getInstance().stop();
            CameraDevice.getInstance().deinit();
        }
    }
    
    
    // Returns true if Vuforia Engine is initialized, the trackers started and the
    // tracker data loaded
    private boolean isARRunning()
    {
        return mStarted;
    }
    
}
