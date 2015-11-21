package org.opencv.samples.colorblobdetect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.Imgproc;
import org.opencv.ocrinterface.OCR;
import org.opencv.core.Rect;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.widget.Toast;

public class ColorBlobDetectionActivity extends Activity implements OnTouchListener, CvCameraViewListener2 {
    private static final String  TAG              = "OCVSample::Activity";

    private boolean              mIsColorSelected = true;
    private Mat                  mRgba;
    private Scalar               mBlobColorRgba;
    private Scalar               mBlobColorHsv;
    private ColorBlobDetector    mDetector;
    private Mat                  mSpectrum;
    private Size                 SPECTRUM_SIZE;
    private Scalar               CONTOUR_COLOR;
    private Rect imagerect = new Rect();
    private Point base = new Point();
    private CameraBridgeViewBase mOpenCvCameraView;
    private OCR mOCR = new OCR();
    static {

        if (!OpenCVLoader.initDebug()) {

            // Handle initialization error

        }

    }
    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(ColorBlobDetectionActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public ColorBlobDetectionActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.color_blob_detection_surface_view);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(10,202,164);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255,0,0,255);
        //mDetector.setHsvColor(mBlobColorHsv);
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public boolean onTouch(View v, MotionEvent event) {
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
        int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;

        int x = (int)event.getX() - xOffset;
        int y = (int)event.getY() - yOffset;

        Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        Rect touchedRect = new Rect();

        touchedRect.x = (x>4) ? x-4 : 0;
        touchedRect.y = (y>4) ? y-4 : 0;

        touchedRect.width = (x+4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y+4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // Calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width*touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;

        mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

        Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        Log.i(TAG, "Touched mBlobColorHsv color: (" + mBlobColorHsv.val[0] + ", " + mBlobColorHsv.val[1] +
                ", " + mBlobColorHsv.val[2] + ", " + mBlobColorHsv.val[3] + ")");
        mDetector.setHsvColor(mBlobColorHsv);

        Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

        mIsColorSelected = true;

        touchedRegionRgba.release();
        touchedRegionHsv.release();

        return false; // don't need subsequent touch events
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();

        if (mIsColorSelected) {
            Point p = mDetector.process(mRgba);
            drawRect(p);
            List<MatOfPoint> contours = mDetector.getContours();
            Log.e(TAG, "Contours count: " + contours.size());
            Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR);

            Mat colorLabel = mRgba.submat(4, 68, 4, 68);
            colorLabel.setTo(mBlobColorRgba);

            Mat spectrumLabel = mRgba.submat(4, 4 + mSpectrum.rows(), 70, 70 + mSpectrum.cols());
            mSpectrum.copyTo(spectrumLabel);
        }

        return mRgba;
    }
    private int imageCount;
    private void drawRect(Point center){
    	
    	Point pt1 = new Point();
    	pt1.x = center.x-50;
    	pt1.y= center.y-100;
    	if(pt1.x<0){
    		pt1.x = 0;
    	}
    	if(pt1.y<0){
    		pt1.y = 0;
    	}
    	Point pt2 = new Point();;
    	pt2.x = center.x+50;
    	pt2.y= center.y;
    	if(pt2.x<0){
    		pt2.x = 0;
    	}
    	if(pt2.y<0){
    		pt2.y = 0;
    	}
    	Imgproc.rectangle(mRgba, pt1, pt2, new Scalar(255,255,0));
    	imagerect.x = (int) pt1.x;
    	imagerect.y = (int) pt1.y;
    	imagerect.width = 100;
    	imagerect.height = 100;
    	addimage();
    	filter(center);
    	
    }

    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);
        return new Scalar(pointMatRgba.get(0, 0));
    }
    private int count = 0;
    private boolean filter(Point p){
    	double b = (p.x-base.x)*(p.x-base.x)+(p.y-base.y)*(p.y-base.y);
    	b = Math.sqrt(b);
    	if(b < 20){
    		count++;
    	}else{
    		count = 0;
    	}
    	base.x = p.x;
    	base.y = p.y;
    	count++;
    	if( count==5){
    		
    		Log.i("junjiedebug","call image");
     		count = 0;
    		Bitmap bitmap = copyImage(mRgba);
        	imageCount++;
        	String fullname = getSDPath()+"/a/"+imageCount+".bmp";
        	saveBitmap(bitmap,fullname);
        	mOCR.startOCR(bitmap);
    	}
    	
    	return true;
    }
    private void preImage(){
    	
    }
    public Bitmap copyImage(Mat src){
    	 
    	Mat image_roi = new Mat(src,imagerect);
    	Bitmap b =  Bitmap.createBitmap(image_roi.cols(), image_roi.rows(), Bitmap.Config.ARGB_8888);
    	Utils.matToBitmap(image_roi, b);
    	return b;
    }
    private void addimage(){
    	 
    	if( imagerect.x < 100 || imagerect.x> mRgba.cols()-100){
    		return;
    	}
    	if( imagerect.y < 100 || imagerect.y> mRgba.rows()-100){
    		return;
    	}
    	Mat image_roi = new Mat(mRgba,imagerect);
    	
    	Rect rect = new Rect(100,100,100,100);
    	Mat image1 = new Mat(mRgba,rect);
    	image_roi.copyTo(image1);
    }
    public static String getSDPath() {  
        File sdDir = null;  
        boolean sdCardExist = Environment.getExternalStorageState().equals(  
                android.os.Environment.MEDIA_MOUNTED);  
        if (sdCardExist) {  
            sdDir = Environment.getExternalStorageDirectory(); 
        } else {  
            Log.e("ERROR", "Ã»ÓÐÄÚ´æ¿¨");  
        }  
        return sdDir.toString();  
    }  
    public boolean saveBitmap(Bitmap bmp,String fullFilePath) {  
        if(fullFilePath==null||fullFilePath.length()<1){  
            Log.e("junjiedebug", "saveBitmap error as filePath invalid");  
            return false;  
        }  
        Log.i("junjiedebug","save " +fullFilePath);
        FileOutputStream fos = null;  
        File file = new File(fullFilePath);  
        if(file.exists())return false;  
        isExistsFilePath();
        try {  
            fos = new FileOutputStream(file);  
            if (null != fos) {  
                bmp.compress(Bitmap.CompressFormat.PNG, 90, fos);  
                fos.flush();  
                fos.close();  
                return true;  
            }  
        } catch (IOException e) {  
            Log.e("junjiedebug", "saveBitmap fail as "+e.getMessage());  
        }  
        return false;  
    }  
    private static String isExistsFilePath() {  
        String filePath = getSDPath() + "/a/";  
        File file = new File(filePath);  
        if (!file.exists()) {  
            file.mkdirs();  
        }  
        return filePath;  
    }     
}
