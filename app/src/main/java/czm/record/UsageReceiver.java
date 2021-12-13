package czm.record;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.greenrobot.eventbus.EventBus;

public class UsageReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.
        String action = intent.getAction();
        if ("usage.export".equals(action)) {
            EventBus.getDefault().post(new UsageEvent(0));
            AlarmUtils.getInstance(context).usageAlarmManagerWorkOnReceiver();
        }
    }
}
