package xyz.etkn.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class LoginGateTest {
    @Test public void unknownStoredModeFallsBackToLastUser(){
        assertEquals(LoginGate.LAST_USER,LoginGate.normalize(-1));
        assertEquals(LoginGate.LAST_USER,LoginGate.normalize(LoginGate.LAST_USER));
        assertEquals(LoginGate.PICK_USER,LoginGate.normalize(LoginGate.PICK_USER));
        assertEquals(LoginGate.LAST_USER,LoginGate.normalize(7));
    }
    @Test public void pickerShowsOnlyWhenEnabledUnsatisfiedAndAccountsExist(){
        assertTrue(LoginGate.needsPicker(LoginGate.PICK_USER,false,2));
        assertTrue(LoginGate.needsPicker(LoginGate.PICK_USER,false,1));
        assertFalse(LoginGate.needsPicker(LoginGate.PICK_USER,true,2));
        assertFalse(LoginGate.needsPicker(LoginGate.PICK_USER,false,0));
        assertFalse(LoginGate.needsPicker(LoginGate.LAST_USER,false,2));
        assertFalse(LoginGate.needsPicker(LoginGate.LAST_USER,true,0));
    }
}
