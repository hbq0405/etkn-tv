package xyz.etkn.tv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

/** Shared fixed-position notification styling for TV playback and app notices. */
final class TvNotice {
    private TvNotice() {}

    static void style(TextView view, Activity activity) {
        view.setTextColor(Color.WHITE);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(activity,24),dp(activity,10),dp(activity,24),dp(activity,10));
        GradientDrawable background=new GradientDrawable();
        background.setColor(TvAppearance.translucent(TvAppearance.primary()));
        background.setCornerRadius(dp(activity,28));
        view.setBackground(background);
        view.setMinHeight(dp(activity,48));
        view.setElevation(0);
        view.setMaxLines(2);view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setMaxWidth(activity.getResources().getDisplayMetrics().widthPixels-dp(activity,224));
    }

    static void show(Activity activity, String message, int duration) {
        if(activity==null||activity.isFinishing()||activity.isDestroyed()||message==null||message.isEmpty())return;
        activity.runOnUiThread(()->{
            if(activity.isFinishing()||activity.isDestroyed())return;
            if(activity instanceof PlayerActivity){((PlayerActivity)activity).showUnifiedNotice(message,duration==Toast.LENGTH_LONG?3500:2000);return;}
            if(activity instanceof TvActivity){((TvActivity)activity).showAppNotice(message,duration==Toast.LENGTH_LONG?3500:2000);return;}
            showToast(activity,message,duration);
        });
    }

    static void showToast(Activity activity,String message,int duration){
        if(activity==null||activity.isFinishing()||activity.isDestroyed()||message==null||message.isEmpty())return;
        TextView view=new TextView(activity);view.setText(message);style(view,activity);
        Toast toast=Toast.makeText(activity,"",duration);toast.setView(view);toast.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,dp(activity,48));toast.show();
    }

    private static int dp(Activity activity,float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}