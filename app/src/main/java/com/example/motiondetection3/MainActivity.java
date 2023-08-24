package com.example.motiondetection3;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

//import com.example.motiondetection3.assets.Yolov7ModelMetadata;
//import com.example.motiondetection3.ml.SsdMobilenetV1Metadata;
import com.example.motiondetection3.ml.BestFp16;

//import com.example.motiondetection3.ml.Yolov7ModelMetadata;
import com.example.motiondetection3.ml.SsdMobilenetV11Metadata1;
//import com.example.motiondetection3.ml.SsdMobilenetV1Metadata;
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
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

public class MainActivity extends CameraActivity {

    CameraBridgeViewBase cameraBridgeViewBase;
    Mat prevframe_gray,currframe_gray,rgb_frame,diff;

    double motionThreshold = 5000;

    boolean is_init;

    double longitude;
    double latitude;

    FirebaseDatabase database;
    FirebaseStorage storage;

    LocationManager locationManager;

    private List<String> labels;


    private final Paint paint = new Paint();

    List<String> DetectedClasses;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //get camera and location permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getPermission();
        }


        //

        try {
            labels = FileUtil.loadLabels(this,"labels.txt");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

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
                Imgproc.threshold(diff,diff,70,255,Imgproc.THRESH_BINARY);

                double motionPixels = Core.countNonZero(diff);

                if(motionPixels > motionThreshold){


                    // Convert Mat to byte array
                    Mat rotatedMat = rotateMat(rgb_frame); //rotate by 90 degree
                    System.out.println("rows"+rotatedMat.rows());
                    System.out.println("cols"+rotatedMat.cols());

                    byte[] imageData = convertMatToByteArray(rotatedMat);

                    //extra
                    // Convert Mat to Bitmap
                    Bitmap bitmapImage = Bitmap.createBitmap(rotatedMat.cols(), rotatedMat.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(rotatedMat, bitmapImage);

                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmapImage, 300, 300, false);

                    Mat matImage = new Mat();

                    // Convert Bitmap to Mat
                    Utils.bitmapToMat(scaledBitmap, matImage);

                    System.out.println("rows2"+matImage.rows());
                    System.out.println("cols2"+matImage.cols());




                    Mat finalmat = new Mat();

                    try {
                        SsdMobilenetV11Metadata1 model = SsdMobilenetV11Metadata1.newInstance(MainActivity.this);

                        // Creates inputs for reference.
                        TensorImage image = TensorImage.fromBitmap(scaledBitmap);

                        // Runs model inference and gets result.
                        SsdMobilenetV11Metadata1.Outputs outputs = model.process(image);
                        TensorBuffer locations = outputs.getLocationsAsTensorBuffer();
                        TensorBuffer classes = outputs.getClassesAsTensorBuffer();
                        TensorBuffer scores = outputs.getScoresAsTensorBuffer();
                        TensorBuffer numberOfDetections = outputs.getNumberOfDetectionsAsTensorBuffer();


                        Bitmap mutablebitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, true);
                        Canvas canvas = new Canvas(mutablebitmap);

                        int h = mutablebitmap.getHeight();
                        int w = mutablebitmap.getWidth();

                        paint.setTextSize(h / 15f);
                        paint.setStrokeWidth(h / 85f);

                        int x = 0;
                        int numDetections = (int) numberOfDetections.getFloatValue(0);

                        DetectedClasses=new ArrayList<String>();

                        for (int index = 0; index < numDetections; index++) {
                            float fl = scores.getFloatValue(index);
                            x = index * 4;


                            if (fl > 0.5) {
                                DetectedClasses.add(labels.get(classes.getIntValue(index)));

                                if(labels.get(classes.getIntValue(index)).equals("person") || labels.get(classes.getIntValue(index)).equals("elephant") || labels.get(classes.getIntValue(index)).equals("zebra")){


                                paint.setColor(Color.YELLOW);
                                paint.setStyle(Paint.Style.STROKE);
                                canvas.drawRect(
                                        new RectF(
                                                locations.getFloatValue(x + 1) * w,
                                                locations.getFloatValue(x) * h,
                                                locations.getFloatValue(x + 3) * w,
                                                locations.getFloatValue(x + 2) * h
                                        ),
                                        paint
                                );
                                paint.setStyle(Paint.Style.FILL);
                                canvas.drawText(
                                        labels.get(classes.getIntValue(index)) + " " + String.valueOf(fl),
                                        locations.getFloatValue(x + 1) * w,
                                        locations.getFloatValue(x) * h,
                                        paint
                                );
                                }
                            }
                        }
                        Utils.bitmapToMat(mutablebitmap, finalmat);
                        // Releases model resources if no longer used.
                        model.close();
                    } catch (IOException e) {
                        // TODO Handle the exception
                    }

                    byte[] imageData2 = convertMatToByteArray(finalmat);


//                    //ml part start
//                    try {
//                        BestFp16 model = BestFp16.newInstance(MainActivity.this);
//
////                        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(416 * 416 * 3 * 4); // 4 bytes per float
////                        byteBuffer.order(ByteOrder.nativeOrder());
////
////                        System.out.println("imagedata2 length that is number of float values"+imageData2.length);
////                        for (byte value : imageData2) {
////                            // Convert byte value to float and normalize/scale if needed
////                            float floatValue = (value & 0xFF) / 255.0f; // Example conversion, adjust as needed
////                            byteBuffer.putFloat(floatValue);
////                        }
//                        ByteBuffer byteBuffer = convertMatToByteBuffer(matImage);
//                        int bufferSize = byteBuffer.capacity();
//                        System.out.println("ByteBuffer size: " + bufferSize + " bytes");
//
//
//                        //byteBuffer.flip();
////                        ByteBuffer byteBuffer = ByteBuffer.wrap(imageData);
//                        // Creates inputs for reference.
//                        TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 416, 416, 3}, DataType.FLOAT32);
//                        inputFeature0.loadBuffer(byteBuffer);
//
//                        // Runs model inference and gets result.
//                        BestFp16.Outputs outputs = model.process(inputFeature0);
//                        TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
//
//                        float[] outputArray = outputFeature0.getFloatArray();
//                        Log.d("Output Size"," :"+outputArray.length);
//                        Log.d("Output [0] "," :"+outputArray[0]);
//                         // Obtain the output array
//
//                        int numPredictions = 10647;
//                        int numAttributesPerPrediction = 9;
//                        int numClasses = 4; // Number of classes
//
//                        float objectnessThreshold = 0.5f; // Objectness score threshold
//                        float classScoreThreshold = 0.5f; // Class score threshold
//                        float nmsThreshold = 0.3f; // Non-maximum suppression threshold
//
//// Store detection results
//                        List<DetectionResult> results = new ArrayList<>();
//
//                        for (int i = 0; i < numPredictions; i++) {
//                            int startIndex = i * numAttributesPerPrediction;
//                            float[] predictionAttributes = Arrays.copyOfRange(outputArray, startIndex, startIndex + numAttributesPerPrediction);
//
//                            float objectnessScore = predictionAttributes[4]; // Objectness score
//                            float[] classScores = Arrays.copyOfRange(predictionAttributes, 5, 9); // Class scores
//
//                            // Find the class index with the highest score
//                            int maxClassIndex = 0;
//                            float maxClassScore = classScores[0];
//                            for (int classIndex = 1; classIndex < numClasses; classIndex++) {
//                                if (classScores[classIndex] > maxClassScore) {
//                                    maxClassIndex = classIndex;
//                                    maxClassScore = classScores[classIndex];
//                                }
//                            }
//
//                            // If the objectness score and class score are above the thresholds
//                            if (objectnessScore > objectnessThreshold && maxClassScore > classScoreThreshold) {
//                                // Extract class label
//                                String detectedClass = "";
//                                switch (maxClassIndex) {
//                                    case 0:
//                                        detectedClass = "elephant";
//                                        break;
//                                    case 1:
//                                        detectedClass = "buffalo";
//                                        break;
//                                    case 2:
//                                        detectedClass = "rhino";
//                                        break;
//                                    case 3:
//                                        detectedClass = "person";
//                                        break;
//                                    default:
//                                        break;
//                                }
//
//                                // Process the prediction: bounding box coordinates, detected class, confidence score
//                                float xCenter = predictionAttributes[0];
//                                float yCenter = predictionAttributes[1];
//                                float width = predictionAttributes[2];
//                                float height = predictionAttributes[3];
//
//                                float confidenceScore = objectnessScore * maxClassScore;
//
//                                // Store the detection result
//                                results.add(new DetectionResult(detectedClass, xCenter, yCenter, width, height, confidenceScore));
//                            }
//                        }
//                        Log.d("Result count",""+results.size());
//
//// Perform non-maximum suppression
//                        List<DetectionResult> finalResults = performNonMaxSuppression(results, nmsThreshold);
//
//                        System.out.println("final result size :"+finalResults.size());
//// Print or handle the final detection results
//                        for (DetectionResult result : finalResults) {
//                            System.out.println("Detected: " + result.detectedClass);
//                            System.out.println("Coordinates: x=" + result.xCenter + ", y=" + result.yCenter + ", width=" + result.width + ", height=" + result.height);
//                            System.out.println("Confidence: " + result.confidenceScore);
//                        }
//
//// ...
//
//
//                        // Releases model resources if no longer used.
//                        model.close();
//                    } catch (IOException e) {
//                        // TODO Handle the exception
//                    }

                    //ml part end

                    //upload image to firebase
                    String imageName = "image_" + System.currentTimeMillis() + ".jpg";
                    StorageReference imageRef = storage.getReference().child(imageName);


                    imageRef.putBytes(imageData2)
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

                                            FrameData frameData = new FrameData(imageName,uri.toString(),String.valueOf(latitude),String.valueOf(longitude),strDate,DetectedClasses);
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
                                Log.d("ImageStatus","Error... not uploaded successfully "+ e);


                            });

                }


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

    private ByteBuffer convertMatToByteBuffer(Mat mat) {
        int numBytesPerChannel = 4; // Assuming 4 bytes per float value (32-bit float)
        int numChannels = 3; // Number of color channels (e.g., RGB)

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(mat.rows() * mat.cols() * numChannels * numBytesPerChannel);
        byteBuffer.order(ByteOrder.nativeOrder()); // Set byte order

        for (int row = 0; row < mat.rows(); row++) {
            for (int col = 0; col < mat.cols(); col++) {
                double[] pixelValues = mat.get(row, col);
                for (int channel = 0; channel < numChannels; channel++) {
                    float floatValue = (float) pixelValues[channel];
                    byteBuffer.putFloat(floatValue);
                }
            }
        }
        byteBuffer.flip(); // Prepare buffer for reading
        return byteBuffer;
    }

    public Mat rotateMat(Mat sourceMat) {
        Mat rotatedMat = new Mat();
        Core.transpose(sourceMat, rotatedMat);
        Core.flip(rotatedMat, rotatedMat, 1);
        return rotatedMat;
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
//private List<DetectionResult> performNonMaxSuppression(List<DetectionResult> results, float threshold) {
//    List<DetectionResult> filteredResults = new ArrayList<>();
//
//    Set<Integer> toKeepIndices = new HashSet<>();
//
//    // Sort the results by confidence score in descending order
//    results.sort((result1, result2) -> Float.compare(result2.confidenceScore, result1.confidenceScore));
//
//    for (int i = 0; i < results.size(); i++) {
//        if (toKeepIndices.contains(i)) {
//            continue; // Skip suppressed detections
//        }
//
//        DetectionResult currentResult = results.get(i);
//        toKeepIndices.add(i);
//
//        for (int j = i + 1; j < results.size(); j++) {
//            if (toKeepIndices.contains(j)) {
//                continue; // Skip suppressed detections
//            }
//
//            DetectionResult otherResult = results.get(j);
//            float iou = calculateIoU(currentResult, otherResult);
////            System.out.println("iou : "+iou);
//
//            if (iou > threshold) {
//                System.out.println("yes it is overlapping");
//                if (currentResult.confidenceScore < otherResult.confidenceScore) {
//                    toKeepIndices.remove(i);
//                    break;
//                } else {
//                    toKeepIndices.remove(j);
//                }
//            }
//        }
//    }
//
//    for (Integer index : toKeepIndices) {
//        filteredResults.add(results.get(index));
//    }
//    Log.d("Filterd size",""+filteredResults.size());
//    return filteredResults;
//
//}
//    private float calculateIoU(DetectionResult box1, DetectionResult box2) {
//        float x1 = box1.xCenter - box1.width / 2;
//        float y1 = box1.yCenter - box1.height / 2;
//        float x2 = box1.xCenter + box1.width / 2;
//        float y2 = box1.yCenter + box1.height / 2;
//
//        float x3 = box2.xCenter - box2.width / 2;
//        float y3 = box2.yCenter - box2.height / 2;
//        float x4 = box2.xCenter + box2.width / 2;
//        float y4 = box2.yCenter + box2.height / 2;
//
//        float intersectionArea = Math.max(0, Math.min(x2, x4) - Math.max(x1, x3)) *
//                Math.max(0, Math.min(y2, y4) - Math.max(y1, y3));
//
//
//        float box1Area = (x2 - x1) * (y2 - y1);
//        float box2Area = (x4 - x3) * (y4 - y3);
//
//        System.out.println("box1 (x1,y1,w,h) ->"+"("+box1.xCenter+","+box1.yCenter+","+box1.width+","+box1.height+","+")");
//        System.out.println("box2 (x2,y2,w,h) ->"+"("+box2.xCenter+","+box2.yCenter+","+box2.width+","+box2.height+","+")");
//
//
//
//        System.out.println("box1Area: "+box1Area);
//        System.out.println("box2Area: "+ box2Area);
//        System.out.println("INtersection area: "+intersectionArea);
//
//        float iou = intersectionArea / (box1Area + box2Area - intersectionArea);
//        return iou;
//    }
//
//    class DetectionResult {
//        String detectedClass;
//        float xCenter;
//        float yCenter;
//        float width;
//        float height;
//        float confidenceScore;
//
//        public DetectionResult(String detectedClass, float xCenter, float yCenter, float width, float height, float confidenceScore) {
//            this.detectedClass = detectedClass;
//            this.xCenter = xCenter;
//            this.yCenter = yCenter;
//            this.width = width;
//            this.height = height;
//            this.confidenceScore = confidenceScore;
//        }
//    }
}