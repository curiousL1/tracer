package czm.record;

import static android.content.Context.ACTIVITY_SERVICE;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

public class MemUtils {

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    static float getAvailMem(Context mContext){
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager)mContext.getSystemService(ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);
        float availableMegs = mi.availMem / 0x100000L;
        float totalMegs = mi.totalMem / 0x100000L;
        //Percentage can be calculated for API 16+
        float percentAvail = mi.availMem / (float)mi.totalMem * 100.0f;
        System.out.println("Mem total:" + totalMegs + " avail:" + availableMegs + " per:" + percentAvail);
        return availableMegs;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    static float getTotalMem(Context mContext){
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager)mContext.getSystemService(ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);
        float totalMegs = mi.totalMem / 0x100000L;
        return totalMegs;
    }
}
