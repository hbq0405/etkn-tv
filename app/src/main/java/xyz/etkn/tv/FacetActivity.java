package xyz.etkn.tv;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Library facet cards, with explicit artwork actions and ordinary Back navigation. */
public final class FacetActivity extends TvActivity {
    private GridLayout cards;
    private Button batchArtwork;
    private boolean cancelled;
    private String artworkResult="";
    private String parent,kind,focusId="";
    private boolean canArtwork,busy,loading;
    private int request;
    private AlertDialog actionMenu;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);parent=getIntent().getStringExtra("parent");kind=getIntent().getStringExtra("kind");
        ScrollView scroll=new FocusScroll.Vertical(this);scroll.setClipToPadding(false);scroll.setSmoothScrollingEnabled(false);
        content=column();content.setPadding(dp(40),dp(24),dp(40),dp(24));scroll.addView(content);setContentView(scroll);
        status=text("正在读取…",14,MUTED);
        content.addView(text(getIntent().getStringExtra("title"),24,TEXT));
        batchArtwork=button("Types".equals(kind)?"生成封面":"拉取图标",()->{List<JSONObject> items=new ArrayList<>();for(int i=0;i<cards.getChildCount();i++)items.add((JSONObject)cards.getChildAt(i).getTag());startArtwork(items);});
        batchArtwork.setVisibility(View.GONE);content.addView(batchArtwork);content.addView(status);
        cards=new GridLayout(this);cards.setClipChildren(false);cards.setClipToPadding(false);cards.setPadding(0,dp(12),0,dp(12));watchGridColumns(cards,192,12,80);content.addView(cards);
        async(()->api.get("/api/tv/Users/Me"),user->{JSONObject policy=user.optJSONObject("Policy"),tv=user.optJSONObject("TvPermissions");canArtwork=policy!=null&&policy.optBoolean("IsAdministrator")||tv!=null&&tv.optBoolean("CanRefreshMetadata");updateArtworkButton();});
        load();
    }
    private String path(){return api.userPath()+"/Facets?ParentId="+EtknApi.encode(parent)+"&Kind="+EtknApi.encode(kind);}
    private String artwork(JSONObject item){return api.userPath()+"/FacetArtwork?ParentId="+EtknApi.encode(parent)+"&Kind="+EtknApi.encode(item.optString("Kind"))+"&Value="+EtknApi.encode(item.optString("Value"));}
    private void load(){
        if(loading)return;loading=true;updateArtworkButton();int ticket=++request;
        async(()->{try{return api.get(path());}finally{runOnUiThread(()->{loading=false;updateArtworkButton();});}},result->{
            if(ticket!=request)return;View current=getCurrentFocus();if(!"batch".equals(focusId)&&current!=null&&current.getTag() instanceof JSONObject)focusId=((JSONObject)current.getTag()).optString("Id");
            cards.removeAllViews();for(JSONObject item:EtknApi.rows(result))addCard(item);
            updateArtworkButton();status.setText(artworkResult.isEmpty()?(cards.getChildCount()==0?"暂无可展示的分类":"共 "+cards.getChildCount()+" 项"):artworkResult);
            if("batch".equals(focusId)){batchArtwork.requestFocus();return;}
            for(int i=0;i<cards.getChildCount();i++)if(focusId.equals(((JSONObject)cards.getChildAt(i).getTag()).optString("Id"))){cards.getChildAt(i).requestFocus();return;}
            if(cards.getChildCount()>0)cards.getChildAt(0).requestFocus();
        });
    }
    private void addCard(JSONObject item){
        LinearLayout card=column();card.setTag(item);card.setFocusable(true);card.setClickable(true);card.setClipChildren(false);card.setClipToPadding(false);card.setContentDescription(item.optString("Name"));
        FrameLayout art=new FrameLayout(this);art.setBackground(shape(PANEL,0));art.setClipToOutline(true);shadow(art,4);art.setForeground(Images.border(dp(12)));card.addView(art,new LinearLayout.LayoutParams(-1,dp(108)));
        TextView placeholder=text(item.optString("Name"),24,TEXT);placeholder.setGravity(Gravity.CENTER);placeholder.setMaxLines(2);placeholder.setPadding(dp(10),0,dp(10),0);art.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        if(item.has("ImageTags")){ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(image,dp(9));art.addView(image,new FrameLayout.LayoutParams(-1,-1));Images.visibleThumbnail(api,api.server+artwork(item)+"&tag="+EtknApi.encode(item.optJSONObject("ImageTags").optString("Primary")),image,dp(208),dp(117),success->{if(success)placeholder.setVisibility(View.INVISIBLE);});}
        TextView label=text(item.optString("Name"),12,TEXT);label.setMaxLines(2);label.setPadding(0,dp(4),0,0);card.addView(label,new LinearLayout.LayoutParams(-1,dp(42)));
        addAdaptiveGridItem(cards,card,12,5);fitArtworkHeight(card,art,9f/16f);
        card.setOnFocusChangeListener((view,focused)->{card.setScaleX(focused?FOCUS_SCALE:1f);card.setScaleY(focused?FOCUS_SCALE:1f);card.setTranslationZ(0);shadow(art,focused?8:4);art.setForeground(focused?focusOutline():Images.border(dp(12)));});
        card.setOnClickListener(view->{focusId=item.optString("Id");startActivity(new Intent(this,HomeActivity.class).putExtra("facetKind",item.optString("Kind")).putExtra("facetValue",item.optString("Value")).putExtra("facetParent",parent));});
        card.setOnLongClickListener(view->{showActions(item);return true;});
        card.setOnGenericMotionListener((view,event)->{if(event.getAction()==MotionEvent.ACTION_BUTTON_PRESS&&event.getActionButton()==MotionEvent.BUTTON_SECONDARY){card.requestFocus();showActions(item);return true;}return false;});
    }
    private void showActions(JSONObject item){
        if(!canArtwork||busy||actionMenu!=null&&actionMenu.isShowing())return;
        boolean studio="Networks".equals(item.optString("Kind"))||"Companies".equals(item.optString("Kind"));
        String[] labels=studio?new String[]{"拉取图标"}:new String[]{"生成封面"};
        actionMenu=new AlertDialog.Builder(this).setTitle(item.optString("Name")).setItems(labels,(dialog,index)->{
            startArtwork(java.util.Collections.singletonList(item));
        }).setNegativeButton("取消",null).show();
    }
    private void updateArtworkButton(){
        batchArtwork.setVisibility(canArtwork?View.VISIBLE:View.GONE);
        batchArtwork.setEnabled(canArtwork&&!busy&&!loading&&cards.getChildCount()>0);
    }
    private void startArtwork(List<JSONObject> items){
        if(!canArtwork||busy||loading||items.isEmpty())return;
        View focused=getCurrentFocus();focusId=focused!=null&&focused.getTag() instanceof JSONObject?((JSONObject)focused.getTag()).optString("Id"):"batch";
        busy=true;cancelled=false;artworkResult="";updateArtworkButton();generateNext(items,0,0);
    }
    private void generateNext(List<JSONObject> items,int index,int succeeded){
        if(cancelled||index==items.size()){
            busy=false;artworkResult=(cancelled?"已停止，":"已完成，")+"成功 "+succeeded+" 项，失败 "+(index-succeeded)+" 项";updateArtworkButton();status.setText(artworkResult);load();return;
        }
        JSONObject item=items.get(index);boolean studio="Networks".equals(item.optString("Kind"))||"Companies".equals(item.optString("Kind"));
        status.setText((studio?"正在拉取图标 ":"正在生成封面 ")+(index+1)+" / "+items.size()+" · "+item.optString("Name"));
        async(()->{try{api.post(artwork(item)+(studio?"&Action=icon":""),new JSONObject());return true;}catch(EtknApi.AuthException error){throw error;}catch(Exception error){return false;}},success->generateNext(items,index+1,succeeded+(success?1:0)));
    }
    @Override protected void onStop(){cancelled=true;super.onStop();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getKeyCode()==KeyEvent.KEYCODE_MENU&&getCurrentFocus()!=null&&getCurrentFocus().getTag() instanceof JSONObject){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)showActions((JSONObject)getCurrentFocus().getTag());return true;}
        return super.dispatchKeyEvent(event);
    }
}
