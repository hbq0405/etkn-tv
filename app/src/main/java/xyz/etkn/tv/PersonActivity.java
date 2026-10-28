package xyz.etkn.tv;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;
import org.json.JSONObject;
import java.util.List;

public final class PersonActivity extends TvActivity {
    private String personId;
    private GridLayout works;
    private Button more;
    private int loaded;
    private boolean loading;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);personId=getIntent().getStringExtra("id");screen(getIntent().getStringExtra("name"),"");nav("返回",this::finish);
        LinearLayout identity=row();ImageView portrait=new ImageView(this);portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(portrait,dp(10));identity.addView(portrait,new LinearLayout.LayoutParams(dp(140),dp(200)));
        TextView biography=text("正在读取人物资料…",16,Color.WHITE);biography.setMaxLines(8);biography.setEllipsize(android.text.TextUtils.TruncateAt.END);biography.setFocusable(true);biography.setBackground(transparentFocusBackground());biography.setPadding(dp(20),dp(8),dp(12),dp(8));identity.addView(biography,new LinearLayout.LayoutParams(0,dp(200),1));content.addView(identity);
        async(()->api.get("/api/tv/Persons/"+EtknApi.encode(personId)+"?UserId="+EtknApi.encode(api.session.userId())),person->{
            Images.load(api,api.artwork(person,"Primary",320),portrait);String overview=person.optString("Overview");biography.setText(overview.isEmpty()?"暂无人物简介":overview);biography.setOnClickListener(view->new AlertDialog.Builder(this).setTitle(person.optString("Name")).setMessage(biography.getText()).setPositiveButton("关闭",null).show());
        });
        content.addView(text("关联媒体",22,Color.WHITE));works=grid();more=button("加载更多",this::loadWorks);content.addView(more);loadWorks();
    }
    private void loadWorks(){
        if(loading)return;loading=true;more.setEnabled(false);status.setText("正在读取关联媒体…");
        async(()->{try{return api.get("/api/tv/Persons/"+EtknApi.encode(personId)+"/Items?UserId="+EtknApi.encode(api.session.userId())+"&IncludeItemTypes=Movie,Series&StartIndex="+loaded+"&Limit=40");}finally{runOnUiThread(()->{loading=false;more.setEnabled(true);});}},result->{
            List<JSONObject> items=EtknApi.rows(result);for(JSONObject item:items)posterCard(works,item,()->detail(item),result.optString("EpisodeCountMode","unplayed"));loaded+=items.size();
            int total=result.optInt("TotalRecordCount",loaded);status.setText(total==0?"片库内暂无可见关联媒体":"已显示 "+loaded+" / "+total+" 项");more.setVisibility(items.isEmpty()||loaded>=total?android.view.View.GONE:android.view.View.VISIBLE);
        });
    }
}
