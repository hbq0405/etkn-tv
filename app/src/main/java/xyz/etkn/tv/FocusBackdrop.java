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
    private static final long TRANSITION_MS=320;
    private final TvActivity activity;
    private final ImageView backdrop,previousBackdrop,imageLoader,pendingLogo,logo;
    private final TextView title,metadata,overview;
    private final LinearLayout copy,information;
    private final Button action;
    private JSONObject current;
    private boolean actionVisible;
    private boolean resumeFocused;
    private boolean visibleShowDetails,pendingShowDetails,pendingContent,pendingLogoReady,transitionRunning;
    private Bitmap sharp,blurred,lastBlurred,displayed;
    private String imageKey="",pendingBlur="";
    private String pendingTitle="",pendingMetadata="",pendingOverview="";
    private int contentSerial;
    private final android.util.LruCache<String,Bitmap> blurredImages=new android.util.LruCache<>(1024*1024){@Override protected int sizeOf(String key,Bitmap image){return image.getAllocationByteCount();}};

    FocusBackdrop(TvActivity activity){
        super(activity);this.activity=activity;setBackgroundColor(TvActivity.INK);
        backdrop=new ImageView(activity);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(backdrop,activity.dp(10));addView(backdrop,new LayoutParams(-1,-1));
        previousBackdrop=new ImageView(activity);previousBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(previousBackdrop,activity.dp(10));addView(previousBackdrop,new LayoutParams(-1,-1));
        imageLoader=new ImageView(activity);imageLoader.setVisibility(GONE);addView(imageLoader,new LayoutParams(0,0));
        pendingLogo=new ImageView(activity);pendingLogo.setVisibility(GONE);addView(pendingLogo,new LayoutParams(0,0));
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
        current=item;contentSerial++;pendingContent=true;pendingLogoReady=false;sharp=null;blurred=null;imageKey="";pendingBlur="";
        int serial=contentSerial;
        pendingTitle=library?EtknApi.title(item):EtknApi.resumeTitle(item);
        String type=library?"媒体库":"Episode".equals(item.optString("Type"))||"Series".equals(item.optString("Type"))?"剧集":"电影";
        String rating=item.optString("OfficialRating"),score=item.optDouble("CommunityRating",0)>0?"★ "+item.optString("CommunityRating"):"";
        pendingMetadata=join(type,item.optString("ProductionYear"),rating,score,genres(item));pendingOverview=item.optString("Overview");pendingShowDetails=showDetails;
        pendingLogo.setImageDrawable(null);pendingLogo.setTag("none:"+serial);imageLoader.setImageDrawable(null);
        String image=library?activity.api.artwork(item,"Primary",1280):activity.api.artwork(item,"Backdrop",1280);
        Images.backdrop(activity.api,image,imageLoader,success->{if(serial!=contentSerial)return;String fallback=activity.api.image(item);if(success)imageLoaded(serial);else if(!image.equals(fallback))Images.backdrop(activity.api,fallback,imageLoader,loaded->{if(serial!=contentSerial)return;if(loaded)imageLoaded(serial);else imageUnavailable(serial);});else imageUnavailable(serial);});
        if(showDetails&&!library){
            String logoUrl=activity.api.artwork(item,"Logo",600);JSONObject tags=item.optJSONObject("ImageTags");
            boolean hasLogo=tags!=null&&!tags.optString("Logo").isEmpty()||!item.optString("ParentLogoItemId").isEmpty()&&!item.optString("ParentLogoImageTag").isEmpty();
            if(hasLogo)Images.backdrop(activity.api,logoUrl,pendingLogo,success->{if(serial!=contentSerial)return;pendingLogoReady=success;if(!pendingContent)applyPendingLogo();});
        }
    }

    void clear(){contentSerial++;cancelTransition(true);current=null;pendingContent=false;visibleShowDetails=false;sharp=null;blurred=null;lastBlurred=null;displayed=null;imageKey="";pendingBlur="";imageLoader.setTag("none:"+System.nanoTime());imageLoader.setImageDrawable(null);pendingLogo.setTag("none:"+System.nanoTime());pendingLogo.setImageDrawable(null);backdrop.setImageDrawable(null);logo.setImageDrawable(null);logo.setTag("none:"+System.nanoTime());logo.setVisibility(GONE);title.setText("");title.setVisibility(GONE);metadata.setText("");overview.setText("");action.setVisibility(GONE);copy.setAlpha(1f);copy.setVisibility(GONE);resumeFocused(false);}

    /** Focus controls only the backdrop image and information; the carousel action stays operable. */
    void resumeFocused(boolean focused){boolean changed=resumeFocused!=focused;if(changed)cancelTransition(true);resumeFocused=focused;boolean showInfo=focused&&visibleShowDetails&&(!changed||!pendingContent);information.setVisibility(showInfo?VISIBLE:GONE);action.setVisibility(actionVisible&&showInfo?VISIBLE:GONE);displayImage(false);}
    private void imageLoaded(int serial){
        if(serial!=contentSerial)return;
        sharp=((BitmapDrawable)imageLoader.getDrawable()).getBitmap();imageKey=String.valueOf(imageLoader.getTag());blurred=blurredImages.get(imageKey);displayImage(true);
    }
    private void imageUnavailable(int serial){if(serial!=contentSerial)return;sharp=null;blurred=null;pendingBlur="";render(null,false,true);}
    private void displayImage(boolean transition){
        if(blurred!=null)lastBlurred=blurred;
        Bitmap target=resumeFocused?sharp:blurred;boolean ready=target!=null;if(target==null&&!resumeFocused)target=lastBlurred;
        render(target,transition,ready);
        if(sharp==null)return;
        if(blurred!=null||imageKey.equals(pendingBlur))return;
        Bitmap source=sharp;String key=imageKey;pendingBlur=key;
        TvActivity.IO.execute(()->{
            Bitmap result=BackdropBlur.create(source);
            post(()->{blurredImages.put(key,result);if(sharp==source&&imageKey.equals(key)){pendingBlur="";blurred=result;displayImage(true);}});
        });
    }

    private void render(Bitmap image,boolean transition,boolean ready){
        if(displayed==image){if(ready&&!transitionRunning){backdrop.setAlpha(1f);commitPendingContent();revealContent();}return;}
        Bitmap previous=displayed;cancelTransition(false);displayed=image;backdrop.setImageBitmap(image);
        if(!transition||previous==null||image==null||!ready){backdrop.setAlpha(1f);if(ready){commitPendingContent();revealContent();}return;}
        transitionRunning=true;
        previousBackdrop.setImageBitmap(previous);previousBackdrop.setAlpha(1f);backdrop.setAlpha(0f);
        if(pendingContent)copy.animate().alpha(0f).setDuration(TRANSITION_MS).start();else copy.setAlpha(1f);
        previousBackdrop.animate().alpha(0f).setDuration(TRANSITION_MS).withEndAction(()->{previousBackdrop.setImageDrawable(null);commitPendingContent();backdrop.animate().alpha(1f).setDuration(TRANSITION_MS).withEndAction(()->transitionRunning=false).start();fadeInContent();}).start();
    }
    private void cancelTransition(boolean revealCurrent){
        backdrop.animate().cancel();previousBackdrop.animate().cancel();copy.animate().cancel();transitionRunning=false;if(revealCurrent)backdrop.setAlpha(1f);copy.setAlpha(revealCurrent?1f:copy.getAlpha());previousBackdrop.setAlpha(0f);previousBackdrop.setImageDrawable(null);
    }
    private void commitPendingContent(){
        if(!pendingContent)return;
        title.setText(pendingTitle);metadata.setText(pendingMetadata);overview.setText(pendingOverview);visibleShowDetails=pendingShowDetails;copy.setVisibility(visibleShowDetails?VISIBLE:GONE);information.setVisibility(resumeFocused&&visibleShowDetails?VISIBLE:GONE);applyPendingLogo();action.setVisibility(actionVisible&&resumeFocused&&visibleShowDetails?VISIBLE:GONE);pendingContent=false;
    }
    private void applyPendingLogo(){
        logo.setImageDrawable(null);logo.setVisibility(GONE);title.setVisibility(VISIBLE);if(pendingLogoReady&&pendingLogo.getDrawable()!=null){logo.setImageDrawable(pendingLogo.getDrawable());logo.setVisibility(VISIBLE);title.setVisibility(GONE);}
    }
    private void revealContent(){
        transitionRunning=false;
        copy.setAlpha(1f);
    }
    private void fadeInContent(){
        if(copy.getVisibility()==VISIBLE)copy.animate().alpha(1f).setDuration(TRANSITION_MS).start();else copy.setAlpha(1f);
    }

    /** 轮播模式下展示“查看详情”按钮作为焦点目标；普通联动背景保持隐藏。 */
    void actionVisible(boolean on){actionVisible=on;action.setVisibility(on&&resumeFocused&&visibleShowDetails?VISIBLE:GONE);}
    View actionView(){return action;}

    private String genres(JSONObject item){org.json.JSONArray values=item.optJSONArray("Genres");if(values==null)return "";StringBuilder result=new StringBuilder();for(int index=0;index<values.length();index++){String value=values.optString(index);if(value.isEmpty())continue;if(result.length()>0)result.append(" / ");result.append(value);}return result.toString();}
    private String join(String...parts){StringBuilder result=new StringBuilder();for(String part:parts)if(part!=null&&!part.isEmpty()){if(result.length()>0)result.append("  ·  ");result.append(part);}return result.toString();}
}
