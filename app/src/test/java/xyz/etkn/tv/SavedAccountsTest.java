package xyz.etkn.tv;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class SavedAccountsTest {
    @Test public void upsertKeepsOneEncryptedEntryPerUserAndMovesItToFront(){
        String accounts=SavedAccounts.upsert("[]","user-1","Alice","iv:cipher-a");
        accounts=SavedAccounts.upsert(accounts,"user-2","Bob","iv:cipher-b");
        accounts=SavedAccounts.upsert(accounts,"user-1","Alice 2","new-iv:new-cipher");
        List<SavedAccounts.Account> users=SavedAccounts.list(accounts);
        assertEquals(2,users.size());assertEquals("user-1",users.get(0).userId);assertEquals("Alice 2",users.get(0).name);
        assertEquals("new-iv:new-cipher",SavedAccounts.find(accounts,"user-1").optString("token"));
    }
    @Test public void removingOneUserPreservesOtherAccounts(){
        String accounts=SavedAccounts.upsert("[]","user-1","Alice","iv:cipher-a");
        accounts=SavedAccounts.upsert(accounts,"user-2","Bob","iv:cipher-b");
        String remaining=SavedAccounts.remove(accounts,"user-2");
        assertNull(SavedAccounts.find(remaining,"user-2"));
        assertEquals("iv:cipher-a",SavedAccounts.find(remaining,"user-1").optString("token"));
        assertEquals("Alice",SavedAccounts.list(remaining).get(0).name);
        assertEquals("user-1",SavedAccounts.list(remaining).get(0).userId);
    }
    @Test public void malformedRegistryDoesNotExposeOrSelectAnAccount(){
        assertTrue(SavedAccounts.list("not-json").isEmpty());assertNull(SavedAccounts.find("not-json","user-1"));
    }
}
