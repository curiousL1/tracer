package czm.record;

import android.content.Context;
import android.net.TrafficStats;
import android.os.Build;
import android.os.StrictMode;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpUtils {

    private static volatile HttpUtils instance;
    private OkHttpClient client = new OkHttpClient();
    private String url = "http://inpluslab.com/paperwriting2019/multifile.php";
    private static String netTestUrl = "http://8.134.60.169:8000/api/storage";
    private static String netTestFileDir = "/sdcard/czm.tracer/";
    private static String netTestFileName = "1.bmp";
    private static float maxUprate = 0;
    private static float maxDownrate = 0;

    public static HttpUtils getInstance() {
        if (instance == null) {
            synchronized (HttpUtils.class) {
                if(null == instance) {
                    instance = new HttpUtils();
                }
            }
        }
        return instance;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void syncRecordToCloud(Context context, File gps, File usage, Callback callback){
        if (Utils.getIMEI(context) != null) {
            MediaType mediaType = MediaType.parse("application/octet-stream");
            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            builder.addFormDataPart("IMEI", Utils.getIMEI(context));
            builder.addFormDataPart("gps", gps.getName(), RequestBody.create(mediaType, gps));
            builder.addFormDataPart("usage", usage.getName(), RequestBody.create(mediaType, usage));
            Request request = new Request.Builder()
                    .url(url)
                    .post(builder.build())
                    .build();
            Call call = client.newCall(request);
            call.enqueue(callback);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void syncCPUInfoToCloud(Context context) throws IOException {
        String cpu_filename = "cpu_"+Utils.getIMEI(context)+".txt";
        Utils.deleteFile(netTestFileDir+cpu_filename);

        String cpu_info = CPUUtils.ReadCPUinfo();
        Utils.writeTxtToFile(cpu_info, netTestFileDir, cpu_filename);
        File cpu_file = new File(netTestFileDir+cpu_filename);
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", cpu_filename,
                        RequestBody.create(MediaType.parse("multipart/form-data"), cpu_file))
                .build();

        Request request = new Request.Builder()
                .url(netTestUrl)
                .post(requestBody)
                .build();

        Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": http post cpuinfo begin\r\n",
                "/sdcard/czm.tracer/", "log.txt");
        StrictMode.ThreadPolicy policy=new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy); // prevent StrictMode$AndroidBlockGuardPolicy
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()){
            Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": http post cpuinfo failed\r\n",
                    "/sdcard/czm.tracer/", "log.txt");
            return;
        }
        Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": http post cpuinfo success\r\n",
                "/sdcard/czm.tracer/", "log.txt");

    }

    static float readNetUpRate() {
        long upBytesBefore = TrafficStats.getTotalTxBytes();

        try {
            Thread.sleep(1000);
        } catch (Exception e) {}

        long upBytesAfter = TrafficStats.getTotalTxBytes();

        return (float)(upBytesAfter - upBytesBefore) / (1024 * 1024);
    }

    static float readNetDownRate() {
        long upBytesBefore = TrafficStats.getTotalRxBytes();

        try {
            Thread.sleep(1000);
        } catch (Exception e) {}

        long upBytesAfter = TrafficStats.getTotalRxBytes();

        return (float)(upBytesAfter - upBytesBefore) / (1024 * 1024);
    }

    public void testMaxNetUpRate() throws IOException {
        File netTestFile = new File(netTestFileDir + netTestFileName);
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", netTestFileName,
                        RequestBody.create(MediaType.parse("multipart/form-data"), netTestFile))
                .build();

        Request request = new Request.Builder()
                .url(netTestUrl)
                .post(requestBody)
                .build();

        StrictMode.ThreadPolicy policy=new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy); // prevent StrictMode$AndroidBlockGuardPolicy
        long startTime = System.currentTimeMillis(); // 开始上传时获取开始时间
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()){
            HttpUtils.setMaxNetUpRate(0);
            return;
        }

        long curTime = System.currentTimeMillis();
        float uploadSize = (float)(netTestFile.length()/1048576.0); // MB
        float usedTime = (float) ((curTime-startTime)/1000.0);    // seconds
        float uploadSpeed = uploadSize/usedTime; // 上传速度 MB/s
        System.out.println("up size: " + uploadSize + " start: " + startTime + " end: " + curTime + " used: " + usedTime);
        HttpUtils.setMaxNetUpRate(uploadSpeed);
        Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": net test end " + "\r\n",
                "/sdcard/czm.tracer/", "log.txt");
    }

    public void testMaxNetDownRate() throws IOException {
        Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": net test begin " + "\r\n",
                "/sdcard/czm.tracer/", "log.txt");
        final long startTime = System.currentTimeMillis(); // 开始下载时获取开始时间
        Request request = new Request.Builder().url(netTestUrl+"?path=" + netTestFileName).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                HttpUtils.setMaxNetDownRate(0);
                return;
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                InputStream is = null;
                byte[] buf = new byte[2048];
                int len = 0;
                FileOutputStream fos = null;
                try {
                    is = response.body().byteStream();
                    long total = response.body().contentLength();
                    File netTestFile = new File(netTestFileDir, netTestFileName);
                    fos = new FileOutputStream(netTestFile);
                    long sum = 0;
                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                        sum += len;
                        int progress = (int) (sum * 1.0f / total * 100);
                        // 下载中
                    }
                    fos.flush();
                    // 下载完成
                    long curTime = System.currentTimeMillis();
                    float downloadSize = (float)(netTestFile.length()/1048576.0); // MB
                    float usedTime = (float) ((curTime-startTime)/1000.0);    // seconds
                    float downloadSpeed = downloadSize/usedTime; // 上传速度 MB/s
                    System.out.println("down size: " + downloadSize + " start: " + startTime + " end: " + curTime + " used: " + usedTime);
                    HttpUtils.setMaxNetDownRate(downloadSpeed);
                } catch (Exception e) {
                    System.out.println(e);
                } finally {
                    try {
                        if (is != null)
                            is.close();
                    } catch (IOException e) {
                        System.out.println(e);
                    }
                    try {
                        if (fos != null)
                            fos.close();
                    } catch (IOException e) {
                        System.out.println(e);
                    }

                    Utils.writeTxtToFile( Utils.tc(System.currentTimeMillis()) + ": net down test end, up begin " + "\r\n",
                            "/sdcard/czm.tracer/", "log.txt");
                    testMaxNetUpRate(); // prevent java.net.ProtocolException: unexpected end of stream
                }
            }
        });

    }

    public static void setMaxNetDownRate(float rate){
        maxDownrate = rate;
    }

    public static void setMaxNetUpRate(float rate){
        maxUprate = rate;
    }

    public static float getMaxNetDownRate(){
        return maxDownrate;
    }

    public static float getMaxNetUpRate(){
        return maxUprate;
    }
}
