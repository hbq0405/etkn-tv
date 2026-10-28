package xyz.etkn.tv;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

final class FocusBackdrop extends FrameLayout {
    private final TvActivity activity;
    private final ImageView backdrop,logo;
    private final TextView title,metadata,overview;
    private final LinearLayout copy;

    FocusBackdrop(TvActivity activity){
        super(activity);this.activity=activity;setBackgroundColor(TvActivity.INK);
        backdrop=new ImageView(activity);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(backdrop,activity.dp(10));addView(backdrop,new LayoutParams(-1,-1));
        copy=activity.column();copy.setPadding(activity.dp(30),0,activity.dp(24),0);
        LayoutParams copyLayout=new LayoutParams(activity.dp(540),-2,Gravity.TOP|Gravity.START);copyLayout.topMargin=activity.dp(54);addView(copy,copyLayout);
        FrameLayout identity=new FrameLayout(activity);copy.addView(identity,new LinearLayout.LayoutParams(-1,activity.dp(88)));
        title=activity.text("",32,Color.WHITE);title.setTypeface(null,Typeface.BOLD);title.setMaxLines(2);identity.addView(title,new LayoutParams(-1,-1));
        logo=new ImageView(activity);logo.setScaleType(ImageView.ScaleType.FIT_START);Images.rounded(logo,activity.dp(7));logo.setVisibility(GONE);identity.addView(logo,new LayoutParams(activity.dp(340),activity.dp(82),Gravity.CENTER_VERTICAL));
        metadata=activity.text("",14,Color.rgb(207,222,231));copy.addView(metadata);
        overview=activity.text("",15,Color.rgb(226,232,238));overview.setMaxLines(4);copy.addView(overview);
    }

    void show(JSONObject item,boolean library,boolean showDetails){
        String itemTitle=library?EtknApi.title(item):EtknApi.resumeTitle(item);
        copy.setVisibility(showDetails?VISIBLE:GONE);title.setText(itemTitle);title.setVisibility(VISIBLE);logo.setVisibility(GONE);logo.setImageDrawable(null);logo.setTag("none:"+System.nanoTime());backdrop.setImageDrawable(null);
        String type=library?"媒体库":"Episode".equals(item.optString("Type"))||"Series".equals(item.optString("Type"))?"剧集":"电影";
        String rating=item.optString("OfficialRating"),score=item.optDouble("CommunityRating",0)>0?"★ "+item.optString("CommunityRating"):"";
        metadata.setText(join(type,item.optString("ProductionYear"),rating,score,genres(item)));overview.setText(item.optString("Overview"));
        String image=library?activity.api.artwork(item,"Primary",1280):activity.api.artwork(item,"Backdrop",1280);
        Images.load(activity.api,image,backdrop,success->{String fallback=activity.api.image(item);if(!success&&!image.equals(fallback))Images.load(activity.api,fallback,backdrop);});
        if(showDetails&&!library){
            String logoUrl=activity.api.artwork(item,"Logo",600);JSONObject tags=item.optJSONObject("ImageTags");
            boolean hasLogo=tags!=null&&!tags.optString("Logo").isEmpty()||!item.optString("ParentLogoItemId").isEmpty()&&!item.optString("ParentLogoImageTag").isEmpty();
            if(hasLogo)Images.load(activity.api,logoUrl,logo,success->{logo.setVisibility(success?VISIBLE:GONE);title.setVisibility(success?GONE:VISIBLE);});
        }
    }

    void clear(){backdrop.setImageDrawable(null);logo.setImageDrawable(null);logo.setTag("none:"+System.nanoTime());logo.setVisibility(GONE);title.setText("");title.setVisibility(GONE);metadata.setText("");overview.setText("");copy.setVisibility(GONE);}

    private String genres(JSONObject item){org.json.JSONArray values=item.optJSONArray("Genres");if(values==null)return "";StringBuilder result=new StringBuilder();for(int index=0;index<values.length();index++){String value=values.optString(index);if(value.isEmpty())continue;if(result.length()>0)result.append(" / ");result.append(value);}return result.toString();}
    private String join(String...parts){StringBuilder result=new StringBuilder();for(String part:parts)if(part!=null&&!part.isEmpty()){if(result.length()>0)result.append("  ·  ");result.append(part);}return result.toString();}
}
