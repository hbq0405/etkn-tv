package xyz.etkn.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/** 登录方式门禁：启动应用与从后台返回（热重启）时按配置决定直接进入最后登录用户，还是先显示用户选择框。 */
final class LoginGate {
    static final int LAST_USER=0,PICK_USER=1;
    static final String[] LABELS={"最后登录用户","选择登录用户"};
    private static final String PREFS="tv_preferences",KEY="login-mode";
    /** 进程级标记：本次前台会话已通过用户选择或登录，离开后台后重置。 */
    private static boolean satisfied;
    private LoginGate(){}
    static int mode(Context context){return normalize(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getInt(KEY,LAST_USER));}
    static void setMode(Context context,int value){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putInt(KEY,normalize(value)).apply();}
    static int normalize(int value){return value==PICK_USER?PICK_USER:LAST_USER;}
    static boolean needsPicker(int mode,boolean satisfied,int accountCount){return mode==PICK_USER&&!satisfied&&accountCount>0;}
    static boolean requiresPicker(Context context,EtknApi api){return needsPicker(mode(context),satisfied,api.session.accounts().size());}
    static void satisfy(){satisfied=true;}
    static void reset(){satisfied=false;}
    /** 交回 SetupActivity，由它按当前配置展示用户选择框或登录表单。 */
    static void enter(Activity activity){activity.startActivity(new Intent(activity,SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));activity.finish();}
}
