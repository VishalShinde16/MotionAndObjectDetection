package com.example.motiondetection3;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;

import java.util.Date;
import java.util.List;

public class MainActivity extends CameraActivity {

    CameraBridgeViewBase cameraBridgeViewBase;
    Mat prevframe_gray,currframe_gray,rgb_frame,diff;

    double motionThreshold = 5000;
    double changed_frame_count = 0;
    double frame_count = 0;
    boolean is_init;

    double longitude;
    double latitude;

    FirebaseDatabase database;
    FirebaseStorage storage;

    LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //get camera and location permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getPermission();
        }


        //


        is_init = false;

        database = FirebaseDatabase.getInstance();
        storage = FirebaseStorage.getInstance();

        cameraBridgeViewBase = findViewById(R.id.cameraView);

        cameraBridgeViewBase.setCvCameraViewListener(new CameraBridgeViewBase.CvCameraViewListener2() {
            @Override
            public void onCameraViewStarted(int width, int height) {
                prevframe_gray = new Mat();
                currframe_gray = new Mat();
                rgb_frame = new Mat();
                diff  = new Mat();
            }

            @Override
            public void onCameraViewStopped() {

            }

            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

                //if its first frame
                if(!is_init) {
                    prevframe_gray = inputFrame.gray();
                    is_init = true;
                    return prevframe_gray;
                }
                //else

                rgb_frame = inputFrame.rgba();          //colorful current frame
                currframe_gray = inputFrame.gray();     //gray current frame

                //find difference between current and prev
                Core.absdiff(currframe_gray,prevframe_gray,diff);
                Imgproc.threshold(diff,diff,40,255,Imgproc.THRESH_BINARY);

                double motionPixels = Core.countNonZero(diff);

                if(motionPixels > motionThreshold){
                    changed_frame_count +=1;
                    Log.d("Changedframe","changed_frame_count "+ changed_frame_count);
                    Log.d("Totalframe","frame_count "+ frame_count);

                    // Convert Mat to byte array
                    byte[] imageData = convertMatToByteArray(rgb_frame);

                    //upload image to firebase
                    String imageName = "image_" + System.currentTimeMillis() + ".jpg";
                    StorageReference imageRef = storage.getReference().child(imageName);

                    imageRef.putBytes(imageData)
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    // Image uploaded successfully
                                    Log.d("ImageStatus","uploaded successfully");
                                    imageRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                        @Override
                                        public void onSuccess(Uri uri) {

                                            Date currentTime = Calendar.getInstance().getTime();
                                            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
                                            String strDate = dateFormat.format(currentTime);

                                            FrameData frameData = new FrameData(imageName,uri.toString(),String.valueOf(latitude),String.valueOf(longitude),strDate);
                                            database.getReference("Images").child("Image").push()
                                                    .setValue(frameData).addOnSuccessListener(new OnSuccessListener<Void>() {
                                                        @Override
                                                        public void onSuccess(Void unused) {
                                                            Log.d("RealtimeDatabase","Image Url uploaded " +latitude +" and "+longitude);
                                                        }
                                                    });
                                        }
                                    });


                                } else {
                                    // Handle image upload failure
                                }
                            })
                            .addOnFailureListener(e -> {
                                // Handle image upload failure
                                Log.d("ImageStatus","Error... not uploaded successfully");

                            });

                }
                frame_count +=1;


                //finally
                prevframe_gray = currframe_gray.clone();
                return diff;
            }
        });


        if (OpenCVLoader.initDebug()) {
            //success
            cameraBridgeViewBase.enableView();
        }
        else Log.d("Loaded","Error");
    }



    @Override
    protected void onResume() {
        super.onResume();
        cameraBridgeViewBase.enableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraBridgeViewBase.disableView();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraBridgeViewBase.disableView();
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(cameraBridgeViewBase);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    void getPermission(){
        if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA},101);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == 101){
            if(grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED ){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    getPermission();
    //                getLocation();
                }
            }
        }


    }

    private byte[] convertMatToByteArray(Mat mat) {
        Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bitmap);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        try {
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return byteArray;
    }


//    @Override
//    public void onLocationChanged(@NonNull Location location) {
//
//    }
//
//    @Override
//    public void onLocationChanged(@NonNull List<Location> locations) {
//        LocationListener.super.onLocationChanged(locations);
//    }
//
//    @Override
//    public void onFlushComplete(int requestCode) {
//        LocationListener.super.onFlushComplete(requestCode);
//    }
//
//    @Override
//    public void onStatusChanged(String provider, int status, Bundle extras) {
//        LocationListener.super.onStatusChanged(provider, status, extras);
//    }
//
//    @Override
//    public void onProviderEnabled(@NonNull String provider) {
//        LocationListener.super.onProviderEnabled(provider);
//    }
//
//    @Override
//    public void onProviderDisabled(@NonNull String provider) {
//        LocationListener.super.onProviderDisabled(provider);
//    }
}