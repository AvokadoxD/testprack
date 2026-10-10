package ru.strazh.ac.player;

import org.junit.jupiter.api.Test;
import ru.strazh.ac.player.tracker.RotationTracker;
import ru.strazh.ac.player.tracker.TrackedEntity;
import ru.strazh.ac.util.AABB;

import static org.junit.jupiter.api.Assertions.*;

class TrackerTest {

    @Test
    void possibleBoxCoversUnconfirmedAndInterpolatedPositions() {
        TrackedEntity e = new TrackedEntity(1, null, "player", true, true, 0.6, 1.8, 0, 64, 0, 0, 1);
        e.onConfirmed(1, 10);
        e.moveTo(1, 64, 0, 1, 2);       // клиент мог получить после подтверждения seq 1
        // Подтверждён только seq 1: новая позиция возможна, старая ещё видна (интерполяция).
        AABB box = e.possibleBox(1, 11, 3);
        assertNotNull(box);
        assertEquals(-0.3, box.minX(), 1e-9);
        assertEquals(1.3, box.maxX(), 1e-9);
        // Новая позиция подтверждена давно — старая уже не видна.
        e.onConfirmed(2, 12);
        AABB later = e.possibleBox(2, 20, 3);
        assertEquals(0.7, later.minX(), 1e-9);
    }

    @Test
    void positionNotYetSentToClientIsExcluded() {
        TrackedEntity e = new TrackedEntity(1, null, "player", true, true, 0.6, 1.8, 0, 64, 0, 0, 1);
        e.onConfirmed(1, 5);
        e.moveTo(5, 64, 0, 3, 4); // pre = 3: клиент ещё не подтвердил даже 3
        AABB box = e.possibleBox(1, 20, 3);
        assertEquals(0.3, box.maxX(), 1e-9);
    }

    @Test
    void rotationEchoMatchedInsideWindowAndMissedCounted() {
        RotationTracker r = new RotationTracker();
        r.add(new RotationTracker.Forced(5, 6, 10f, true, 0f, true, true));
        assertNull(r.matchEcho(4), "пинг до поворота ещё не подтверждён");
        assertNotNull(r.matchEcho(5));
        assertNull(r.matchEcho(5), "одно эхо на поворот");
        r.onPong(6, 100);
        assertEquals(0, r.missedEchoes);

        r.add(new RotationTracker.Forced(7, 8, 3f, true, 0f, true, true));
        r.onPong(8, 101);
        assertEquals(1, r.missedEchoes);
        assertTrue(r.possiblyApplied(8, 102), "без эха ближайший тик помечен как навязанный");
        assertFalse(r.possiblyApplied(8, 103));
    }
}
