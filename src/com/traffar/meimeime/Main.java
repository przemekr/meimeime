package com.traffar.meimeime;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;


public class Main extends Activity
{
   TextView view1;
   TextView view2;
   SensorManager sensorManager;
   Sensor accel;
   private static final String TAG = "MeiMeiMe";
   public CameraCtl cam;
   public Analyzer analyzer;
   private PowerManager.WakeLock fullWakeLock;
   private PowerManager.WakeLock partialWakeLock;
   public SensorEventListener accelListner;

   public Main()
   {
      accelListner = new SensorEventListener()
      {
         public int motionDetected = 0;
         public long lastTime = 0;
         private static final int DATAPOINTS = 15;
         float[][] latestValues = new float[15][3];
         public void onAccuracyChanged(Sensor sensor, int acc) { }
         public void onSensorChanged(SensorEvent event)
         {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            long t = System.currentTimeMillis();
            Main.this.view1.setText((CharSequence)String.format("%d %.1f, %.1f, %.1f\n", (int)(t - this.lastTime),
                     Float.valueOf(event.values[0]),
                     Float.valueOf(event.values[1]),
                     Float.valueOf(event.values[2])));
            if (motionDetected == 0 && Math.sqrt(x * x + y * y) > 10.0)
            {
               this.motionDetected = 15;
               Main.this.view2.setText((CharSequence)"");
               Main.this.wakeDevice();
            }
            if (motionDetected != 0)
            {
               latestValues[15 - motionDetected][0] = event.values[0];
               latestValues[15 - motionDetected][1] = event.values[1];
               latestValues[15 - motionDetected][2] = event.values[2];
               if (t - lastTime < 100)
               {
                  return;
               }
               Main.this.view2.append((CharSequence)String.format("%d %.1f, %.1f, %.1f\n",
                        (int)(t - this.lastTime),
                        Float.valueOf(event.values[0]),
                        Float.valueOf(event.values[1]),
                        Float.valueOf(event.values[2])));

               if (--motionDetected == 0)
               {
                  analyzer.go(this.latestValues);
               }
            }
            this.lastTime = t;
         }
      };
   }

