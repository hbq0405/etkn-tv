package xyz.etkn.tv;

import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
import java.util.HashSet;
import java.util.Set;

public final class SetupActivity extends TvActivity {
    private LanDiscovery discovery;
    private EditText server,username,password;
    private LinearLayout servers;
    private int scanRevision;
    private final Set<String> discovered=new HashSet<>();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(!api.token.isEmpty()){open(HomeActivity.class);finish();return;}
        screen("连接你的 ETKN", "自动发现局域网服务器，遥控器点选后登录。");
        nav("重新搜索",this::scan);nav("关于 / 检查更新",()->open(SettingsActivity.class));
        servers=column();content.addView(servers);
        server=input("媒体服务器地址，例如 192.168.1.10:8097",false);server.setText(api.session.server());content.addView(server);
        username=input("用户名",false);content.addView(username);password=input("密码",true);content.addView(password);
        content.addView(button("连接并登录",()->{
            status.setText("正在登录…");String address=server.getText().toString(),name=username.getText().toString(),pass=password.getText().toString();
            async(()->{api.login(EtknApi.normalizeServer(address),name,pass);return true;},ok->{password.setText("");open(HomeActivity.class);finish();});
        }));scan();
    }
    private void scan(){
        int revision=++scanRevision;if(discovery!=null)discovery.cancel();discovered.clear();servers.removeAllViews();status.setText("正在搜索同网段 ETKN 媒体服务器…");
        discovery=new LanDiscovery();discovery.scan(this,item->runOnUiThread(()->{
            if(isDestroyed()||revision!=scanRevision||!discovered.add(item.optString("url")))return;
            Button choice=button(item.optString("name")+"   ·   "+item.optString("url"),()->{server.setText(item.optString("url"));username.requestFocus();});
            servers.addView(choice);if(discovered.size()==1)choice.requestFocus();
        }),()->runOnUiThread(()->{if(!isDestroyed()&&revision==scanRevision)status.setText(discovered.isEmpty()?"未找到服务器。请确认电视与 ETKN 在同一局域网，或手动填写媒体服务器地址。":"找到 "+discovered.size()+" 台服务器，选择后登录。");}));
    }
    @Override protected void onDestroy(){if(discovery!=null)discovery.cancel();super.onDestroy();}
}
