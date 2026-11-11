package xyz.etkn.tv;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;

/** Shared square album covers and compact recommendation rows. */
final class MusicViews {
    static String artists(JSONObject item){
        JSONArray names=item.optJSONArray("Artists");
        if(names==null)return item.optString("Artist");
        java.util.List<String> values=new java.util.ArrayList<>();for(int i=0;i<names.length();i++)values.add(names.optString(i));
        return android.text.TextUtils.join(" / ",values);
    }
    static String artwork(EtknApi api,JSONObject item,int width){
        String hash=item.optString("ArtworkHash");
        if(!hash.isEmpty())return api.server+"/api/image_proxy?cache="+EtknApi.encode(hash)+"&maxWidth="+width+("artists".equals(item.optString("Kind"))?"&variantGroup=avatar":"");
        JSONObject artist=item.optJSONObject("ArtistImage");
        if(artist!=null)return api.server+"/api/tv/Items/"+EtknApi.encode(artist.optString("ItemId"))+"/Images/Backdrop/"+artist.optInt("Index")+"?maxWidth="+width+"&tag="+EtknApi.encode(artist.optString("Tag"))+"&variantGroup=avatar";
        return api.artwork(item,"Primary",width);
    }
    static View card(TvActivity host,GridLayout grid,JSONObject item,Runnable click,boolean flat){
        LinearLayout card=flat?host.row():host.column();card.setFocusable(true);card.setClickable(true);card.setOnClickListener(view->click.run());
        if(flat){card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(host.dp(8),host.dp(8),host.dp(12),host.dp(8));card.setBackground(host.focusBackground());host.shadow(card,4);}
        FrameLayout cover=new FrameLayout(host);cover.setBackground(host.shape(TvActivity.PANEL,0));cover.setClipToOutline(true);cover.setForeground(Images.border(host.dp(12)));host.shadow(cover,flat?0:4);
        TextView placeholder=host.text("♪",flat?24:40,TvActivity.ACCENT);placeholder.setGravity(Gravity.CENTER);cover.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(host);image.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(image,host.dp(10));cover.addView(image,new FrameLayout.LayoutParams(-1,-1));
        card.addView(cover,new LinearLayout.LayoutParams(flat?host.imageDp(52):-1,flat?host.imageDp(52):host.imageDp(140)));
        LinearLayout captions=host.column();if(flat)captions.setPadding(host.dp(12),0,0,0);card.addView(captions,new LinearLayout.LayoutParams(flat?0:-1,-2,flat?1:0));
        TextView title=host.text(item.optString("Name","未命名"),14,TvActivity.TEXT);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);captions.addView(title);
        String subtitle=item.has("Count")?item.optInt("Count")+" 首"+(artists(item).isEmpty()?"":" · "+artists(item)):artists(item);
        TextView detail=host.text(subtitle,12,TvActivity.MUTED);detail.setSingleLine(true);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);captions.addView(detail);
        if(host.isAdaptiveGrid(grid)){host.addAdaptiveGridItem(grid,card,12,12);if(!flat)host.fitArtworkHeight(card,cover,1);}
        else{GridLayout.LayoutParams bounds=new GridLayout.LayoutParams();bounds.width=host.imageDp(flat?300:140);bounds.height=host.imageDp(flat?76:198);bounds.setMargins(host.dp(12),host.dp(12),host.dp(12),host.dp(12));grid.addView(card,bounds);}
        card.setOnFocusChangeListener((view,focused)->{card.setScaleX(focused?1.04f:1);card.setScaleY(focused?1.04f:1);host.shadow(flat?card:cover,focused?8:4);if(!flat)cover.setForeground(focused?host.focusOutline():Images.border(host.dp(12)));});
        card.setContentDescription(item.optString("Name")+" · "+subtitle);Images.visibleArtwork(host.api,width->artwork(host.api,item,width),image,success->{});
        return card;
    }
    static String time(long milliseconds){long seconds=Math.max(0,milliseconds/1000);return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
}
