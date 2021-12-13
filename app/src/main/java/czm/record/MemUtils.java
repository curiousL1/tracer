package czm.record;

import static android.content.Context.ACTIVITY_SERVICE;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

public class MemUtils {

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    static double getAvailMem(Context mContext){
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager)mContext.getSystemService(ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);
        double availableMegs = mi.availMem / 0x100000L;
        double totalMegs = mi.totalMem / 0x100000L;
        //Percentage can be calculated for API 16+
        double percentAvail = mi.availMem / (double)mi.totalMem * 100.0;
        System.out.println("Mem total:" + totalMegs + " avail:" + availableMegs + " per:" + percentAvail);
        return percentAvail;
    }
}
