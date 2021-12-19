package czm.record;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.text.style.TtsSpan;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.tencent.map.geolocation.TencentLocation;
import com.tencent.map.geolocation.TencentLocationListener;
import com.tencent.map.geolocation.TencentLocationManager;
import com.tencent.map.geolocation.TencentLocationRequest;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class BackgroundService extends Service implements TencentLocationListener{

    private MediaPlayer mMediaPlayer;
    private AlarmUtils  alarmUtils;
    private Notification notification;
    private NotificationManager manager;
    private static final String CHANNEL_ID = "TracerService";
    private boolean start = false;
    private int errorTimes;
    private long USAGE_STATS_PERIOD, now, beginTime, endTime;
    private Callback callback;
    private PendingIntent pendingIntent;
    public String TAG = "bgservice";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
//        mMediaPlayer = MediaPlayer.create(getApplication(), R.raw.no_kill);
//        mMediaPlayer.setLooping(true);
        EventBus.getDefault().register(this);
        Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": service create\r\n",
                "/sdcard/czm.tracer/", "log.txt");


        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel Channel = null;
            Channel = new NotificationChannel(CHANNEL_ID, "主服务", NotificationManager.IMPORTANCE_HIGH);

            Channel.enableLights(true);//设置提示灯
            Channel.setLightColor(Color.RED);//设置提示灯颜色
            Channel.setShowBadge(true);//显示logo
            Channel.setDescription("tracer");//设置描述
            Channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC); //设置锁屏可见 VISIBILITY_PUBLIC=可见
            manager.createNotificationChannel(Channel);

            Intent resultIntent = new Intent(this, MainActivity.class);
            resultIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            pendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            notification = new Notification.Builder(this)
                    .setChannelId(CHANNEL_ID)
                    .setContentTitle("Tracer")//标题
                    .setContentText(Utils.getIMEI(getApplicationContext()))//内容z
                    .setContentIntent(pendingIntent)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.icon)//小图标一定需要设置,否则会报错(如果不设置它启动服务前台化不会报错,但是你会发现这个通知不会启动),如果是普通通知,不设置必然报错
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.icon))
                    .build();
            startForeground(1, notification);

            TencentLocationRequest request = TencentLocationRequest.create();
            request.setInterval(5 * 60 * 1000)
                    .setAllowCache(true)
                    .setRequestLevel(1);
            TencentLocationManager tlManager = TencentLocationManager.getInstance(this);
            int code = tlManager.requestLocationUpdates(request, this);

            // upload cpu info when start
            HttpUtils httpUtils = HttpUtils.getInstance();
            try {
                httpUtils.syncCPUInfoToCloud(getApplicationContext());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        initAlarm();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                startPlaySong();
//            }
//        }).start();
        return START_STICKY;
    }

    //开始、暂停播放
    private void startPlaySong() {
        if (mMediaPlayer == null) {
            mMediaPlayer = MediaPlayer.create(getApplication(), R.raw.no_kill);
            mMediaPlayer.start();
        } else {
            mMediaPlayer.start();
        }
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (mMediaPlayer != null) {
            mMediaPlayer.pause();
        }
    }


    @Override
    public void onDestroy() {
        Utils.writeTxtToFile(Utils.tc(System.currentTimeMillis()) + ": service destroy\r\n",
                "/sdcard/czm.tracer/", "log.txt");
        super.onDestroy();
        EventBus.getDefault().unregister(this);
//        mMediaPlayer.pause();
//        stopPlaySong();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(getApplicationContext(), BackgroundService.class));
        } else {
            startService(new Intent(getApplicationContext(), BackgroundService.class));
        }
    }

    //停止播放销毁对象
    private void stopPlaySong() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    private void initAlarm(){
        alarmUtils = AlarmUtils.getInstance(this);
        Intent advAlarmIntent = new Intent(this, UsageReceiver.class).setAction("usage.export");
        alarmUtils.createUsageAlarm(advAlarmIntent);
        alarmUtils.usageAlarmManagerStartWork();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onLocationChanged(TencentLocation tencentLocation, int i, String s) {
        String lat = "0", lon = "0";
        if (TencentLocation.ERROR_OK == i) {
            // 定位成功
            if (tencentLocation != null) {
                Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": locate success " + "\r\n",
                        "/sdcard/czm.tracer/", "log.txt");
                lat = String.valueOf(tencentLocation.getLatitude());
                lon = String.valueOf(tencentLocation.getLongitude());
            }
        } else {
            // 定位失败
            Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": locate failed " + "\r\n",
                    "/sdcard/czm.tracer/", "log.txt");
            lat = "err";
            lon = "err";
        }

        HttpUtils httpUtils = HttpUtils.getInstance();
        try {
            httpUtils.testMaxNetDownRate();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // wait for net test result
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        BatteryManager manager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int batteryRemain = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);///当前电量百分比

        String result = lat + "," + lon + "," + Utils.tc(System.currentTimeMillis())
                + "," + MemUtils.getAvailMem(this) + "," + MemUtils.getTotalMem(this) + ","
                + HttpUtils.getMaxNetUpRate() + "," + HttpUtils.getMaxNetDownRate() + ","
                + batteryRemain + "," + CPUUtils.getMaxCpuFreq() + "," +
                CPUUtils.getCurCpuFreq() + "," + CPUUtils.getMinCpuFreq() +"\n";
        Log.d(TAG, ":" + lat + "---" + lon);
        System.out.println("gps_data result: " + result);
        Utils.writeTxtToFile(result, "/sdcard/czm.tracer/", "gps_data.txt");
    }

    @Override
    public void onStatusUpdate(String s, int i, String s1) {

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void notifyError(DestroyEvent event){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(getApplicationContext())
                    .setChannelId(CHANNEL_ID)
                    .setContentTitle("Tracer")//标题
                    .setContentText(event.getMessage())//内容
                    .setContentIntent(pendingIntent)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.icon)//小图标一定需要设置,否则会报错(如果不设置它启动服务前台化不会报错,但是你会发现这个通知不会启动),如果是普通通知,不设置必然报错
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.icon))
                    .build();
        }
        manager.notify(1, notification);
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void upload(UploadEvent event){
        final HttpUtils httpUtils = HttpUtils.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final String gpsUri = "/sdcard/czm.tracer/gps_" + Utils.getIMEI(getApplicationContext()) + ".txt";
            final String usageUri = "/sdcard/czm.tracer/usage_" + Utils.getIMEI(getApplicationContext()) + ".txt";
            errorTimes = 0;
            callback = new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    errorTimes++;
                    Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": http failed: " + e.getMessage() + "\r\n",
                            "/sdcard/czm.tracer/", "log.txt");
                    if (errorTimes < 3) {
                        httpUtils.syncRecordToCloud(getApplicationContext(), new File(gpsUri), new File(usageUri), callback);
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": http success: " + response.toString() + "\r\n",
                            "/sdcard/czm.tracer/", "log.txt");
                    Utils.deleteFile(gpsUri);
                    Utils.deleteFile(usageUri);
                }
            };
            Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": http post begin\r\n",
                    "/sdcard/czm.tracer/", "log.txt");
            httpUtils.syncRecordToCloud(this, new File(gpsUri), new File(usageUri), callback);

        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void writeUsage(UsageEvent msg){
        // wait for gps_data
        try {
            Thread.sleep(11000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": write usage begin\r\n",
                "/sdcard/czm.tracer/", "log.txt");
//        Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": write usage begin\r\n",
//                "/sdcard/czm.tracer/", "usage.txt");
//        SharedPreferences preferences = getSharedPreferences("config", Context.MODE_PRIVATE);
//        long lastTime = preferences.getLong("lastTime", 0);
//        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
//            USAGE_STATS_PERIOD = 1000 * 60 * 60 * 24 * 1;
//            now = System.currentTimeMillis();
//            Log.d(TAG, "lastTime: " + lastTime);
//            if (lastTime != 0)
//                beginTime = lastTime;
//            else
//                beginTime = now - now % USAGE_STATS_PERIOD;
//            Log.d(TAG, "begin: " + beginTime);
//            UsageEvents usageEvents = usm.queryEvents(beginTime, now);
//            String foreEvent = null, appName = null;
//            String result = "";
//            long appStart = 0, appEnd = 0;
//            //遍历这个事件集合，如果还有下一个事件
//            while (usageEvents.hasNextEvent()) {
//                UsageEvents.Event event = new UsageEvents.Event();
//                //得到下一个事件放入event中,先得得到下个一事件，如果这个时候直接调用，则	event的package是null，type是0。
//                usageEvents.getNextEvent(event);
//                if (event.getPackageName().contains("android")) continue;
////                Log.d(TAG, "package == " + event.getPackageName() + ",  type == " + event.getEventType()
////                        + ", time == " + event.getTimeStamp());
//                //如果这是个将应用置于前台的事件
//                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
//                    //获取这个前台事件的packageName.time
//                    foreEvent = event.getPackageName();
//                    appStart = event.getTimeStamp();
//                } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND && event.getPackageName().equals(foreEvent)) {
//                    //如果是最后一个前台应用的置于后台的动作
//                    PackageManager pm = getPackageManager();
//                    try {
//                        ApplicationInfo appInfo = pm.getApplicationInfo(event.getPackageName(), PackageManager.GET_META_DATA);
//
//                        appName = (String) pm.getApplicationLabel(appInfo);
//                        //appIcon = pm.getApplicationIcon(appInfo);
//
//                    } catch (PackageManager.NameNotFoundException e) {
//                        e.printStackTrace();
//                    }
//
//                    appEnd = event.getTimeStamp();
//                    result = foreEvent + "," + appName + "," + Utils.tc(appStart) +
//                            "," + Utils.tc(appEnd) + "," + (appEnd - appStart) + "\n";
//                    Utils.writeTxtToFile(result, "/sdcard/czm.tracer/", "usage.txt");
//                }
//
//            }
//            Log.d(TAG, "appEnd = " + appEnd);
//            if (appEnd != 0) {
//                SharedPreferences.Editor editor = preferences.edit();
//                editor.putLong("lastTime", appEnd);
//                editor.apply();
//            }
//        }
        Utils.writeTxtToFile("usage ignored.\n", "/sdcard/czm.tracer/", "usage.txt");
        FileTask task = new FileTask(this);
        task.execute();
        Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": file operate begin\r\n",
                "/sdcard/czm.tracer/", "log.txt");
    }

}
