package xyz.etkn.tv;

import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

final class FocusBackdrop extends FrameLayout {
    private final TvActivity activity;
    private final ImageView backdrop,logo;
    private final TextView title,metadata,overview;
    private final LinearLayout copy,information;
    private final Button action;
    private JSONObject current;
    private boolean actionVisible;
    private boolean resumeFocused;
    private Bitmap sharp,blurred;
    private String imageKey="",pendingBlur="";
    private final android.util.LruCache<String,Bitmap> blurredImages=new android.util.LruCache<>(1024*1024){@Override protected int sizeOf(String key,Bitmap image){return image.getAllocationByteCount();}};

    FocusBackdrop(TvActivity activity){
        super(activity);this.activity=activity;setBackgroundColor(TvActivity.INK);
        backdrop=new ImageView(activity);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(backdrop,activity.dp(10));addView(backdrop,new LayoutParams(-1,-1));
        addView(activity.backdropShade(),new LayoutParams(-1,-1));
        copy=activity.column();copy.setPadding(activity.dp(30),0,activity.dp(24),0);
        LayoutParams copyLayout=new LayoutParams(activity.dp(540),-2,Gravity.TOP|Gravity.START);copyLayout.topMargin=activity.dp(54);addView(copy,copyLayout);
        information=activity.column();information.setVisibility(GONE);copy.addView(information);
        FrameLayout identity=new FrameLayout(activity);information.addView(identity,new LinearLayout.LayoutParams(-1,activity.dp(88)));
        title=activity.text("",32,Color.WHITE);title.setTypeface(null,Typeface.BOLD);title.setMaxLines(2);identity.addView(title,new LayoutParams(-1,-1));
        logo=new ImageView(activity);logo.setScaleType(ImageView.ScaleType.FIT_START);Images.rounded(logo,activity.dp(7));logo.setVisibility(GONE);identity.addView(logo,new LayoutParams(activity.dp(340),activity.dp(82),Gravity.CENTER_VERTICAL));
        metadata=activity.text("",14,Color.rgb(207,222,231));information.addView(metadata);
        overview=activity.text("",15,Color.rgb(226,232,238));overview.setMaxLines(2);overview.setEllipsize(android.text.TextUtils.TruncateAt.END);information.addView(overview);
        action=activity.button("查看详情",()->{if(current!=null)activity.detail(current);});action.setVisibility(GONE);copy.addView(action);
    }

    void show(JSONObject item,boolean library,boolean showDetails){
        current=item;sharp=null;blurred=null;imageKey="";pendingBlur="";
        String itemTitle=library?EtknApi.title(item):EtknApi.resumeTitle(item);
        copy.setVisibility(showDetails?VISIBLE:GONE);action.setVisibility(actionVisible&&showDetails?VISIBLE:GONE);title.setText(itemTitle);title.setVisibility(VISIBLE);logo.setVisibility(GONE);logo.setImageDrawable(null);logo.setTag("none:"+System.nanoTime());backdrop.setImageDrawable(null);
        String type=library?"媒体库":"Episode".equals(item.optString("Type"))||"Series".equals(item.optString("Type"))?"剧集":"电影";
        String rating=item.optString("OfficialRating"),score=item.optDouble("CommunityRating",0)>0?"★ "+item.optString("CommunityRating"):"";
        metadata.setText(join(type,item.optString("ProductionYear"),rating,score,genres(item)));overview.setText(item.optString("Overview"));
        String image=library?activity.api.artwork(item,"Primary",1280):activity.api.artwork(item,"Backdrop",1280);
        Images.backdrop(activity.api,image,backdrop,success->{String fallback=activity.api.image(item);if(success)imageLoaded();else if(!image.equals(fallback))Images.backdrop(activity.api,fallback,backdrop,loaded->{if(loaded)imageLoaded();});});
        if(showDetails&&!library){
            String logoUrl=activity.api.artwork(item,"Logo",600);JSONObject tags=item.optJSONObject("ImageTags");
            boolean hasLogo=tags!=null&&!tags.optString("Logo").isEmpty()||!item.optString("ParentLogoItemId").isEmpty()&&!item.optString("ParentLogoImageTag").isEmpty();
            if(hasLogo)Images.backdrop(activity.api,logoUrl,logo,success->{logo.setVisibility(success?VISIBLE:GONE);title.setVisibility(success?GONE:VISIBLE);});
        }
    }

    void clear(){sharp=null;blurred=null;imageKey="";pendingBlur="";backdrop.setTag("none:"+System.nanoTime());backdrop.setImageDrawable(null);logo.setImageDrawable(null);logo.setTag("none:"+System.nanoTime());logo.setVisibility(GONE);title.setText("");title.setVisibility(GONE);metadata.setText("");overview.setText("");action.setVisibility(GONE);copy.setVisibility(GONE);resumeFocused(false);}

    /** Focus controls only the backdrop image and information; the carousel action stays operable. */
    void resumeFocused(boolean focused){resumeFocused=focused;information.setVisibility(focused?VISIBLE:GONE);displayImage();}
    private void imageLoaded(){
        sharp=((BitmapDrawable)backdrop.getDrawable()).getBitmap();imageKey=String.valueOf(backdrop.getTag());blurred=blurredImages.get(imageKey);displayImage();
    }
    private void displayImage(){
        if(sharp==null)return;
        backdrop.setImageBitmap(resumeFocused||blurred==null?sharp:blurred);
        if(resumeFocused||blurred!=null||imageKey.equals(pendingBlur))return;
        Bitmap source=sharp;String key=imageKey;pendingBlur=key;
        TvActivity.IO.execute(()->{
            Bitmap result=BackdropBlur.create(source);
            post(()->{blurredImages.put(key,result);if(sharp==source&&imageKey.equals(key)){pendingBlur="";blurred=result;if(!resumeFocused)backdrop.setImageBitmap(result);}});
        });
    }

    /** 轮播模式下展示“查看详情”按钮作为焦点目标；普通联动背景保持隐藏。 */
    void actionVisible(boolean on){actionVisible=on;action.setVisibility(on&&copy.getVisibility()==VISIBLE?VISIBLE:GONE);}
    View actionView(){return action;}

    private String genres(JSONObject item){org.json.JSONArray values=item.optJSONArray("Genres");if(values==null)return "";StringBuilder result=new StringBuilder();for(int index=0;index<values.length();index++){String value=values.optString(index);if(value.isEmpty())continue;if(result.length()>0)result.append(" / ");result.append(value);}return result.toString();}
    private String join(String...parts){StringBuilder result=new StringBuilder();for(String part:parts)if(part!=null&&!part.isEmpty()){if(result.length()>0)result.append("  ·  ");result.append(part);}return result.toString();}
}
