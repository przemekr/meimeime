package com.traffar.meimeime;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.AlertDialog;
import android.view.WindowManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;

import android.media.SoundPool;
import android.media.AudioManager;
import android.preference.PreferenceManager;

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
import android.widget.ImageView;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Comparator;
import java.util.HashMap;


public class Main extends Activity
{
   TextView view1;
   TextView view2;
   SensorManager sensorManager;
   Sensor accel;
   private static final String TAG = "MeiMeiMe";
   public CameraCtl cam;
   public Analyzer analyzer;
   public SensorEventListener accelListner;
   public HashMap<Integer, Integer> imgViewMap;

   SharedPreferences mPrefs;
   final String welcomeScreenShownPref = "welcomeScreenShown";


   public Main()
   {
      accelListner = new SensorEventListener()
      {
         public int motionDetected = 0;
         public int initialHighAccel = 0;
         public int finalStady = 0;
         public int beepRate = 4;
         public long lastTime = 0;
         private static final int DATAPOINTS = 20;
         float[][] latestValues = new float[DATAPOINTS][3];
         public void onAccuracyChanged(Sensor sensor, int acc) { }
         public void onSensorChanged(SensorEvent event)
         {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            long t = System.currentTimeMillis();
            view1.setText((CharSequence)String.format("%d %.1f, %.1f, %.1f\n", (int)(t - lastTime),
                     Float.valueOf(event.values[0]),
                     Float.valueOf(event.values[1]),
                     Float.valueOf(event.values[2])));
            Log.d(TAG, String.format("On sensonr changed:  %.1f, %.1f, %.1f", event.values[0], event.values[1], event.values[2]));

            if (motionDetected == 0 && Math.sqrt(x*x + y*y) > 15.0)
            {
               motionDetected = DATAPOINTS;
               view2.setText((CharSequence)"");
               finalStady = 0;
               initialHighAccel = 0;
            }
            if (motionDetected != 0)
            {
               latestValues[DATAPOINTS - motionDetected][0] = event.values[0];
               latestValues[DATAPOINTS - motionDetected][1] = event.values[1];
               latestValues[DATAPOINTS - motionDetected][2] = event.values[2];
               if (t - lastTime < 150)
               {
                  return;
               }
               view2.append((CharSequence)String.format("%d %.1f, %.1f, %.1f\n",
                        (int)(t - lastTime),
                        Float.valueOf(event.values[0]),
                        Float.valueOf(event.values[1]),
                        Float.valueOf(event.values[2])));

               if (motionDetected %beepRate == 0)
               {
                  playSound(Beep1);
               }

               if (DATAPOINTS-motionDetected < 3 && Math.sqrt(x*x + y*y) > 10.0)
               {
                  initialHighAccel++;
               }
               if (DATAPOINTS-motionDetected == 3 && initialHighAccel <= 1)
               {
                  motionDetected = 0;
                  return;
               } else {
                  wakeDevice();
               }

               if (motionDetected < 5 && Math.sqrt(x*x + y*y + z*z) < 4.0)
               {
                  finalStady++;
               }

               if (--motionDetected == 0 && finalStady > 3)
               {
                  playSound(Shutter);
                  analyzer.go(latestValues);
               }
            }
            lastTime = t;
         }
      };
   }

   private void registerBroadcastReceiver()
   {
      final IntentFilter theFilter = new IntentFilter();
      /** System Defined Broadcast */
      theFilter.addAction(Intent.ACTION_SCREEN_ON);
      theFilter.addAction(Intent.ACTION_SCREEN_OFF);

      BroadcastReceiver screenOnOffReceiver = new BroadcastReceiver()
      {
         @Override
         public void onReceive(Context context, Intent intent)
         {
            String strAction = intent.getAction();
            if (strAction.equals(Intent.ACTION_SCREEN_OFF))
            {
                  Log.d(TAG, "Screen off");
                  sensorManager = (SensorManager)getSystemService("sensor");
                  accel = sensorManager.getDefaultSensor(10);
                  sensorManager.unregisterListener(accelListner, accel);
            }
            if (strAction.equals(Intent.ACTION_SCREEN_ON))
            {
                  Log.d(TAG, "Screen on");
                  sensorManager = (SensorManager)getSystemService("sensor");
                  accel = sensorManager.getDefaultSensor(10);
                  sensorManager.registerListener(accelListner, accel,
                        SensorManager.SENSOR_DELAY_NORMAL);
            }
         }
      };
      getApplicationContext().registerReceiver(screenOnOffReceiver, theFilter);
   }

