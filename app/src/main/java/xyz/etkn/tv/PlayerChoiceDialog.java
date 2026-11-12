package xyz.etkn.tv;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;

/** 播放器选择弹窗：保留原生列表与选择回调，使用 TV 的深色卡片和焦点边框。 */
final class PlayerChoiceDialog {
    private PlayerChoiceDialog(){}
    static AlertDialog create(TvActivity host,String title,String[] labels,int selected,DialogInterface.OnClickListener choice){
        return create(host,title,labels,selected,choice,600);
    }
    static AlertDialog create(TvActivity host,String title,String[] labels,int selected,DialogInterface.OnClickListener choice,int maximumWidthDp){
        return create(host,title,labels,selected,choice,maximumWidthDp,null,null);
    }
    static AlertDialog create(TvActivity host,String title,String[] labels,int selected,DialogInterface.OnClickListener choice,int maximumWidthDp,String actionLabel,Runnable action){
        LinearLayout heading=host.column();heading.setPadding(host.dp(26),host.dp(18),host.dp(26),host.dp(10));
        heading.addView(host.text(title,23,TvActivity.TEXT));heading.addView(host.text("方向键选择 · 确认键应用",12,TvActivity.MUTED));
        Button topAction=action==null?null:host.button(actionLabel,action);if(topAction!=null)heading.addView(topAction);
        BaseAdapter adapter=new BaseAdapter(){
            @Override public int getCount(){return labels.length;}
            @Override public Object getItem(int position){return labels[position];}
            @Override public long getItemId(int position){return position;}
            @Override public View getView(int position,View reuse,ViewGroup parent){
                TextView row=host.text((position==selected?"✓  ":"    ")+labels[position].replace(" ✓"," · 播放中"),16,position==selected?TvActivity.ACCENT:TvActivity.TEXT);
                row.setGravity(Gravity.CENTER_VERTICAL);row.setMinHeight(host.dp(58));row.setPadding(host.dp(18),host.dp(10),host.dp(18),host.dp(10));row.setMaxLines(2);row.setEllipsize(android.text.TextUtils.TruncateAt.END);
                StateListDrawable background=new StateListDrawable();
                background.addState(new int[]{android.R.attr.state_selected},new LayerDrawable(new android.graphics.drawable.Drawable[]{host.shape(TvActivity.SOFT,0),new FocusPulse(host.dp(12),host.dp(2),TvActivity.ACCENT,host instanceof PlayerActivity)}));
                background.addState(new int[]{},host.shape(position==selected?TvActivity.SOFT:Color.TRANSPARENT,0));row.setBackground(background);return row;
            }
        };
        AlertDialog dialog=host.dialogBuilder().setCustomTitle(heading).setSingleChoiceItems(adapter,selected,choice).setNegativeButton("返回",null).create();
        dialog.setOnShowListener(ignored->{
            Window window=dialog.getWindow();if(window!=null){android.graphics.drawable.GradientDrawable background=host.shape(TvActivity.PANEL,TvActivity.ACCENT);background.setCornerRadius(host.dp(18));window.setBackgroundDrawable(background);window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(0.15f);int width=host.dp(Math.min(maximumWidthDp,host.getResources().getConfiguration().screenWidthDp-80));window.setLayout(width,-2);
                View decor=window.getDecorView();decor.post(()->{int maximum=host.getResources().getDisplayMetrics().heightPixels-host.dp(48);if(decor.getHeight()>maximum)window.setLayout(width,maximum);});}
            ListView list=dialog.getListView();list.setDivider(new ColorDrawable(Color.TRANSPARENT));list.setDividerHeight(host.dp(8));list.setPadding(host.dp(20),host.dp(4),host.dp(20),host.dp(4));list.setSelector(new ColorDrawable(Color.TRANSPARENT));list.setClipToPadding(false);
            Button back=dialog.getButton(DialogInterface.BUTTON_NEGATIVE);back.setAllCaps(false);back.setTextSize(14);host.themeText(back,TvActivity.MUTED);back.setBackground(host.selectionBackground());back.setPadding(host.dp(24),0,host.dp(24),0);
            list.requestFocus();list.setSelection(Math.max(0,selected));
            if(topAction!=null)topAction.requestFocus();
        });
        return dialog;
    }
}
