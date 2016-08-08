package com.traffar.meimeime;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import android.hardware.Camera;
import android.util.Log;
import com.traffar.meimeime.Main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;


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
            if (this.motionDetected == 0 && Math.sqrt(x * x + y * y) > 10.0)
            {
               this.motionDetected = 15;
               Main.this.view2.setText((CharSequence)"");
               Main.this.wakeDevice();
            }
            if (this.motionDetected != 0)
            {
               this.latestValues[15 - this.motionDetected][0] = event.values[0];
               this.latestValues[15 - this.motionDetected][1] = event.values[1];
               this.latestValues[15 - this.motionDetected][2] = event.values[2];
               if (t - this.lastTime < 100) {
                  return;
               }
               Main.this.view2.append((CharSequence)String.format("%d %.1f, %.1f, %.1f\n",
                        (int)(t - this.lastTime),
                        Float.valueOf(event.values[0]),
                        Float.valueOf(event.values[1]),
                        Float.valueOf(event.values[2])));

               --this.motionDetected;
               if (this.motionDetected == 0)
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
      this.setContentView(2130903041);
      this.sensorManager = (SensorManager)this.getSystemService("sensor");
      this.accel = this.sensorManager.getDefaultSensor(10);
      this.sensorManager.registerListener(this.accelListner, this.accel, 3);
      this.view1 = (TextView)this.findViewById(2131165186);
      this.view2 = (TextView)this.findViewById(2131165187);
      this.cam = new CameraCtl(this, (Activity)this);
      this.analyzer = new Analyzer(this, this);
      this.createWakeLocks();
   }

   protected void createWakeLocks()
   {
      PowerManager powerManager = (PowerManager)this.getSystemService("power");
      this.fullWakeLock = powerManager.newWakeLock(268435482, "MeiMeiMe - FULL WAKE LOCK");
      this.partialWakeLock = powerManager.newWakeLock(1, "MeiMeiMe - PARTIAL WAKE LOCK");
   }

   protected void onPause()
   {
      super.onPause();
      this.partialWakeLock.acquire();
   }

   protected void onResume()
   {
      super.onResume();
      if (this.fullWakeLock.isHeld())
      {
         this.fullWakeLock.release();
      }
      if (this.partialWakeLock.isHeld())
      {
         this.partialWakeLock.release();
      }
   }

   public void wakeDevice()
   {
      this.fullWakeLock.acquire();
      KeyguardManager keyguardManager = (KeyguardManager)this.getSystemService("keyguard");
      KeyguardManager.KeyguardLock keyguardLock = keyguardManager.newKeyguardLock("TAG");
      keyguardLock.disableKeyguard();
      Log.d((String)"MeiMeiMe", (String)"====Bringging Application to Front====");
      Intent notificationIntent = new Intent((Context)this, (Class)Main.class);
      notificationIntent.setFlags(603979776);
      PendingIntent pendingIntent = PendingIntent.getActivity((Context)this, (int)0, (Intent)notificationIntent, (int)0);
      try {
         pendingIntent.send();
      }
      catch (PendingIntent.CanceledException e) {
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
      private Camera.PictureCallback mPicture;

      CameraCtl(Activity a)
      {
         this.activity = a;
         this.mPicture = new CameraCtl.PictureCallback()
         {
            public void onPictureTaken(byte[] data, Camera camera) {
               File pictureFile = CameraCtl.this.getOutputMediaFile(1);
               try {
                  Log.d((String)"MeiMeiMe", (String)("CALLBACK:" + pictureFile));
                  FileOutputStream fos = new FileOutputStream(pictureFile);
                  fos.write(data);
                  fos.close();
                  CameraCtl.this.c.stopPreview();
                  CameraCtl.this.c.release();
               }
               catch (FileNotFoundException e) {
                  Log.d((String)"MeiMeiMe", (String)("File not found: " + e.getMessage()));
               }
               catch (IOException e) {
                  Log.d((String)"MeiMeiMe", (String)("Error accessing file: " + e.getMessage()));
               }
            }
         };
      }

      private Uri getOutputMediaFileUri(int type)
      {
         return Uri.fromFile((File)this.getOutputMediaFile(type));
      }

      private File getOutputMediaFile(int type)
      {
         File mediaFile;
         Log.d((String)"MeiMeiMe", (String)"getOutputMediaFile");
         File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory((String)Environment.DIRECTORY_PICTURES), "MeiMeiMe");
         Log.d((String)"MeiMeiMe", (String)("storage:" + mediaStorageDir));
         if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
            Log.d((String)"MeiMeiMe", (String)"failed to create directory");
            return null;
         }
         String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
         if (type == 1) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");
         } else if (type == 2) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4");
         } else {
            return null;
         }
         Log.d((String)"MeiMeiMe", (String)("Media File" + mediaFile));
         return mediaFile;
      }

      public void takePhoto()
      {
         this.c = Camera.open();
         Log.d((String)"MeiMeiMe", (String)("CAM OPENED" + (Object)this.c));
         this.preview = (FrameLayout)Main.this.findViewById(2131165184);
         this.mPreview = new CameraPreview(this, (Context)this.activity, this.c);
         this.preview.addView((View)this.mPreview);
         this.timer = new Timer();
         Log.d((String)"MeiMeiMe", (String)"TAKE PHOTO");
         this.c.startPreview();
         this.timer.schedule((TimerTask)new TakePhotoTask(this, this.c), 1000);
      }

      public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback
      {
         private SurfaceHolder mHolder;
         private Camera mCamera;

         public CameraPreview(Context context, Camera camera)
         {
            super(context);
            this.mCamera = camera;
            Log.d((String)"MeiMeiMe", (String)"CameraPreview");
            this.mHolder = this.getHolder();
            this.mHolder.addCallback((SurfaceHolder.Callback)this);
            this.mHolder.setType(3);
         }

         public void surfaceCreated(SurfaceHolder holder) {
            try {
               Log.d((String)"MeiMeiMe", (String)"SURFACE created");
               this.mCamera.setPreviewDisplay(holder);
               Log.d((String)"MeiMeiMe", (String)"preview started");
            }
            catch (IOException e) {
               Log.d((String)"MeiMeiMe", (String)("Error setting camera preview: " + e.getMessage()));
            }
         }

         public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d((String)"MeiMeiMe", (String)"surface destroyed");
         }

         public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            Log.d((String)"MeiMeiMe", (String)"SURFACE CREATED");
            if (this.mHolder.getSurface() == null) {
               return;
            }
            try {
               this.mCamera.stopPreview();
            }
            catch (Exception var5_5) {
               // empty catch block
            }
            try {
               this.mCamera.setPreviewDisplay(this.mHolder);
            }
            catch (Exception e) {
               Log.d((String)"MeiMeiMe", (String)("Error starting camera preview: " + e.getMessage()));
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
            this.c.takePicture(null, null, CameraCtl.this.mPicture);
         }
      }
   }


   public class Analyzer
   {
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