   public void onCreate(Bundle savedInstanceState)
   {
      super.onCreate(savedInstanceState);
      mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
      setContentView(R.layout.main);
      sensorManager = (SensorManager)getSystemService("sensor");
      accel = sensorManager.getDefaultSensor(10);
      sensorManager.registerListener(accelListner, accel,
            SensorManager.SENSOR_DELAY_NORMAL);
      view1 = (TextView)findViewById(R.id.view1);
      view2 = (TextView)findViewById(R.id.view2);
      cam = new CameraCtl(this);
      cam.resume();
      analyzer = new Analyzer();
      initSounds();
      registerBroadcastReceiver();

      if (!mPrefs.getBoolean(welcomeScreenShownPref, false))
      {
         String whatsNewTitle = getResources().getString(R.string.whatsNewTitle);
         String whatsNewText = getResources().getString(R.string.whatsNewText);
         new AlertDialog.Builder(this).setIcon(
               android.R.drawable.ic_dialog_alert).setTitle(whatsNewTitle).setMessage(whatsNewText).setPositiveButton(
               R.string.ok, new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int which) {
                     dialog.dismiss();
                  }
               }).show();
         SharedPreferences.Editor editor = mPrefs.edit();
         editor.putBoolean(welcomeScreenShownPref, true);
         editor.commit();
      }

      imgViewMap = new HashMap<Integer, Integer>();
      imgViewMap.put(1, R.id.imageView1);
      imgViewMap.put(2, R.id.imageView2);
      imgViewMap.put(3, R.id.imageView3);
      imgViewMap.put(4, R.id.imageView4);

      // check the latest photos
      final File mediaStorageDir = new File(
            Environment.getExternalStoragePublicDirectory(
               Environment.DIRECTORY_PICTURES), TAG);
      File[] fileList = mediaStorageDir.listFiles();
      Arrays.sort(fileList, new Comparator<File>(){
         public int compare(File f1, File f2) {
            return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
         }});

      int idx = 0;
      for (final File file : fileList)
      {
         if (file.isFile() && idx++ < 4)
         {
            ImageView iv = (ImageView)findViewById(imgViewMap.get(idx));
            iv.setImageBitmap(getScaledBitmap(file.getPath(), 400, 300));
            iv.setOnClickListener(new View.OnClickListener() {
               public void onClick(View v)
               {
                  Intent intent = new Intent();
                  intent.setAction(Intent.ACTION_VIEW);
                  intent.setDataAndType(Uri.fromFile(file), "image/*");
                  startActivity(intent);
               }        
            });
         }
      }
   }

   protected void onPause()
   {
      super.onPause();
      cam.release();
   }

   protected void onResume()
   {
      super.onResume();
      cam.resume();
   }

   public void wakeDevice()
   {
      if (cam.active())
      {
         cam.startPreview();
         return;
      }
      Log.d(TAG, "==== disable keyguard ====");
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

      Log.d(TAG, "==== bringging application to front ====");
      Intent notificationIntent = new Intent(this, Main.class);
      notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
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
      private Activity activity;
      private CameraPreview cameraPreview;
      private FrameLayout frameLayout;
      public static final int MEDIA_TYPE_IMAGE = 1;
      public static final int MEDIA_TYPE_VIDEO = 2;
      private Camera c;
      private PictureCallback mPicture;
      private int currentThumb;
      public File pictureFile;

      CameraCtl(Activity a)
      {
         activity = a;
         currentThumb = 0;
         mPicture = new PictureCallback()
         {
            public void onPictureTaken(byte[] data, Camera camera)
            {
               pictureFile = getOutputMediaFile(1);
               try
               {
                  Log.d(TAG, ("CALLBACK:" + pictureFile));
                  FileOutputStream fos = new FileOutputStream(pictureFile);
                  fos.write(data);
                  fos.close();
               }
               catch (FileNotFoundException e)
               {
                  Log.d(TAG, ("File not found: " + e.getMessage()));
               }
               catch (IOException e)
               {
                  Log.d(TAG, ("Error accessing file: " + e.getMessage()));
               }

               ImageView iv = (ImageView)findViewById(imgViewMap.get(currentThumb++%4+1));
               iv.setImageBitmap(getScaledBitmap(pictureFile.getPath(), 400, 300));
               iv.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                           Intent intent = new Intent();
                           intent.setAction(Intent.ACTION_VIEW);
                           intent.setDataAndType(Uri.parse("file://" + pictureFile.getPath()), "image/*");
                           startActivity(intent);
                        }        
               });
               camera.startPreview();
            }
         };
      }

      void startPreview()
      {
         if (c == null)
         {
            resume();
         } else {
            c.startPreview();
         }
      }

      boolean active()
      {
         return (c != null);
      }

      void resume()
      {
         Log.d(TAG, ("CAMCTL RESUME"));
         if (c == null)
         {
            c = Camera.open();
            c.setDisplayOrientation(90);
            Log.d(TAG, ("CAM OPENED:" + c));
         }
         cameraPreview = new CameraPreview(activity, c);
         frameLayout = (FrameLayout)findViewById(R.id.camera_preview);
         frameLayout.removeAllViews();
         frameLayout.addView((View)cameraPreview);
      }

      void release()
      {
         if (c != null)
         {
            c.release();
            c = null;
            Log.d(TAG, "CAM RELEASE");
         }
         if (cameraPreview != null)
         {
            cameraPreview.removeCallback();
            cameraPreview = null;
            Log.d(TAG, "CAM PREVIEW RELEASE");
         }
      }

      private Uri getOutputMediaFileUri(int type)
      {
         return Uri.fromFile(getOutputMediaFile(type));
      }

      private File getOutputMediaFile(int type)
      {
         File mediaFile;
         Log.d(TAG, "getOutputMediaFile");
         File mediaStorageDir = new File(
               Environment.getExternalStoragePublicDirectory(
                  Environment.DIRECTORY_PICTURES), TAG);
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
         Log.d(TAG, "TAKE PHOTO");
         if (c == null)
         {
            Log.d(TAG, "TAKE PHOTO: camera null!");
            return;
         }
         c.takePicture(null, null, mPicture);
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

         public void removeCallback()
         {
            mHolder.removeCallback(this);
         }

         public void surfaceChanged(SurfaceHolder holder, int format, int w, int h)
         {
            Log.d(TAG, "surface changed");
            if (mHolder.getSurface() == null)
            {
               Log.d(TAG, "surface changed, getSurface() == NULL!!!!");
               return;
            }
            try
            {
               mCamera.stopPreview();
            }
            catch (Exception e)
            {
               Log.d(TAG, ("Error stoping camera preview: " + e.getMessage()));
            }
            try
            {
               mCamera.setPreviewDisplay(mHolder);
               mCamera.startPreview();
            }
            catch (Exception e)
            {
               Log.d(TAG, ("Error starting camera preview: " + e.getMessage()));
            }
         }
      }
   }

   public class Analyzer
   {
      public void go(float[][] values)
      {
         cam.takePhoto();
      }
   }

   private SoundPool soundPool;
   public int Beep1;
   public int Shutter;

   /** Populate the SoundPool*/
   public void initSounds()
   {
      soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 100);
      Beep1 = soundPool.load(this, R.raw.beep1, 1);
      Shutter = soundPool.load(this, R.raw.shutter, 1);
   }

   /** Play a given sound in the soundPool */
   public void playSound(int soundID)
   {
      if (soundPool == null)
      {
         initSounds();
      }
      float volume = (float)0.5;
      // play sound with same right and left volume, with a priority of 1, 
      // zero repeats (i.e play once), and a playback rate of 1f
      soundPool.play(soundID, volume, volume, 1, 0, 1f);
   }

   private Bitmap getScaledBitmap(String picturePath, int width, int height)
   {
      BitmapFactory.Options sizeOptions = new BitmapFactory.Options();
      sizeOptions.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(picturePath, sizeOptions);
      int inSampleSize = calculateInSampleSize(sizeOptions, width, height);
      sizeOptions.inJustDecodeBounds = false;
      sizeOptions.inSampleSize = inSampleSize;
      return BitmapFactory.decodeFile(picturePath, sizeOptions);
   }

   private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
   {
      // Raw height and width of image
      final int height = options.outHeight;
      final int width = options.outWidth;
      int inSampleSize = 1;
      if (height > reqHeight || width > reqWidth)
      {
         // Calculate ratios of height and width to requested height and
         // width
         final int heightRatio = Math.round((float) height / (float) reqHeight);
         final int widthRatio = Math.round((float) width / (float) reqWidth);

         // Choose the smallest ratio as inSampleSize value, this will
         // guarantee
         // a final image with both dimensions larger than or equal to the
         // requested height and width.
         inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
      }
      return inSampleSize;
   }
}
