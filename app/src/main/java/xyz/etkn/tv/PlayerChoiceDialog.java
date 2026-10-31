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
        LinearLayout heading=host.column();heading.setPadding(host.dp(26),host.dp(18),host.dp(26),host.dp(10));
        heading.addView(host.text(title,23,Color.WHITE));heading.addView(host.text("方向键选择 · 确认键应用",12,TvActivity.MUTED));
        BaseAdapter adapter=new BaseAdapter(){
            @Override public int getCount(){return labels.length;}
            @Override public Object getItem(int position){return labels[position];}
            @Override public long getItemId(int position){return position;}
            @Override public View getView(int position,View reuse,ViewGroup parent){
                TextView row=host.text((position==selected?"✓  ":"    ")+labels[position].replace(" ✓"," · 播放中"),16,position==selected?TvActivity.ACCENT:Color.WHITE);
                row.setGravity(Gravity.CENTER_VERTICAL);row.setMinHeight(host.dp(58));row.setPadding(host.dp(18),host.dp(10),host.dp(18),host.dp(10));row.setMaxLines(2);row.setEllipsize(android.text.TextUtils.TruncateAt.END);
                StateListDrawable background=new StateListDrawable();
                background.addState(new int[]{android.R.attr.state_selected},new LayerDrawable(new android.graphics.drawable.Drawable[]{TvActivity.shape(Color.rgb(39,69,73),0),new FocusPulse(host.dp(8),host.dp(2),TvActivity.ACCENT)}));
                background.addState(new int[]{},TvActivity.shape(position==selected?Color.rgb(25,48,44):Color.TRANSPARENT,0));row.setBackground(background);return row;
            }
        };
        AlertDialog dialog=new AlertDialog.Builder(host).setCustomTitle(heading).setSingleChoiceItems(adapter,selected,choice).setNegativeButton("返回",null).create();
        dialog.setOnShowListener(ignored->{
            Window window=dialog.getWindow();if(window!=null){android.graphics.drawable.GradientDrawable background=TvActivity.shape(Color.rgb(17,25,37),Color.rgb(45,62,77));background.setCornerRadius(host.dp(18));window.setBackgroundDrawable(background);window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(0.6f);int width=host.dp(Math.min(600,host.getResources().getConfiguration().screenWidthDp-80));window.setLayout(width,-2);
                View decor=window.getDecorView();decor.post(()->{int maximum=host.getResources().getDisplayMetrics().heightPixels-host.dp(48);if(decor.getHeight()>maximum)window.setLayout(width,maximum);});}
            ListView list=dialog.getListView();list.setDivider(new ColorDrawable(Color.TRANSPARENT));list.setDividerHeight(host.dp(8));list.setPadding(host.dp(20),host.dp(4),host.dp(20),host.dp(4));list.setSelector(new ColorDrawable(Color.TRANSPARENT));list.setClipToPadding(false);
            Button back=dialog.getButton(DialogInterface.BUTTON_NEGATIVE);back.setAllCaps(false);back.setTextSize(14);back.setTextColor(TvActivity.MUTED);back.setBackground(host.focusBackground());back.setPadding(host.dp(24),0,host.dp(24),0);
            list.requestFocus();list.setSelection(Math.max(0,selected));
        });
        return dialog;
    }
}