   public void onCreate(Bundle savedInstanceState)
   {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.main);
      sensorManager = (SensorManager)this.getSystemService("sensor");
      accel = this.sensorManager.getDefaultSensor(10);
      sensorManager.registerListener(this.accelListner, this.accel, 3);
      view1 = (TextView)this.findViewById(R.id.view1);
      view2 = (TextView)this.findViewById(R.id.view2);
      cam = new CameraCtl(this);
      analyzer = new Analyzer(this);
      createWakeLocks();
   }

   protected void createWakeLocks()
   {
      PowerManager powerManager = (PowerManager)this.getSystemService("power");
      this.fullWakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "MeiMeiMe - FULL WAKE LOCK");
      this.partialWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeiMeiMe - PARTIAL WAKE LOCK");
   }

   protected void onPause()
   {
      super.onPause();
      partialWakeLock.acquire();
      cam.release();
   }

   protected void onResume()
   {
      super.onResume();
      if (fullWakeLock.isHeld())
      {
         fullWakeLock.release();
      }
      if (partialWakeLock.isHeld())
      {
         partialWakeLock.release();
      }
   }

   public void wakeDevice()
   {
      this.fullWakeLock.acquire();
      KeyguardManager keyguardManager = (KeyguardManager)this.getSystemService("keyguard");
      KeyguardManager.KeyguardLock keyguardLock = keyguardManager.newKeyguardLock("TAG");
      keyguardLock.disableKeyguard();
      Log.d(TAG, "====Bringging Application to Front====");
      Intent notificationIntent = new Intent((Context)this, (Class)Main.class);
      notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
      PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
      try
      {
         pendingIntent.send();
      }
      catch (PendingIntent.CanceledException e)
      {
         e.printStackTrace();
      }
   }

   public class CameraCtl
   {
      private Timer timer;
      private Activity activity;
      private CameraPreview mPreview;
      private FrameLayout preview;
      public static final int MEDIA_TYPE_IMAGE = 1;
      public static final int MEDIA_TYPE_VIDEO = 2;
      private Camera c;
      private PictureCallback mPicture;

      CameraCtl(Activity a)
      {
         this.activity = a;
         this.mPicture = new PictureCallback()
         {
            public void onPictureTaken(byte[] data, Camera camera)
            {
               File pictureFile = CameraCtl.this.getOutputMediaFile(1);
               try
               {
                  Log.d(TAG, ("CALLBACK:" + pictureFile));
                  FileOutputStream fos = new FileOutputStream(pictureFile);
                  fos.write(data);
                  fos.close();
                  camera.stopPreview();
                  camera.release();
               }
               catch (FileNotFoundException e)
               {
                  Log.d(TAG, ("File not found: " + e.getMessage()));
               }
               catch (IOException e)
               {
                  Log.d(TAG, ("Error accessing file: " + e.getMessage()));
               }
            }
         };
      }

      void release()
      {
         if (c != null)
         {
            c.release();
         }
      }

      private Uri getOutputMediaFileUri(int type)
      {
         return Uri.fromFile((File)this.getOutputMediaFile(type));
      }

      private File getOutputMediaFile(int type)
      {
         File mediaFile;
         Log.d(TAG, "getOutputMediaFile");
         File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), TAG);
         Log.d(TAG, ("storage:" + mediaStorageDir));
         if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs())
         {
            Log.d(TAG, "failed to create directory");
            return null;
         }
         String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
         if (type == 1)
         {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");
         } else if (type == 2) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4");
         } else {
            return null;
         }
         Log.d(TAG, ("Media File" + mediaFile));
         return mediaFile;
      }

      public void takePhoto()
      {
         c = Camera.open();
         Log.d(TAG, ("CAM OPENED" + (Object)this.c));
         preview = (FrameLayout)findViewById(R.id.camera_preview);
         mPreview = new CameraPreview(activity, c);
         preview.addView((View)this.mPreview);
         timer = new Timer();
         Log.d(TAG, "TAKE PHOTO");
         timer.schedule(new TakePhotoTask(c), 1000);
      }

      public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback
      {
         private SurfaceHolder mHolder;
         private Camera mCamera;

         public CameraPreview(Context context, Camera camera)
         {
            super(context);
            mCamera = camera;
            mHolder = getHolder();
            mHolder.addCallback(this);
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
         }

         public void surfaceCreated(SurfaceHolder holder)
         {
            try
            {
               Log.d(TAG, "SURFACE created");
               mCamera.setPreviewDisplay(holder);
               mCamera.startPreview();
            }
            catch (IOException e)
            {
               Log.d(TAG, ("Error setting camera preview: " + e.getMessage()));
            }
         }

         public void surfaceDestroyed(SurfaceHolder holder)
         {
            Log.d(TAG, "surface destroyed");
         }

         public void surfaceChanged(SurfaceHolder holder, int format, int w, int h)
         {
            Log.d(TAG, "surface changed");
            if (this.mHolder.getSurface() == null)
            {
               return;
            }
            try
            {
               this.mCamera.stopPreview();
            }
            catch (Exception e)
            {
               // empty catch block
            }
            try
            {
               this.mCamera.setPreviewDisplay(this.mHolder);
            }
            catch (Exception e)
            {
               Log.d(TAG, ("Error starting camera preview: " + e.getMessage()));
            }
         }
      }

      public class TakePhotoTask extends TimerTask
      {
         private Camera c;

         TakePhotoTask(Camera c)
         {
            this.c = c;
         }

         public void run()
         {
            c.takePicture(null, null, mPicture);
         }
      }
   }

   public class Analyzer
   {
      public Main main;
      Analyzer(Main a)
      {
         main = a;
      }

      public void go(float[][] values)
      {
         main.cam.takePhoto();
      }
   }
}
