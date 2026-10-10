package ru.strazh.ac.player;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionManagerTest {

    private static PlayerData data() {
        return PlayerData.forTest(d -> List.of());
    }

    @Test
    void confirmsInOrderAndRunsTasks() {
        PlayerData d = data();
        TransactionManager tx = d.transactions();
        int a = tx.createIdForTest();
        int b = tx.createIdForTest();
        int c = tx.createIdForTest();
        assertTrue(tx.isOurs(a));
        assertFalse(tx.isOurs(12345));
        tx.onPingWritten(a);
        long postAfterA = tx.nextPostSeq();
        List<String> ran = new ArrayList<>();
        tx.runOnConfirm(postAfterA, () -> ran.add("b"));
        tx.onPingWritten(b);
        tx.onPingWritten(c);
        assertEquals(3, tx.lastSent());
        assertTrue(tx.onPong(a));
        assertEquals(1, tx.lastConfirmed());
        assertTrue(ran.isEmpty());
        assertTrue(tx.onPong(b));
        assertEquals(List.of("b"), ran);
        assertEquals(0, tx.invalidPongs());
        assertFalse(tx.isOurs(a), "подтверждённый id больше не наш");
    }

    @Test
    void skippedPongIsInvalid() {
        PlayerData d = data();
        TransactionManager tx = d.transactions();
        int a = tx.createIdForTest();
        int b = tx.createIdForTest();
        tx.onPingWritten(a);
        tx.onPingWritten(b);
        assertTrue(tx.onPong(b)); // пропустил a
        assertEquals(1, tx.invalidPongs());
        assertEquals(2, tx.lastConfirmed());
        assertFalse(tx.onPong(a), "старый id уже удалён");
    }

    @Test
    void unknownPongIsNotOurs() {
        PlayerData d = data();
        assertFalse(d.transactions().onPong(42));
    }

    @Test
    void idsAreRandomAndOutsideVanillaRange() {
        PlayerData d = data();
        TransactionManager tx = d.transactions();
        int prev = tx.createIdForTest();
        int sequential = 0;
        for (int i = 0; i < 200; i++) {
            int id = tx.createIdForTest();
            assertTrue(id <= -32768 || id >= 32768);
            if (Math.abs((long) id - prev) == 1) sequential++;
            prev = id;
        }
        assertTrue(sequential < 3);
    }

    @Test
    void runOnConfirmImmediateWhenAlreadyConfirmed() {
        PlayerData d = data();
        TransactionManager tx = d.transactions();
        int a = tx.createIdForTest();
        tx.onPingWritten(a);
        tx.onPong(a);
        boolean[] ran = {false};
        tx.runOnConfirm(1, () -> ran[0] = true);
        assertTrue(ran[0]);
    }
}
