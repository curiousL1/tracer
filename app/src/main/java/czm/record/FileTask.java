package czm.record;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.greenrobot.eventbus.EventBus;

public class FileTask extends AsyncTask {

    private Context context;

    public FileTask(Context context) {
        this.context = context;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected Object doInBackground(Object[] objects) {
        Utils.copyFile("/sdcard/czm.tracer/gps_data.txt",
                "/sdcard/czm.tracer/gps_" + Utils.getIMEI(context) + ".txt");
        Utils.deleteFile("sdcard/czm.tracer/gps_data.txt");
        Utils.copyFile("/sdcard/czm.tracer/usage.txt",
                "/sdcard/czm.tracer/usage_" + Utils.getIMEI(context) + ".txt");
        Utils.deleteFile("sdcard/czm.tracer/usage.txt");
        return null;
    }

    @Override
    protected void onPostExecute(Object o) {
        EventBus.getDefault().post(new UploadEvent());
        super.onPostExecute(o);
    }
}
