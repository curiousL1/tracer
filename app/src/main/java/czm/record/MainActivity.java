package czm.record;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity /*implements TencentLocationListener*/ {

    public String TAG = "record_data";
//    private TextView textView;
    private int exportCount = 0;
    private AlertDialog alertDialog;
    private boolean permission = false;
    private int errorTImes;
    private Callback callback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPerLoc();
        checkPerUsage();
        doze();
        EventBus.getDefault().post(new DestroyEvent("Have a nice day~"));
    }

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_export) {
            export();
        }
        return super.onOptionsItemSelected(item);
    }*/

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //可在此继续其他操作。
        permission = true;
        initLocation();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0 && resultCode == 0) checkPerUsage();
    }

    public void checkPerLoc(){
        if (Build.VERSION.SDK_INT >= 23) {
            String[] permissions = {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };

            if (checkSelfPermission(permissions[0]) != PackageManager.PERMISSION_GRANTED)
            {
                requestPermissions(permissions, 0);
            } else {
                Log.d(TAG, "checkPerLoc: ");
                initLocation();
            }
        }
    }

    public void checkPerUsage(){
        AppOpsManager appOps = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow("android:get_usage_stats",
                    android.os.Process.myUid(), getPackageName());
            boolean granted = mode == AppOpsManager.MODE_ALLOWED;
            if (!granted) {
                startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), 0);
            } else {
//                USAGE_STATS_PERIOD = 1000 * 60 * 60 * 24 * 1;
//                now = System.currentTimeMillis();
//                beginTime = now - now % USAGE_STATS_PERIOD;
//                endTime = beginTime + USAGE_STATS_PERIOD;
            }
        }
    }

    //低电量模式降低杀后台风险
    public void doze(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = getPackageName();
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName);
            if (!isIgnoring) {
                Intent intent = new Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                try {
                    startActivity(intent);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    public void initLocation(){
        /*TencentLocationRequest request = TencentLocationRequest.create();
        request.setInterval(60 * 1000)
                .setAllowCache(true)
                .setRequestLevel(1);
        TencentLocationManager manager = TencentLocationManager.getInstance(this);
        int code = manager.requestLocationUpdates(request, this);
        Log.d(TAG, "注册监听器code = " + code);
        Bundle bundle = new Bundle();
        bundle.putString("msg", "注册监听器code = " + code + "\n");
        Message message = new Message();
        message.setData(bundle);
        tvHandler.sendMessage(message);*/
        Intent intent = new Intent(MainActivity.this, BackgroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        }
    }

    /*private void initAlarm() {
        Log.d(TAG, "initAlarm: ");
        almg = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent advAlarmIntent = new Intent(this, UsageReceiver.class).setAction("usage.export");
        advBroadcast = PendingIntent.getBroadcast(this, 0, advAlarmIntent, 0);
//        almg.setInexactRepeating(AlarmManager.RTC_WAKEUP, (endTime - 1000*60*5), AlarmManager.INTERVAL_DAY, advBroadcast);
//        almg.setInexactRepeating(AlarmManager.RTC_WAKEUP, (endTime - 1000*60*60*4), AlarmManager.INTERVAL_DAY, advBroadcast);
//        almg.setInexactRepeating(AlarmManager.RTC_WAKEUP, (beginTime + 1000*60*60*4), AlarmManager.INTERVAL_DAY, advBroadcast);
//        almg.setInexactRepeating(AlarmManager.RTC_WAKEUP, (endTime - 1000*60*60*12), AlarmManager.INTERVAL_DAY, advBroadcast);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            almg.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, SystemClock.elapsedRealtime(), advBroadcast);
        }
    }*/

    /*@Subscribe(threadMode = ThreadMode.MAIN)
    public void writeUsage(UsageEvent msg){
        Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": write usage begin\r\n",
                "/sdcard/czm.tracer/", "log.txt");
        Log.d(TAG, "writeUsage: ");
        SharedPreferences preferences = getSharedPreferences("config", Context.MODE_PRIVATE);
        long lastTime = preferences.getLong("lastTime", 0);
        Log.d(TAG, "writeUsage: " + lastTime);
        if (msg.getFlag() == 0) {
            Utils.writeTxtToFile("由alarm触发的使用情况记录" + Utils.tc(System.currentTimeMillis()) + "\n",
                    "/sdcard/czm.tracer/", "usage.txt");
        } else {
            Utils.writeTxtToFile("切换后台或app结束触发的使用情况记录" + Utils.tc(System.currentTimeMillis()) + "\n",
                    "/sdcard/czm.tracer/", "usage.txt");
        }
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (lastTime != 0) beginTime = lastTime;
            endTime = System.currentTimeMillis();
            UsageEvents usageEvents = usm.queryEvents(beginTime, endTime);
            String foreEvent = null, appName = null;
            String result = "";
            long appStart = 0, appEnd = 0;
            //遍历这个事件集合，如果还有下一个事件
            while (usageEvents.hasNextEvent()) {
                UsageEvents.Event event = new UsageEvents.Event();
                //得到下一个事件放入event中,先得得到下个一事件，如果这个时候直接调用，则	event的package是null，type是0。
                usageEvents.getNextEvent(event);
                if (event.getPackageName().contains("android")) continue;
//                Log.d(TAG, "package == " + event.getPackageName() + ",  type == " + event.getEventType()
//                        + ", time == " + event.getTimeStamp());
                //如果这是个将应用置于前台的事件
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    //获取这个前台事件的packageName.time
                    foreEvent = event.getPackageName();
                    appStart = event.getTimeStamp();
                } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND && event.getPackageName().equals(foreEvent)) {
                    //如果是最后一个前台应用的置于后台的动作
                    PackageManager pm = getPackageManager();
                    try {
                        ApplicationInfo appInfo = pm.getApplicationInfo(event.getPackageName(), PackageManager.GET_META_DATA);

                        appName = (String) pm.getApplicationLabel(appInfo);
                        //appIcon = pm.getApplicationIcon(appInfo);

                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }

                    appEnd = event.getTimeStamp();
                    result = foreEvent + "," + appName + "," + Utils.tc(appStart) +
                            "," + Utils.tc(appEnd) + "," + (appEnd - appStart) + "\n";
                    Utils.writeTxtToFile(result, "/sdcard/czm.tracer/", "usage.txt");
//                    Log.d(TAG, "包名:" + foreEvent + ", 名称：" + appName + ", 开始于：" + Utils.tc(appStart) +
//                            "，结束于：" + Utils.tc(appEnd) + "，运行时间：" + (appEnd - appStart));
//                    Bundle bundle = new Bundle();
//                    bundle.putString("msg","包名:" + foreEvent + ", 名称：" + appName + ", 开始于：" + Utils.tc(appStart) +
//                            "，结束于：" + Utils.tc(appEnd) + "，运行时间：" + (appEnd - appStart) + "\n");
//                    Message message = new Message();
//                    message.setData(bundle);
//                    tvHandler.sendMessage(message);
                }
                Log.d(TAG, "writeUsage: appEnd = " + appEnd);

            }
            Log.d(TAG, "appEnd = " + appEnd);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putLong("lastTime", appEnd);
            editor.apply();
//            Log.d(TAG, "writeUsage: " + result);
            exportCount++;
//            Utils.writeTxtToFile(result, "/sdcard/czm.record/", "usage.txt");
        }
        if (msg.getFlag() == 0) export();
    }*/

    /*@Subscribe(threadMode = ThreadMode.MAIN)
    public void dismiss(UploadEvent event){
        Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": http post begin\r\n",
                "/sdcard/czm.tracer/", "log.txt");
        final HttpUtils httpUtils = HttpUtils.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final String gpsUri = "/sdcard/czm.tracer/gps_" + Utils.getIMEI(getApplicationContext()) + ".txt";
            final String usageUri = "/sdcard/czm.tracer/usage_" + Utils.getIMEI(getApplicationContext()) + ".txt";
            errorTImes = 0;
            callback = new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    errorTImes++;
                    Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": http failed: " + e.getMessage() + "\r\n",
                            "/sdcard/czm.tracer/", "log.txt");
                    if (errorTImes == 3) {
                        alertDialog.dismiss();
                    } else {
                        httpUtils.syncRecordToCloud(getApplicationContext(), new File(gpsUri), new File(usageUri), callback);
                        Looper.prepare();
                        Toast.makeText(getApplicationContext(), "请检查网络连接", Toast.LENGTH_SHORT).show();
                        Looper.loop();
                        alertDialog.dismiss();
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": http success: " + response.toString() + "\r\n",
                            "/sdcard/czm.tracer/", "log.txt");
                    Utils.deleteFile(gpsUri);
                    Utils.deleteFile(usageUri);
                    alertDialog.dismiss();
                }
            };
            httpUtils.syncRecordToCloud(this, new File(gpsUri), new File(usageUri), callback);

        }
    }*/

    /*public void export() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialog_view = inflater.inflate(R.layout.layout_dialog,null);
        alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setCancelable(false);
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.show();
        Window window = alertDialog.getWindow();
        window.setContentView(dialog_view);
        window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        FileTask task = new FileTask(this);
        task.execute();
        Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": file operate begin\r\n",
                "/sdcard/czm.tracer/", "log.txt");
    }*/

//    public Handler tvHandler = new Handler(){
//        @Override
//        public void handleMessage(@NonNull Message msg) {
//            super.handleMessage(msg);
//            textView.setText(msg.getData().getString("msg"));
//        }
//    };

    /*@Override
    protected void onPause() {
        if (permission) writeUsage(new UsageEvent(1));
        super.onPause();
    }*/

    @Override
    protected void onDestroy() {
        Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": activity destroy\r\n",
                "/sdcard/czm.tracer/", "log.txt");
        EventBus.getDefault().post(new DestroyEvent("检测到服务异常，请手动打开app"));
//        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }
}
