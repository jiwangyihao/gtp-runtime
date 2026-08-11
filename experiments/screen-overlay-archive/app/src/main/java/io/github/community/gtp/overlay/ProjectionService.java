package io.github.community.gtp.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.WindowManager;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class ProjectionService extends Service {
    private static final String TAG = "GtpProjection";
    private static final String ACTION_START = "io.github.community.gtp.overlay.START";
    private static final String ACTION_STOP = "io.github.community.gtp.overlay.STOP";
    private static final String EXTRA_RESULT_CODE = "result_code";
    private static final String EXTRA_RESULT_DATA = "result_data";
    private static final String CHANNEL_ID = "screen_translation";
    private static final int NOTIFICATION_ID = 1701;
    private static final int MAX_CAPTURE_EDGE = 1600;
    private static final long MIN_OCR_INTERVAL_MS = 850;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private final Object captureLock = new Object();
    private final AtomicBoolean recognitionInFlight = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private HandlerThread captureThread;
    private Handler captureHandler;
    private MediaProjection mediaProjection;
    private MediaProjection.Callback projectionCallback;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private TextRecognizer recognizer;
    private DictionaryTranslator translator;
    private TranslationOverlay overlay;
    private long lastOcrAt;
    private int emptyFrames;
    private String lastTranslation = "";
    private boolean released;

    static Intent captureIntent(Context context) {
        return context.getSystemService(MediaProjectionManager.class).createScreenCaptureIntent();
    }

    static Intent startIntent(Context context, int resultCode, Intent resultData) {
        return new Intent(context, ProjectionService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData);
    }

    static Intent stopIntent(Context context) {
        return new Intent(context, ProjectionService.class).setAction(ACTION_STOP);
    }

    static boolean isRunning() {
        return RUNNING.get();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        captureThread = new HandlerThread("gtp-screen-capture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
        recognizer = TextRecognition.getClient(
                new JapaneseTextRecognizerOptions.Builder().build());
        translator = DictionaryTranslator.seedMenuDictionary();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            Log.i(TAG, "SESSION_STOP_REQUESTED");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (RUNNING.get()) {
            return START_NOT_STICKY;
        }

        startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Integer.MIN_VALUE);
        Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        if (resultCode == Integer.MIN_VALUE || resultData == null) {
            Log.e(TAG, "SESSION_START_FAILED missing projection grant");
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            overlay = new TranslationOverlay(this);
            MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
            mediaProjection = manager.getMediaProjection(resultCode, resultData);
            if (mediaProjection == null) {
                throw new IllegalStateException("系统未返回 MediaProjection");
            }
            projectionCallback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    Log.i(TAG, "SESSION_STOPPED_BY_SYSTEM");
                    stopSelf();
                }

                @Override
                public void onCapturedContentResize(int width, int height) {
                    Handler handler = captureHandler;
                    if (handler != null) {
                        handler.post(() -> resizeCapture(width, height));
                    }
                }
            };
            mediaProjection.registerCallback(projectionCallback, mainHandler);

            Rect bounds = getSystemService(WindowManager.class)
                    .getMaximumWindowMetrics()
                    .getBounds();
            configureCapture(bounds.width(), bounds.height());
            RUNNING.set(true);
            Log.i(TAG, "SESSION_STARTED width=" + bounds.width() + " height=" + bounds.height());
        } catch (RuntimeException error) {
            Log.e(TAG, "SESSION_START_FAILED", error);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        releaseResources(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void configureCapture(int sourceWidth, int sourceHeight) {
        synchronized (captureLock) {
            if (released) {
                return;
            }
            CaptureSize size = scaledSize(sourceWidth, sourceHeight);
            ImageReader newReader = ImageReader.newInstance(
                    size.width,
                    size.height,
                    PixelFormat.RGBA_8888,
                    2);
            newReader.setOnImageAvailableListener(this::onImageAvailable, captureHandler);

            if (virtualDisplay == null) {
                int densityDpi = getResources().getDisplayMetrics().densityDpi;
                virtualDisplay = mediaProjection.createVirtualDisplay(
                        "GTP Screen Translation",
                        size.width,
                        size.height,
                        densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        newReader.getSurface(),
                        null,
                        captureHandler);
            } else {
                virtualDisplay.setSurface(null);
                virtualDisplay.resize(
                        size.width,
                        size.height,
                        getResources().getDisplayMetrics().densityDpi);
                virtualDisplay.setSurface(newReader.getSurface());
            }

            ImageReader oldReader = imageReader;
            imageReader = newReader;
            if (oldReader != null) {
                oldReader.setOnImageAvailableListener(null, null);
                oldReader.close();
            }
            Log.i(TAG, "CAPTURE_CONFIGURED width=" + size.width + " height=" + size.height);
        }
    }

    private void resizeCapture(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        try {
            configureCapture(width, height);
        } catch (RuntimeException error) {
            Log.e(TAG, "CAPTURE_RESIZE_FAILED", error);
            stopSelf();
        }
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (now - lastOcrAt < MIN_OCR_INTERVAL_MS
                    || !recognitionInFlight.compareAndSet(false, true)) {
                return;
            }
            lastOcrAt = now;
            Bitmap bitmap = copyImage(image);
            processBitmap(bitmap);
        } catch (IllegalStateException error) {
            Log.w(TAG, "CAPTURE_FRAME_DROPPED " + error.getMessage());
            recognitionInFlight.set(false);
        } catch (RuntimeException error) {
            Log.e(TAG, "CAPTURE_FRAME_FAILED", error);
            recognitionInFlight.set(false);
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private void processBitmap(Bitmap bitmap) {
        InputImage input = InputImage.fromBitmap(bitmap, 0);
        recognizer.process(input)
                .addOnSuccessListener(this::onTextRecognized)
                .addOnFailureListener(error -> Log.e(TAG, "OCR_FAILED", error))
                .addOnCompleteListener(task -> {
                    bitmap.recycle();
                    recognitionInFlight.set(false);
                });
    }

    private void onTextRecognized(Text text) {
        List<String> lines = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                lines.add(line.getText());
            }
        }
        List<DictionaryTranslator.Match> matches = translator.translateLines(lines);
        if (matches.isEmpty()) {
            emptyFrames++;
            if (emptyFrames >= 2 && overlay != null) {
                lastTranslation = "";
                overlay.hide();
            }
            Log.d(TAG, "OCR_FRAME lines=" + lines.size() + " matches=0");
            return;
        }

        emptyFrames = 0;
        String translation = matches.stream()
                .map(DictionaryTranslator.Match::translation)
                .collect(Collectors.joining("  ·  "));
        if (!translation.equals(lastTranslation) && overlay != null) {
            lastTranslation = translation;
            overlay.show(translation);
        }
        String sources = matches.stream()
                .map(DictionaryTranslator.Match::source)
                .collect(Collectors.joining(" | "));
        Log.i(TAG, "OCR_MATCH count=" + matches.size()
                + " source=" + sources
                + " translation=" + translation);
    }

    private static Bitmap copyImage(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        buffer.rewind();
        int paddedWidth = plane.getRowStride() / plane.getPixelStride();
        Bitmap padded = Bitmap.createBitmap(
                paddedWidth,
                image.getHeight(),
                Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        if (paddedWidth == image.getWidth()) {
            return padded;
        }
        Bitmap cropped = Bitmap.createBitmap(
                padded,
                0,
                0,
                image.getWidth(),
                image.getHeight());
        padded.recycle();
        return cropped;
    }

    private static CaptureSize scaledSize(int width, int height) {
        int longest = Math.max(width, height);
        if (longest <= MAX_CAPTURE_EDGE) {
            return new CaptureSize(width, height);
        }
        float scale = MAX_CAPTURE_EDGE / (float) longest;
        return new CaptureSize(
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)));
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_translate)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(
                        null,
                        "停止",
                        stopPendingIntent).build())
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("显示本地屏幕 OCR 的运行状态");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void releaseResources(boolean stopProjection) {
        synchronized (captureLock) {
            if (released) {
                return;
            }
            released = true;
            RUNNING.set(false);

            if (imageReader != null) {
                imageReader.setOnImageAvailableListener(null, null);
                imageReader.close();
                imageReader = null;
            }
            if (virtualDisplay != null) {
                virtualDisplay.setSurface(null);
                virtualDisplay.release();
                virtualDisplay = null;
            }
            if (mediaProjection != null) {
                if (projectionCallback != null) {
                    mediaProjection.unregisterCallback(projectionCallback);
                }
                if (stopProjection) {
                    mediaProjection.stop();
                }
                mediaProjection = null;
            }
        }

        if (overlay != null) {
            overlay.close();
            overlay = null;
        }
        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
            captureHandler = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        Log.i(TAG, "SESSION_RELEASED");
    }

    private record CaptureSize(int width, int height) {}
}
