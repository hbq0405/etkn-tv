package xyz.etkn.tv;

import android.content.Context;
import android.graphics.Color;
import org.json.JSONObject;

/** Server-owned appearance; TV card geometry remains in TvPreferences. */
final class TvAppearance {
    static JSONObject current=new JSONObject();
    static void load(Context context,String server){
        try{current=new JSONObject(context.getSharedPreferences("appearance",0).getString(server,"{}"));}
        catch(Exception ignored){current=new JSONObject();}
    }
    static void save(Context context,String server,JSONObject value){current=value;context.getSharedPreferences("appearance",0).edit().putString(server,value.toString()).apply();}
    static boolean dark(){return "dark".equals(current.optString("mode","light"));}
    static int primary(){try{return Color.parseColor(current.optString("theme_color").isEmpty()?(dark()?"#55c7b0":"#087f70"):current.getString("theme_color"));}catch(Exception ignored){return 0xff087f70;}}
    static int mix(int from,int to,float amount){return Color.rgb(Math.round(Color.red(from)*(1-amount)+Color.red(to)*amount),Math.round(Color.green(from)*(1-amount)+Color.green(to)*amount),Math.round(Color.blue(from)*(1-amount)+Color.blue(to)*amount));}
    static float shadow(){return (float)Math.max(0,Math.min(1,current.optDouble("shadow_strength",1)));}
    static int translucent(int color){return (color&0x00ffffff)|(Math.round(255*(float)Math.max(0,Math.min(1,current.optDouble("glass_opacity",.76))))<<24);}
    static int background(){return dark()?0xff111716:0xffeef3f1;}
    static int surface(){return dark()?0xff1e2825:0xfffafdfb;}
    static int text(){return dark()?0xffedf4f1:0xff1f332e;}
    static int muted(){return dark()?0xffa6b9b1:0xff61746d;}
    static int accent(){int color=primary();double brightness=(Color.red(color)*299+Color.green(color)*587+Color.blue(color)*114)/255000.0;return dark()&&brightness<.55?mix(color,Color.WHITE,.5f):!dark()&&brightness>.65?mix(color,Color.BLACK,.4f):color;}
    static int resolve(int color){
        if(color==TvActivity.INK)return background();if(color==TvActivity.PANEL)return translucent(surface());
        if(color==TvActivity.TEXT)return text();if(color==TvActivity.MUTED)return muted();
        if(color==TvActivity.ACCENT)return accent();if(color==TvActivity.SOFT)return translucent(mix(surface(),primary(),.16f));
        if(color==TvActivity.PROGRESS)return (primary()&0x00ffffff)|(Math.round(Color.alpha(translucent(primary()))*.36f)<<24);
        return color;
    }
}
