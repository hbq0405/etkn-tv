package xyz.etkn.tv;

import android.app.AlertDialog;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.util.function.Consumer;

/** 列表和详情共用媒体管理确认，保持相同的默认值和删除范围。 */
final class MediaActions {
    static AlertDialog refresh(TvActivity host,Consumer<String> submit){
        LinearLayout options=host.column();options.setPadding(host.dp(18),host.dp(8),host.dp(18),host.dp(8));
        Switch metadata=option(host,"替换所有元数据",true),images=option(host,"替换现有图片",false);
        options.addView(metadata,new LinearLayout.LayoutParams(-1,-2));options.addView(images,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(host).setTitle("刷新元数据").setView(options).setNegativeButton("取消",null)
                .setPositiveButton("刷新",(choice,which)->submit.accept(metadata.isChecked()?(images.isChecked()?"replace_metadata_images":"replace_metadata"):"replace_images")).create();
        dialog.show();dialog.getWindow().setBackgroundDrawable(TvActivity.shape(TvActivity.OVERLAY,0));dialog.getWindow().setDimAmount(0.15f);dialog.getWindow().setLayout(host.dp(440),-2);
        Button confirm=dialog.getButton(AlertDialog.BUTTON_POSITIVE),cancel=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        confirm.setBackground(host.selectionBackground());cancel.setBackground(host.selectionBackground());
        CompoundButton.OnCheckedChangeListener changed=(view,checked)->confirm.setEnabled(metadata.isChecked()||images.isChecked());metadata.setOnCheckedChangeListener(changed);images.setOnCheckedChangeListener(changed);
        cancel.setId(View.generateViewId());confirm.setId(View.generateViewId());metadata.setNextFocusDownId(images.getId());images.setNextFocusUpId(metadata.getId());images.setNextFocusDownId(cancel.getId());cancel.setNextFocusUpId(images.getId());confirm.setNextFocusUpId(images.getId());cancel.setNextFocusRightId(confirm.getId());confirm.setNextFocusLeftId(cancel.getId());metadata.requestFocus();return dialog;
    }
    private static Switch option(TvActivity host,String title,boolean checked){
        Switch option=new Switch(host);option.setText(title);option.setTextColor(Color.WHITE);option.setTextSize(16);option.setChecked(checked);
        option.setId(View.generateViewId());option.setFocusable(true);option.setPadding(host.dp(12),host.dp(8),host.dp(12),host.dp(8));option.setBackground(host.selectionBackground());option.setMinHeight(host.dp(52));return option;
    }
    static AlertDialog delete(TvActivity host,JSONObject item,Runnable submit){
        String type=item.optString("Type"),scope=type.equals("Series")?"整部剧集及其全部季、分集":type.equals("Season")?"本季及其全部分集":"该媒体项的全部版本";
        AlertDialog dialog=new AlertDialog.Builder(host).setTitle("删除媒体项").setMessage("确定删除“"+EtknApi.title(item)+"”吗？将删除"+scope+"，并进入联动删除流程，可能同时删除关联文件。")
                .setNegativeButton("取消",null).setPositiveButton("确认删除",(choice,which)->submit.run()).create();
        dialog.show();dialog.getWindow().setBackgroundDrawable(TvActivity.shape(TvActivity.OVERLAY,0));dialog.getWindow().setDimAmount(0.15f);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackground(host.selectionBackground());dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setBackground(host.selectionBackground());dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();return dialog;
    }
}
