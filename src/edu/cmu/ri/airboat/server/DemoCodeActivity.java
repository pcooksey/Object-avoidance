package edu.cmu.ri.airboat.server;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Core;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;

import edu.cmu.ri.crw.VehicleServer;
import edu.cmu.ri.crw.data.Twist;

import android.os.Bundle;
import android.os.IBinder;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;

/// Twist { x, y, x, roll, pitch, yaw }

public class DemoCodeActivity extends Activity implements CvCameraViewListener2 {
	
	public static final String TAG = DemoCodeActivity.class.getName();
	private CameraBridgeViewBase mOpenCvCameraView;
	private AirboatService vehicleService = null;
	
	public static final int VIEW_MODE_RGBA = 0;
    public static final int VIEW_MODE_BW = 1;
    
    public static int viewMode = VIEW_MODE_BW;
	
	private MenuItem mItemRGBA;
    private MenuItem mItemBW;
    
	double thrust = .01;
	double angle = 0;
	
	boolean _isBound = false;
	
	/** 
	* Listener that handles changes in connections to the airboat service 
	*/ 
	private ServiceConnection _connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
	        // This is called when the connection with the service has been
	    	// established, giving us the service object we can use to
	    	// interact with the service.
	    	vehicleService = ((AirboatService.AirboatBinder)service).getService();
	    }
	
    	public void onServiceDisconnected(ComponentName className) {
    		// This is called when the connection with the service has been
    		// unexpectedly disconnected -- that is, its process crashed.
	        vehicleService = null;
	    }
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "called onCreate");
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_demo_code);
		mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.DemoCodeCvView);
		mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
		mOpenCvCameraView.setCvCameraViewListener(this);
		
		doBindService();
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		mItemRGBA  = menu.add("Normal");
        mItemBW  = menu.add("B&W");
		getMenuInflater().inflate(R.menu.demo_code, menu);
		return true;
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        if (item == mItemRGBA)
            viewMode = VIEW_MODE_RGBA;
        if (item == mItemBW)
            viewMode = VIEW_MODE_BW;
        return true;
    }
	
	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
	    @Override
	    public void onManagerConnected(int status) {
	        switch (status) {
	            case LoaderCallbackInterface.SUCCESS:
	            {
	                Log.i(TAG, "OpenCV loaded successfully");
	                mOpenCvCameraView.enableView();
	            } break;
	            default:
	            {
	                super.onManagerConnected(status);
	            } break;
	        }
	    }
	};

	@Override
	public void onResume()
	{
	    super.onResume();
	    OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
	}
	
	 @Override
	 public void onPause()
	 {
	     super.onPause();
	     if (mOpenCvCameraView != null)
	         mOpenCvCameraView.disableView();
	 }

	 public void onDestroy() {
	     super.onDestroy();
	     doUnbindService();
	     if (mOpenCvCameraView != null)
	         mOpenCvCameraView.disableView();
	 }

	 public void onCameraViewStarted(int width, int height) {
	 }

	 public void onCameraViewStopped() {
	 }

	 public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		Mat img = inputFrame.rgba(), img_hue = new Mat(); 
		
		switch (viewMode) {
			case VIEW_MODE_RGBA:
            
				double middle = img.width()/2;
				boolean drawCircles = false;
				
				// Creating control and setting thrust
				Twist twist = new Twist();
				twist.dx(thrust);
				
				/// Convert it to hue, convert to range color, and blur to remove false circles
				Imgproc.cvtColor(img, img_hue, Imgproc.COLOR_RGB2HSV);//COLOR_BGR2HSV);
				//Core.inRange(img_hue, new Scalar(10,100,100), new Scalar(15,255,255), img_hue); //Orange
				Core.inRange(img_hue, new Scalar(160,0,0), new Scalar(179,255,255), img_hue);
				Imgproc.GaussianBlur(img_hue, img_hue, new Size(9,9), 10, 10 );
				
				/// Create mat for circles
				Mat circles = new Mat();
				/// Apply the Hough Transform to find the circles
				Imgproc.HoughCircles(img_hue, circles, Imgproc.CV_HOUGH_GRADIENT, 3, img_hue.rows()/4, 200, 70, 5, 100 );
				
				if(circles.cols()==1)
				{
					drawCircles = true;
					Point center = new Point(middle, circles.get(0, 0)[1]);
					Core.putText(img, ""+ angle, center, Core.FONT_HERSHEY_COMPLEX, .8, new Scalar(255,0,0));
				}
				else if(circles.cols()==2 && Math.abs((circles.get(0,0)[1]-(circles.get(0,1)[1])))<=20)
				{
					drawCircles = true;
					
					Point center = new Point(middle, circles.get(0, 0)[1]);
					double dist1 = circles.get(0,0)[0]-middle, dist2 = circles.get(0, 1)[0]-middle;
					double absdist = Math.abs(Math.abs(dist1) - Math.abs(dist2));
					double centerViewPoint = img.height()-circles.get(0, 0)[1];
					
					// Distance1 is negative and distance2 is positive
					if(dist1<0 && dist2>0)
					{
						if(Math.abs(dist1)<=Math.abs(dist2))
						{
							angle = - Math.atan(absdist/centerViewPoint);
							Log.i(TAG, "Angle1: "+angle);
						} else {
							angle = Math.atan(absdist/centerViewPoint);
							Log.i(TAG, "Angle2: "+angle);
						}
					}
					// Distance2 is negative and distance1 is positive
					else if(dist2<0 && dist1>0)
					{
						if(Math.abs(dist2)<=Math.abs(dist1))
						{
							angle = - Math.atan(absdist/centerViewPoint);
							Log.i(TAG, "Angle3: "+angle);
						} else {
							angle = Math.atan(absdist/centerViewPoint);
							Log.i(TAG, "Angle4: "+angle);
						}
					}
					else if(dist1<=0 && dist2<=0)
					{
						angle = Math.atan(absdist/centerViewPoint);
						Log.i(TAG, "Angle5: "+angle);
					} 
					else if(dist1>=0 && dist2>=0)
					{
						angle = - Math.atan(absdist/centerViewPoint);
						Log.i(TAG, "Angle6: "+angle);
					}
					twist.drz(angle);
					Core.putText(img, ""+ angle, center, Core.FONT_HERSHEY_COMPLEX, .8, new Scalar(255,0,0));
					sendTwist(twist);
				} 
				
				
				// Draw the circles!
				for(int x=0; drawCircles==true && x<circles.cols(); x++)
				{
					double circle[] = circles.get(0,x);
					int ptx = (int) Math.round(circle[0]), pty = (int) Math.round(circle[1]);
					Point pt = new Point(ptx, pty);
					int radius = (int)Math.round(circle[2]);
					
					// Draw the circle outline
			        Core.circle(img, pt, radius, new Scalar(0,255,0), 3, 8, 0 );
			        // Draw the circle center
			        Core.circle(img, pt, 3, new Scalar(0,255,0), -1, 8, 0 );
				}
				break;
			case VIEW_MODE_BW:
				/// This mode displays image in black/white to show what the algorithm sees 
				Imgproc.cvtColor(img, img_hue, Imgproc.COLOR_RGB2HSV);;
				//Core.inRange(img_hue, new Scalar(10,100,100), new Scalar(15,255,255), img_hue); //Orange
				Core.inRange(img_hue, new Scalar(161,0,0), new Scalar(177,255,255), img_hue);
				Imgproc.GaussianBlur(img_hue, img_hue, new Size(9,9), 10, 10 );
				img = img_hue;
				break;
			default:
				break;
		}
		
		return img;
	 }

	 private void sendTwist(Twist twist){
		 VehicleServer vehicleServer = vehicleService.getServer();
		 if(vehicleServer!=null)
			 vehicleServer.setVelocity(twist);
		 else
			 Log.e(TAG,"Not connected to server");
	 }
	 
	 private double getYaw(){
		 VehicleServer vehicleServer = vehicleService.getServer();
		 if(vehicleServer!=null)
			 return vehicleServer.getPose().pose.getRotation().toYaw();	
		 else{
			 Log.e(TAG,"Not connected to server");
			 return 0;
		 }
	 }
	
	 void doBindService() {
	    // Establish a connection with the service.  We use an explicit
	    // class name because we want a specific service implementation.
	    if (!_isBound) {
	    	bindService(new Intent(this, AirboatService.class), _connection, Context.BIND_AUTO_CREATE);
	    	_isBound = true;
	    }
	 }
	
	 void doUnbindService() {
	    // Detach our existing connection.
    	if (_isBound) {
            unbindService(_connection);
            _isBound = false;
        }
	 }
	 
}
