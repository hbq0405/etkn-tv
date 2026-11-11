package xyz.etkn.tv;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DetailChangesTest {
    @Test public void preciseUpdatesCoalesceAndReconnectCatchesOnlyChangedEpisodes()throws Exception{
        DetailChanges changes=new DetailChanges();
        changes.accept(new JSONObject("{Initial:true,Versions:{'item-1':'a','item-2':'b','item-3':'c'}}"));
        assertTrue(changes.updated.isEmpty());assertFalse(changes.structure);
        changes.accept(new JSONObject("{Updated:['item-2'],Versions:{'item-2':'d'}}"));
        changes.accept(new JSONObject("{Updated:['item-2'],Versions:{'item-2':'d'}}"));
        assertEquals(Collections.singleton("item-2"),changes.updated);assertFalse(changes.structure);
        changes.updated.clear();
        changes.accept(new JSONObject("{Initial:true,Versions:{'item-1':'a','item-2':'d','item-3':'e'}}"));
        assertEquals(Collections.singleton("item-3"),changes.updated);assertFalse(changes.structure);
    }
    @Test public void structureChangesAreExplicitAndUnchangedReconnectionDoesNothing()throws Exception{
        DetailChanges changes=new DetailChanges();JSONObject initial=new JSONObject("{Initial:true,Versions:{'item-1':'a'}}");
        changes.accept(initial);changes.accept(initial);assertTrue(changes.updated.isEmpty());assertFalse(changes.structure);
        changes.accept(new JSONObject("{Added:['item-2'],Versions:{'item-2':'b'}}"));assertTrue(changes.structure);
        changes.structure=false;changes.accept(new JSONObject("{Initial:true,Versions:{'item-1':'a'}}"));assertTrue(changes.structure);
    }
}
