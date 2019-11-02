package com.iccl.ar;

import android.support.v7.app.AppCompatActivity;

import android.*;
import android.annotation.*;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.graphics.Paint.*;
import android.graphics.drawable.*;
import android.hardware.camera2.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.support.v4.app.*;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.util.*;
import android.view.*;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import com.iccl.ar.HomeListen.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.text.*;
import java.util.*;

import org.json.*;

@SuppressWarnings("deprecation")
@SuppressLint({"NewApi", "HandlerLeak"})
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CameraToo ";
    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;
    private static String ip = "192.168.0.100";
    private static int port = 10000;

    private int rotation;
    private String UImsg = "";
    private String mCameraID;// 摄像头Id 0 为后 1 为前
    private TextView mTextView;
    private EditText mEditText1, mEditText2;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private ImageView mCanvas;
    private DisplayMetrics metrics = new DisplayMetrics();
    private Handler mTagHandler, childHandler, mainHandler;
    private HandlerThread mBackgroundThread;
    private HomeListen mHomeListen = null;
    private boolean home = false;
    private Bitmap bitmap = null;
    private DatagramSocket mdmsgSocket = null;
    private DatagramSocket mfileSocket = null;
    private ImageReader mCaptureBuffer, mImageReader;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraDevice mCameraDevice;
    private CameraManager mCameraManager;// 摄像头管理器

    protected void onResume() {
        super.onResume();
        if (checkPermission()) {
            showPermission();
        } else {
            setStart();
        }
        if (home) {
            mHomeListen.start();
        }
    }

    protected void onPause() {
        super.onPause();
        if (home) {
            mHomeListen.stop();
        }
        if (!checkPermission()) {
            mSurfaceView.getHolder().setFixedSize(/* width */0, /* height */0);

            if (mCameraCaptureSession != null) {
                mCameraCaptureSession.close();
                mCameraCaptureSession = null;
            }
            mBackgroundThread.quitSafely();
            try {
                mBackgroundThread.join();
            } catch (InterruptedException ex) {
                Log.e(TAG, "Background worker thread was interrupted while joined", ex);
            }
            if (mCaptureBuffer != null)
                mCaptureBuffer.close();
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                onDestroy();
                return true;
            default:
                break;
        }
        return false;
    }

    private void setEdit() {
        // Create LinearLayout Dynamically
        LinearLayout layout = new LinearLayout(this);

        // Setup Layout Attributes
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.setLayoutParams(params);
        layout.setOrientation(LinearLayout.VERTICAL);

        // Create a EditText to add to layout
        mEditText1 = new EditText(this);
        mEditText1.setHint("IP:" + ip);
        String digits = "0123456789.";
        final String url = "http://192.168.1.115/etaprowe";
        mEditText1.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        mEditText1.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
        mEditText1.setKeyListener(DigitsKeyListener.getInstance(digits));
        mEditText2 = new EditText(this);
        mEditText2.setHint("Port:" + port);
        mEditText2.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        mEditText2.setInputType(InputType.TYPE_CLASS_NUMBER);
        TextView mTextView1 = new TextView(this);
        mTextView1.setText(url);
        mTextView1.setTextSize(20);
        mTextView1.setTextColor(Color.BLUE);
        mTextView1.setPadding(8, 8, 8, 8);
        // Add Views to the layout
        layout.addView(mEditText1);
        layout.addView(mEditText2);
        layout.addView(mTextView1);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("Edit");
        builder.setView(layout);
        mTextView1.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            // do something when the button is clicked
            public void onClick(DialogInterface arg0, int arg1) {
                boolean edit = false;
                if (mEditText1.getText().toString().length() > 0) {
                    ip = mEditText1.getText().toString();
                    edit = true;
                }
                if (mEditText2.getText().toString().length() > 0) {
                    port = Integer.parseInt(mEditText2.getText().toString());
                    edit = true;
                }
                if (edit) {
                    toast("Complete.");
                } else {
                    toast("Please check your IP/Port and try again.");
                }
            }
        });
        builder.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
            // do something when the button is clicked
            public void onClick(DialogInterface arg0, int arg1) {
                // ...
            }
        });
        // Show the custom AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void setHOME() {
        home = true;
        mHomeListen = new HomeListen(this);
        mHomeListen.setOnHomeBtnPressListener(new OnHomeBtnPressLitener() {
            public void onHomeBtnPress() {
                android.os.Process.killProcess(android.os.Process.myPid());
            }

            public void onHomeBtnLongPress() {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });
    }

    private boolean checkPermission() {
        int CAMERA = checkSelfPermission(Manifest.permission.CAMERA);
        int STORAGE = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return permission(CAMERA) || permission(STORAGE);
    }

    @TargetApi(23)
    @SuppressLint("NewApi")
    private void showPermission() {
        if (checkPermission()) {
            // We don't have permission so prompt the user
            List<String> permissions = new ArrayList<String>();
            permissions.add(Manifest.permission.CAMERA);
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            requestPermissions(permissions.toArray(new String[permissions.size()]), 0);
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 0:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 許可授權
                    setStart();
                } else {
                    // 沒有權限
                    toast("未授權應用使用權限!");
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean permission(int mp) {
        return mp != PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("InflateParams")
    private void setStart() {
        metrics = getResources().getDisplayMetrics();
        // int mWidth = metrics.widthPixels; // 螢幕寬
        // int mHeight = metrics.heightPixels; // 螢幕長
        // toast("w:" + mWidth + ",h:" + mHeight);
        mBackgroundThread = new HandlerThread("background");
        mBackgroundThread.start();
        new Handler(mBackgroundThread.getLooper());
        new Handler(getMainLooper());
        mTagHandler = new Handler();
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        setContentView(R.layout.activity_main);
        mCanvas = (ImageView) findViewById(R.id.mainCanvas);
        mTextView = (TextView) findViewById(R.id.mainTextView);
        mSurfaceView = (SurfaceView) findViewById(R.id.mainSurfaceView);
        mSurfaceView.setVisibility(View.VISIBLE);
        mSurfaceView.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // TODO onClick
                setEdit();
            }
        });
        try {
            mdmsgSocket = new DatagramSocket();
            mfileSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        initVIew();
        setHOME();
        mTagHandler.post(mSurfacTag);
    }

    /**
     * 初始化
     */
    private void initVIew() {
        // mSurfaceView
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.setKeepScreenOn(true);
        // mSurfaceView添加回调
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            // SurfaceView创建
            public void surfaceCreated(SurfaceHolder holder) {
                // 初始化Camera
                initCamera2();
            }

            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            public void surfaceDestroyed(SurfaceHolder holder) { // SurfaceView销毁
                // 释放Camera资源
                if (null != mCameraDevice) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
            }
        });
    }

    /**
     * 初始化Camera2
     */
    private void initCamera2() {
        // 获取摄像头管理
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        childHandler = new Handler(handlerThread.getLooper());
        mainHandler = new Handler(getMainLooper());
        mCameraID = "" + CameraCharacteristics.LENS_FACING_FRONT;// 后摄像头
        try {
            mCameraManager.getCameraCharacteristics(mCameraID);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        imagereader();

        try {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            // 打开摄像头
            mCameraManager.openCamera(mCameraID, stateCallback, mainHandler);
            Log.e("Camera", "open");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 摄像头创建监听
     */
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        public void onOpened(CameraDevice camera) {// 打开摄像头
            mCameraDevice = camera;
            // 开启预览
            takePreview();
        }

        public void onDisconnected(CameraDevice camera) {// 关闭摄像头
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        }

        public void onError(CameraDevice camera, int error) {// 发生错误
            toast("前鏡頭開啟失敗");
        }
    };

    /**
     * 开始预览
     */
    private void takePreview() {
        try {
            // 创建预览需要的CaptureRequest.Builder
            final CaptureRequest.Builder previewRequestBuilder = mCameraDevice
                    .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // 将SurfaceView的surface作为CaptureRequest.Builder的目标
            previewRequestBuilder.addTarget(mSurfaceHolder.getSurface());
            // previewRequestBuilder.addTarget(mImageReader.getSurface());
            // 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求
            mCameraDevice.createCaptureSession(Arrays.asList(mSurfaceHolder.getSurface(), mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() // ③
                    {
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            if (null == mCameraDevice) {
                                return;
                            }
                            // 当摄像头已经准备好时，开始显示预览
                            mCameraCaptureSession = cameraCaptureSession;
                            try {
                                // setup3AControlsLocked(previewRequestBuilder);
                                // 自动对焦
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // 打开闪光灯
                                // previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                // CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                                // 显示预览
                                CaptureRequest previewRequest = previewRequestBuilder.build();

                                mCameraCaptureSession.setRepeatingRequest(previewRequest, mPreCaptureCallback,
                                        childHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            toast("配置失敗");
                        }
                    }, childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 拍照
     */
    private void takePicture() {
        if (mCameraDevice == null) {
            return;
        }
        final CaptureRequest.Builder captureRequestBuilder;
        try {
            // TEMPLATE_PREVIEW：創建預覽的請求
            // TEMPLATE_STILL_CAPTURE：創建一個適合於靜態圖像捕獲的請求，圖像質量優先於幀速率。
            // TEMPLATE_RECORD：創建視頻錄製的請求
            // TEMPLATE_VIDEO_SNAPSHOT：創建視視頻錄製時截屏的請求
            // TEMPLATE_ZERO_SHUTTER_LAG：創建一個適用於零快門延遲的請求。在不影響預覽幀率的情況下最大化圖像質量。
            // TEMPLATE_MANUAL：創建一個基本捕獲請求，這種請求中所有的自動控制都是禁用的(自動曝光，自動白平衡、自動焦點)。
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // 将imageReader的surface作为CaptureRequest.Builder的目标
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            // 自动对焦
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 自动曝光
            // captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
            // CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            // 获取手机方向
            rotation = getWindowManager().getDefaultDisplay().getRotation();
            // 拍照
            CaptureRequest mCaptureRequest = captureRequestBuilder.build();
            mCameraCaptureSession.capture(mCaptureRequest, mPreCaptureCallback, childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Runnable mSurfacTag = new Runnable() {
        public void run() {
            // TODO postDelayedTime
            takePicture();
            mTagHandler.postDelayed(this, 1500);
        }
    };

    private CameraCaptureSession.CaptureCallback mPreCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                       TotalCaptureResult result) {
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                        CaptureResult partialResult) {
        }

    };

    private void imagereader() {
        mImageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 1);
        // mImageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG,
        // 1);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            // 可以在这里处理拍照得到的临时照片
            // 例如，写入本地
            public void onImageAvailable(ImageReader reader) {
                Log.e("TAG", "onImageAvailable");
                // 拿到拍照照片数据
                final Image image = reader.acquireNextImage();

                Rect rect = image.getCropRect();
                YuvImage yuvImage = new YuvImage(getDataFromImage(image, COLOR_FormatNV21), ImageFormat.NV21,
                        rect.width(), rect.height(), null);

                try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
                    yuvImage.compressToJpeg(rect, 100, stream);
                    bitmap = compBitmap(BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size()));
                    new mSendImageFile().start();
                    image.close();
                    stream.close();
                } catch (FileNotFoundException ex) {
                    Log.e(TAG, "Unable to open output file for writing", ex);
                } catch (IOException ex) {
                    Log.e(TAG, "Failed to write the image to the output file", ex);
                }
            }
        }, mainHandler);
    }

    private class mainJSON {
        JSONArray labels = new JSONArray();
        @SuppressWarnings("unused")
        int type;

        mainJSON(JSONArray json, int type) {
            this.labels = json;
            this.type = type;
        }
    }

    private class labelsJSON {
        JSONArray boxes = new JSONArray();
        double low_alert;
        double high_alert;
        int voltage;
        String detector_name;
        @SuppressWarnings("unused")
        String dets;

        labelsJSON(JSONArray boxes, double low_alert, double high_alert, int voltage, String detector_name,
                   String dets) {
            this.boxes = boxes;
            this.low_alert = low_alert;
            this.high_alert = high_alert;
            this.voltage = voltage;
            this.detector_name = detector_name;
            this.dets = dets;
        }
    }

    private void getJSON(String tmp) {
        /*
         * { labels : [ { low_alert : 0.0 , detector_name : B05 , boxes : [
         * 226,133,297,191 ] , voltage : 105 , dets : "" , high_alert : 0.0 } ,
         * { low_alert : 0.0 , detector_name : B05 , boxes : [ 226,133,297,191 ]
         * , voltage : 105 , dets : "" , high_alert : 0.0 } ] , type : 2 } $
         */
        try {
            if (tmp.indexOf('$', 0) != -1) {
                String msg = getLine(tmp, 0, '$');
                JSONObject mainO = new JSONObject(msg);
                mainJSON mainJ = new mainJSON(mainO.getJSONArray("labels"), mainO.getInt("type"));
                if (mainJ.labels.length() > 0) {
                    ArrayList<labelsJSON> labelslist = new ArrayList<labelsJSON>();
                    ArrayList<int[]> boxeslist = new ArrayList<int[]>();
                    for (int lab = 0; lab < mainJ.labels.length(); lab++) {
                        String mj = mainJ.labels.get(lab).toString();
                        JSONObject labelsO = new JSONObject(mj);
                        labelsJSON labelsJ = new labelsJSON(labelsO.getJSONArray("boxes"),
                                labelsO.getDouble("low_alert"), labelsO.getDouble("high_alert"),
                                labelsO.getInt("voltage"), labelsO.getString("detector_name"),
                                labelsO.getString("dets"));
                        labelslist.add(labelsJ);
                        String bj = labelsJ.boxes.get(0).toString();
                        boxeslist.add(StringToIntArray(bj));
                    }
                    ArrayList<Double> rectlist = new ArrayList<Double>();
                    for (int box = 0; box < boxeslist.size(); box++) {
                        if (getIntArray(boxeslist.get(box))) {
                            // 2*1280*960
                            // 2*1280*720
                            // 1*640*480
                            rectlist.add(getPercent(boxeslist.get(box)[0], 640));
                            rectlist.add(getPercent(boxeslist.get(box)[1], 480));
                            rectlist.add(getPercent(boxeslist.get(box)[2], 640));
                            rectlist.add(getPercent(boxeslist.get(box)[3], 480));
                        }
                    }
                    print(rectlist, labelslist);
                } else {
                    mCanvas.setBackgroundDrawable(null);
                }
            }
        } catch (JSONException e) {
        }
    }

    private int[] StringToIntArray(String str) {
        // 將剛剛輸出之 array string 先作去頭去尾處理並用 split 來分開各個項目
        String[] items = str.replaceAll("\\[", "").replaceAll("\\]", "").split(",");
        // items.length 是所有項目的個數
        int[] ary = new int[items.length];
        // 將結果放入 results，並利用 Integer.parseInt 來將整數字串轉換為 int
        for (int i = 0; i < items.length; i++) {
            ary[i] = Integer.parseInt(items[i].trim());
        }
        return ary;
    }

    private boolean getIntArray(int[] array) {
        // 去除負值
        for (int i = 0; i < 4; i++) {
            if (array[i] < 0) {
                return false;
            }
        }
        return true;
    }

    private double getPercent(Integer num, Integer totalPeople) {
        String percent;
        Double p3 = 0.0;
        if (totalPeople == 0) {
            p3 = 0.0;
        } else {
            p3 = num * 0.01 / totalPeople;
        }
        NumberFormat nf = NumberFormat.getPercentInstance();
        nf.setMinimumFractionDigits(2);
        // 控制保留小数点后几位，2：表示保留2位小数点
        percent = nf.format(p3);
        percent = percent.substring(0, percent.length() - 1);
        return Double.parseDouble(percent);
    }

    private void print(ArrayList<Double> rectlist, ArrayList<labelsJSON> labelslist) {
        // int mDaySize = metrics.widthPixels / 100;// 字體大小
        // int mCircle = mDaySize * 2;// 大圓
        Bitmap bitmap = Bitmap.createBitmap((int) mCanvas.getWidth(), (int) mCanvas.getHeight(), Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        mCanvas.setBackgroundDrawable(new BitmapDrawable(bitmap));
        int mWidth = metrics.widthPixels; // 螢幕寬
        int mHeight = metrics.heightPixels; // 螢幕長
        // toast("w:" + mWidth + ",h:" + mHeight);

        Paint mPaint = new Paint();
        for (int i = 0; i < rectlist.size(); i += 4) {
            double high = labelslist.get(i / 4).high_alert;
            double low = labelslist.get(i / 4).low_alert;
            if (high + low > 0) {
                mPaint.setColor(Color.RED);
            } else {
                mPaint.setColor(Color.GREEN);
            }
            // toast("H:"+high+",L"+low);
            mPaint.setStyle(Style.FILL);
            int x1 = (int) (rectlist.get(i) * mWidth);
            int y1 = (int) (rectlist.get(i + 1) * mHeight);
            int x2 = (int) (rectlist.get(i + 2) * mWidth);
            int y2 = (int) (rectlist.get(i + 3) * mHeight);
            int z1 = x2 - x1; // 偏移量x
            int z2 = y2 - y1; // 偏移量y
            y1 += z2;
            y2 += z2;
            // TODO print
            canvas.drawRect(mMove(x1, z1), mMove(y1, z2), mMove(x2, z1), mMove(y2, z2), mPaint);
            mPaint.setColor(Color.WHITE);
            canvas.drawRect(mMove(x1 + 5, z1), mMove(y1 + 5, z2), mMove(x2 - 5, z1), mMove(y2 - 5, z2), mPaint);
            mPaint.setColor(Color.BLACK);
            mPaint.setTypeface(Typeface.DEFAULT_BOLD);
            double mDetectorNameSize = z2 * 0.1;// 字體大小
            mPaint.setTextSize((int) mDetectorNameSize);
            canvas.drawText(labelslist.get(i / 4).detector_name, mMove(x1 + 5, z1),
                    mMove(y1 + 5 + (int) (mDetectorNameSize * 0.8), z2), mPaint);
            double mVoltageSize = z2 * 0.25;
            mPaint.setTextSize((int) mVoltageSize);
            canvas.drawText(labelslist.get(i / 4).voltage + "V", (int) (x1 - (z1 * 0.75)),
                    (int) (y1 - (z2 / 2) + (mVoltageSize * 0.25)), mPaint);
        }
    }

    private int mMove(int x, int z) {
        if (x - z < 0) {
            return 0;
        }
        return x - z;
    }

    private String getLine(String msg, int run, char key) {
        return msg.substring(run).substring(0, msg.substring(run).indexOf(key));
    }

    private void toast(String t) {
        Toast.makeText(this, t + "", Toast.LENGTH_SHORT).show();
    }

    private boolean isImageFormatSupported(Image image) {
        int format = image.getFormat();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
                return true;
        }
        return false;
    }

    private byte[] getDataFromImage(Image image, int colorFormat) {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
        }
        if (!isImageFormatSupported(image)) {
            throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
        }
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = width * height;
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height + 1;
                        outputStride = 2;
                    }
                    break;
                case 2:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = (int) (width * height * 1.25);
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height;
                        outputStride = 2;
                    }
                    break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }
        return data;
    }

    private class mSendImageFile extends Thread {

        public void run() {
            // UTP Socket
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Bitmap bmp = bitmap;
                bmp.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                int size = baos.toByteArray().length;
                size = ((size - 1) / 4096) + 1;
                // toast(size+"");
                sendMsg(mfileSocket, size + "", ip, port);
                sendFile(baos, mfileSocket, ip, port);
                new mReceiveImageInfo().start();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    ;

    private class mReceiveImageInfo extends Thread {
        public void run() {
            byte[] receiveData = new byte[4096];
            try {
                // UDP Socket
                mdmsgSocket.setSoTimeout(3000);
                DatagramPacket receivePacket = new DatagramPacket(receiveData, 4096);
                // 是否接收到数据的标志位
                boolean receivedResponse = false;
                int tries = 0;
                // 直到接收到数据，或者重发次数达到预定值，则退出循环
                while (!receivedResponse && tries < 1) {
                    sendMsg(mdmsgSocket, "255", ip, port);
                    try {
                        mdmsgSocket.receive(receivePacket);
                        String modifiedSentence = new String(receivePacket.getData(), 0, receivePacket.getLength());
                        UImsg = modifiedSentence;
                        receivedResponse = true;
                    } catch (SocketTimeoutException e) {
                        tries++;
                    }
                }
                mUIHandler.obtainMessage().sendToTarget();
            } catch (IOException ex) {
                Log.e(TAG, "Failed to mReceiveImageInfo", ex);
            }
        }
    }

    ;

    private Handler mUIHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            if (UImsg.equals(""))
                mTextView.setText("Ready");
            else
                mTextView.setText("");
            getJSON(UImsg);
        }

        ;
    };

    private void sendMsg(DatagramSocket socket, String size, String ip, int port) throws SocketException, IOException {
        byte[] data = size.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getByName(ip), port);
        socket.send(sendPacket);
    }

    private void sendFile(ByteArrayOutputStream baos, DatagramSocket socket, String ip, int port)
            throws IOException, InterruptedException {
        BufferedInputStream bis = null;
        try {
            InputStream inputimage = new ByteArrayInputStream(baos.toByteArray());
            DatagramPacket dp;
            double nosofpackets = Math.ceil((int) baos.toByteArray().length / 4096);
            byte[] data = new byte[4096];
            bis = new BufferedInputStream(inputimage);
            for (double i = 0; i < nosofpackets + 1; i++) {
                bis.read(data, 0, data.length);
                dp = new DatagramPacket(data, data.length, InetAddress.getByName(ip), port);
                socket.send(dp);
            }
        } finally {
            if (bis != null)
                bis.close();
        }
    }

    private Bitmap compBitmap(Bitmap image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        if (baos.toByteArray().length / 1024 > 1024) {// 判断如果图片大于1M,进行压缩避免在生成图片（BitmapFactory.decodeStream）时溢出
            baos.reset();// 重置baos即清空baos
            image.compress(Bitmap.CompressFormat.JPEG, 50, baos);// 这里压缩50%，把压缩后的数据存放到baos中
        }
        ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());
        BitmapFactory.Options newOpts = new BitmapFactory.Options();
        // 开始读入图片，此时把options.inJustDecodeBounds 设回true了
        newOpts.inJustDecodeBounds = true;
        Bitmap bitmap = BitmapFactory.decodeStream(isBm, null, newOpts);
        newOpts.inJustDecodeBounds = false;
        int w = newOpts.outWidth;
        int h = newOpts.outHeight;
        // 现在主流手机比较多是800*480分辨率，所以高和宽我们设置为
        float hh = 800f;// 这里设置高度为800f
        float ww = 480f;// 这里设置宽度为480f
        // 缩放比。由于是固定比例缩放，只用高或者宽其中一个数据进行计算即可
        int be = 1;// be=1表示不缩放
        if (w > h && w > ww) {// 如果宽度大的话根据宽度固定大小缩放
            be = (int) (newOpts.outWidth / ww);
        } else if (w < h && h > hh) {// 如果高度高的话根据宽度固定大小缩放
            be = (int) (newOpts.outHeight / hh);
        }
        if (be <= 0)
            be = 1;
        newOpts.inSampleSize = be;// 设置缩放比例
        // 重新读入图片，注意此时已经把options.inJustDecodeBounds 设回false了
        isBm = new ByteArrayInputStream(baos.toByteArray());
        bitmap = BitmapFactory.decodeStream(isBm, null, newOpts);
        return compressImage(bitmap);// 压缩好比例大小后再进行质量压缩
    }

    private Bitmap compressImage(Bitmap image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        int options = 100;
        while (((baos.toByteArray().length / 1024) > 100) && options != 0) { // 100kb
            baos.reset();
            image.compress(Bitmap.CompressFormat.JPEG, options, baos);
            if (options > 10) {
                options -= 10;
            } else {
                options--;
            }
        }
        ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());
        Bitmap bitmap = BitmapFactory.decodeStream(isBm, null, null);
        if (rotation == 3) {
            Matrix matrix = new Matrix();
            matrix.postRotate(180);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }
        return bitmap;
    }
}
