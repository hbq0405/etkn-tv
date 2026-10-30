package xyz.etkn.tv;

import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SetupActivity extends TvActivity {
    static final String EXTRA_ADD_ACCOUNT="addAccount";
    private LanDiscovery discovery;
    private EditText server,username,password;
    private LinearLayout servers,savedUsers;
    private int scanRevision;
    private final Set<String> discovered=new HashSet<>();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        boolean addAccount=getIntent().getBooleanExtra(EXTRA_ADD_ACCOUNT,false);
        if(addAccount){showLogin(true);return;}
        screen("连接你的 ETKN","正在检查已保存账号的登录规则…");
        nav("重新检查",()->recreate());
        IO.execute(()->{
            try{api.refreshSavedAccounts();runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){api=new EtknApi(this);showLogin(false);}});}
            catch(Exception error){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){showLogin(true);status.setText("无法检查已保存账号，请重新登录或返回重试。");}});}
        });
    }
    private void showLogin(boolean addAccount){
        if(!addAccount&&(LoginGate.requiresPicker(this,api)||api.session.persist()==SessionStore.PERSIST_ACCOUNT&&!api.session.accounts().isEmpty())){showPicker();return;}
        if(!api.token.isEmpty()&&!addAccount){openHome();return;}
        screen(addAccount?"登录新用户":"连接你的 ETKN",addAccount?"为当前 ETKN 媒体服务器添加登录用户。":"自动发现局域网 ETKN 媒体服务器，遥控器点选后登录。");
        if(!addAccount){nav("重新搜索",this::scan);nav("关于 / 检查更新",()->open(SettingsActivity.class));}
        if(!addAccount){
            savedUsers=column();
            List<SavedAccounts.Account> accounts=api.session.accounts();
            if(!accounts.isEmpty()){
                content.addView(text("已登录用户",16,android.graphics.Color.WHITE));content.addView(savedUsers);
                for(SavedAccounts.Account account:accounts)savedUsers.addView(button((account.needsPassword?"登录（需密码）：":"使用：")+account.name,()->loginWithPasswordIfNeeded(account)));
            }
        }
        servers=column();content.addView(servers);
        server=input("媒体服务器地址，例如 192.168.1.10:8097",false);server.setText(api.session.server());content.addView(server);
        if(addAccount){server.setFocusable(false);server.setFocusableInTouchMode(false);server.setEnabled(false);servers.setVisibility(android.view.View.GONE);content.addView(text("账号仅保存在当前 ETKN 媒体服务器下，不支持多服务器账号切换。",13,MUTED));}
        username=input("用户名",false);content.addView(username);password=input("密码",true);content.addView(password);
        content.addView(button("连接并登录",()->{
            status.setText("正在登录…");String address=server.getText().toString(),name=username.getText().toString(),pass=password.getText().toString();
            password.setText("");int generation=LoginGate.generation();
            IO.execute(()->{
                try{api.login(EtknApi.normalizeServer(address),name,pass);runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&generation==LoginGate.generation())openHome();});}
                catch(Exception error){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())status.setText(error instanceof EtknApi.AuthException?"用户名或密码错误":error.getMessage());});}
            });
        }));if(!addAccount)scan();
    }
    private void openHome(){LoginGate.satisfy();startActivity(new Intent(this,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}
    /** 用户选择框：居中面板横向展示已登录用户头像，末尾提供登录新用户入口。 */
    private void showPicker(){
        screen("选择登录用户","点选要进入的用户，也可以登录新用户。");
        content.setGravity(android.view.Gravity.CENTER);
        LinearLayout panel=column();panel.setBackground(shape(PANEL,0));panel.setPadding(dp(30),dp(24),dp(30),dp(20));panel.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        FocusScroll.Row scroll=new FocusScroll.Row(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout users=row();users.setGravity(android.view.Gravity.CENTER_VERTICAL);scroll.addView(users);
        List<SavedAccounts.Account> accounts=api.session.accounts();
        for(SavedAccounts.Account account:accounts){
            ImageView avatar=new ImageView(this);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);Images.rounded(avatar,dp(44));avatar.setImageResource(android.R.drawable.ic_menu_myplaces);
            LinearLayout card=pickerCard(account.name,avatar,()->loginWithPasswordIfNeeded(account));
            card.setContentDescription("登录用户 "+account.name);
            users.addView(card);
            Images.load(api,api.server+"/api/tv/Users/"+EtknApi.encode(account.userId)+"/Images/Primary?maxWidth=160",avatar,api.session.accountToken(account.userId));
        }
        TextView plus=new TextView(this);plus.setText("+");plus.setTextSize(34);plus.setTextColor(MUTED);plus.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable circle=new android.graphics.drawable.GradientDrawable();circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);circle.setColor(PANEL);circle.setStroke(dp(2),MUTED);plus.setBackground(circle);
        users.addView(pickerCard("登录新用户",plus,()->startActivity(new Intent(this,SetupActivity.class).putExtra(EXTRA_ADD_ACCOUNT,true))));
        panel.addView(scroll);
        content.addView(panel,new LinearLayout.LayoutParams(-2,-2));
        panel.post(()->{if(users.getChildCount()>0)users.getChildAt(0).requestFocus();});
    }
    /** 选择框内的用户/新用户卡片：图标 + 名称，聚焦缩放并显示焦点边框。 */
    private LinearLayout pickerCard(String label,android.view.View icon,Runnable click){
        LinearLayout card=column();card.setGravity(android.view.Gravity.CENTER_HORIZONTAL);card.setPadding(dp(18),dp(14),dp(18),dp(10));card.setBackground(focusBackground());card.setFocusable(true);card.setClickable(true);
        card.addView(icon,new LinearLayout.LayoutParams(dp(88),dp(88)));
        TextView name=text(label,15,android.graphics.Color.WHITE);name.setGravity(android.view.Gravity.CENTER);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLayout=new LinearLayout.LayoutParams(dp(120),-2);nameLayout.topMargin=dp(6);card.addView(name,nameLayout);
        card.setOnClickListener(view->click.run());
        card.setOnFocusChangeListener((view,focused)->{view.setScaleX(focused?FOCUS_SCALE:1f);view.setScaleY(focused?FOCUS_SCALE:1f);view.setTranslationZ(focused?dp(4):0);});
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-2,-2);layout.setMargins(dp(6),dp(6),dp(6),dp(6));card.setLayoutParams(layout);
        return card;
    }
    /** 需要密码的账号（如管理员）选择时先输入密码完成完整登录，令牌不落盘。 */
    private void loginWithPasswordIfNeeded(SavedAccounts.Account account){selectAccount(this,account,this::openHome);}
    static void selectAccount(TvActivity host,SavedAccounts.Account account,Runnable onSuccess){
        if(account.needsPassword){promptPassword(host,account,onSuccess);return;}
        host.status.setText("正在检查账号…");
        host.async(()->{
            int persist=host.api.savedAccountPolicy(account.userId);
            if(persist==SessionStore.PERSIST_FULL)host.api.session.activate(account.userId);
            return persist;
        },persist->{
            if(persist==SessionStore.PERSIST_FULL)onSuccess.run();
            else if(persist==SessionStore.PERSIST_ACCOUNT)promptPassword(host,account,onSuccess);
            else host.startActivity(new Intent(host,SetupActivity.class).putExtra(EXTRA_ADD_ACCOUNT,true));
        });
    }
    /** 密码输入弹窗：服务器固定为当前服务器，只输入密码；失败留在当前页面提示，不改变现有会话。 */
    static void promptPassword(TvActivity host,SavedAccounts.Account account,Runnable onSuccess){
        EditText input=host.input("输入 "+account.name+" 的密码",true);
        input.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1,host.dp(48)));
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(host).setTitle("登录 "+account.name).setView(input)
                .setPositiveButton("登录",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(shown->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{
            String password=input.getText().toString();input.setText("");input.setError(null);
            if(password.isEmpty()){input.setError("请输入密码");return;}
            int generation=LoginGate.generation();dialog.setCancelable(false);dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            host.status.setText("正在登录…");
            TvActivity.IO.execute(()->{
                try{host.api.login(host.api.session.server(),account.name,password);host.runOnUiThread(()->{if(!host.isFinishing()&&!host.isDestroyed()&&generation==LoginGate.generation()){dialog.dismiss();onSuccess.run();}});}
                catch(Exception error){host.runOnUiThread(()->{if(!host.isFinishing()&&!host.isDestroyed()){dialog.setCancelable(true);dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setEnabled(true);dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);input.setError(error instanceof EtknApi.AuthException?"用户名或密码错误":(error.getMessage()==null?"登录失败，请重试":error.getMessage()));}});}
            });
        }));
        dialog.show();input.requestFocus();
    }
    private void scan(){
        int revision=++scanRevision;if(discovery!=null)discovery.cancel();discovered.clear();if(servers==null)return;servers.removeAllViews();status.setText("正在搜索同网段 ETKN 媒体服务器…");
        discovery=new LanDiscovery();discovery.scan(this,item->runOnUiThread(()->{
            if(isDestroyed()||revision!=scanRevision||!discovered.add(item.optString("url")))return;
            Button choice=button(item.optString("name")+"   ·   "+item.optString("url"),()->{server.setText(item.optString("url"));username.requestFocus();});
            servers.addView(choice);if(discovered.size()==1)choice.requestFocus();
        }),()->runOnUiThread(()->{if(!isDestroyed()&&revision==scanRevision)status.setText(discovered.isEmpty()?"未找到服务器。请确认电视与 ETKN 在同一局域网，或手动填写媒体服务器地址。":"找到 "+discovered.size()+" 台服务器，选择后登录。");}));
    }
    @Override protected void onDestroy(){if(discovery!=null)discovery.cancel();super.onDestroy();}
    @Override protected void onStop(){if(password!=null)password.setText("");super.onStop();}
}
