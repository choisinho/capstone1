package app.bqlab.febblindrecorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent textIntent = new Intent(context, TextActivity.class);
        textIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(textIntent);
    }
}
