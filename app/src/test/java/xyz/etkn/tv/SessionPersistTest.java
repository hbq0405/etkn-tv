package xyz.etkn.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class SessionPersistTest {
    private final Map<String,Object> disk=new HashMap<>();
    private SessionStore session;
    @Before public void setup(){
        SessionStore.endTemporarySession();
        Context context=mock(Context.class);SharedPreferences prefs=mock(SharedPreferences.class);
        SharedPreferences.Editor edit=mock(SharedPreferences.Editor.class);
        when(context.getSharedPreferences("session",Context.MODE_PRIVATE)).thenReturn(prefs);
        when(prefs.getString(anyString(),anyString())).thenAnswer(call->disk.getOrDefault(call.getArgument(0),call.getArgument(1)));
        when(prefs.getInt(anyString(),anyInt())).thenAnswer(call->disk.getOrDefault(call.getArgument(0),call.getArgument(1)));
        when(prefs.getBoolean(anyString(),anyBoolean())).thenAnswer(call->disk.getOrDefault(call.getArgument(0),call.getArgument(1)));
        when(prefs.edit()).thenReturn(edit);
        when(edit.putString(anyString(),anyString())).thenAnswer(call->{disk.put(call.getArgument(0),call.getArgument(1));return edit;});
        when(edit.putInt(anyString(),anyInt())).thenAnswer(call->{disk.put(call.getArgument(0),call.getArgument(1));return edit;});
        when(edit.putBoolean(anyString(),anyBoolean())).thenAnswer(call->{disk.put(call.getArgument(0),call.getArgument(1));return edit;});
        when(edit.remove(anyString())).thenAnswer(call->{disk.remove(call.getArgument(0));return edit;});
        when(edit.commit()).thenReturn(true);
        disk.put("server","http://fixture");disk.put("mediaEntryVerified",true);
        session=new SessionStore(context);
    }
    @After public void cleanup(){SessionStore.endTemporarySession();}
    @Test public void privateAccountNeverPersistsToken(){
        assertEquals(SessionStore.PERSIST_NONE,SessionStore.persistPolicy(true,false));
        assertEquals(SessionStore.PERSIST_NONE,SessionStore.persistPolicy(true,true));
    }
    @Test public void administratorKeepsOnlyAccountEntry(){
        assertEquals(SessionStore.PERSIST_ACCOUNT,SessionStore.persistPolicy(false,true));
    }
    @Test public void rememberedAdministratorIsOptInAndCannotOverridePrivacy(){
        assertEquals(SessionStore.PERSIST_FULL,SessionStore.persistPolicy(false,true,true));
        assertEquals(SessionStore.PERSIST_ACCOUNT,SessionStore.persistPolicy(false,true,false));
        assertEquals(SessionStore.PERSIST_NONE,SessionStore.persistPolicy(true,true,true));
    }
    @Test public void adminRememberChoiceIsScopedByServerAndAccount()throws Exception{
        assertFalse(session.rememberAdmin("http://fixture","admin"));session.setRememberAdmin("http://fixture","admin",true);
        assertTrue(session.rememberAdmin("http://fixture","admin"));assertFalse(session.rememberAdmin("http://other","admin"));assertFalse(session.rememberAdmin("http://fixture","other"));
        session.setRememberAdmin("http://fixture","admin",false);assertFalse(session.rememberAdmin("http://fixture","admin"));
    }
    @Test public void regularAccountKeepsEncryptedToken(){
        assertEquals(SessionStore.PERSIST_FULL,SessionStore.persistPolicy(false,false));
    }
    @Test public void privateLoginRemovesOldIdentityAndTokenAndExpiresInBackground()throws Exception{
        disk.put("accounts",SavedAccounts.upsert("[]","private","秘密","old-cipher"));
        disk.put("userId","private");disk.put("name","秘密");disk.put("token","old-cipher");
        session.saveSession("http://fixture","private","秘密","private-secret",SessionStore.PERSIST_NONE);
        assertEquals("private-secret",session.token());assertEquals("private",session.userId());
        assertTrue(session.accounts().isEmpty());assertFalse(disk.containsKey("token"));
        assertFalse(disk.containsKey("userId"));assertFalse(disk.containsKey("name"));
        assertFalse(disk.toString().contains("private-secret"));
        SessionStore.endTemporarySession();assertEquals("",session.token());assertEquals("",session.userId());
    }
    @Test public void adminKeepsNameButCannotActivateWithoutPassword()throws Exception{
        session.saveSession("http://fixture","admin","管理员","admin-secret",SessionStore.PERSIST_ACCOUNT);
        assertEquals("admin-secret",session.token());assertEquals("管理员",session.accounts().get(0).name);
        assertTrue(session.accounts().get(0).needsPassword);assertFalse(disk.containsKey("token"));
        assertFalse(disk.toString().contains("admin-secret"));
        SessionStore.endTemporarySession();assertEquals("",session.token());
        try{session.activate("admin");fail("Password required");}catch(Exception expected){assertTrue(expected.getMessage().contains("密码"));}
    }
    @Test public void switchingAwayDiscardsTemporarySession()throws Exception{
        disk.put("accounts",SavedAccounts.upsert("[]","normal","普通","iv:cipher"));
        session.saveSession("http://fixture","admin","管理员","admin-secret",SessionStore.PERSIST_ACCOUNT);
        session.activate("normal");assertEquals("normal",session.userId());
        assertEquals(SessionStore.PERSIST_FULL,session.persist());
        session.restrictSavedAccount("normal",SessionStore.PERSIST_ACCOUNT);
        assertEquals("",session.token());
    }
    @Test public void migrationCleansNonCurrentAccountsWithoutChangingCurrentUser()throws Exception{
        String accounts=SavedAccounts.upsert("[]","normal","普通","normal-cipher");
        accounts=SavedAccounts.upsert(accounts,"admin","管理员","admin-cipher");
        accounts=SavedAccounts.upsert(accounts,"private","秘密","private-cipher");
        disk.put("accounts",accounts);disk.put("userId","normal");disk.put("token","normal-cipher");
        session.restrictSavedAccount("admin",SessionStore.PERSIST_ACCOUNT);
        session.restrictSavedAccount("private",SessionStore.PERSIST_NONE);
        assertEquals("normal",session.userId());assertEquals("normal-cipher",disk.get("token"));
        assertEquals(2,session.accounts().size());assertTrue(session.accounts().get(1).needsPassword);
        assertFalse(disk.toString().contains("admin-cipher"));assertFalse(disk.toString().contains("private-cipher"));
    }
}
