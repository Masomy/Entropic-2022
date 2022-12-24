package org.firstinspires.ftc.teamcode.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class PositionTest {

    private static final double E = 1e-6;

    @Test
    public void distanceToLine() {
        assertEquals(0.0, new Position(0, 0).distance(new Position(0, -1), new Position(0, 1)), E);
        assertEquals(1.0, new Position(0, 0).distance(new Position(1, -1), new Position(1, 1)), E);
        assertEquals(1.0, new Position(0, 0).distance(new Position(-1, -1), new Position(-1, 1)), E);
        assertEquals(Math.sqrt(2) / 2, new Position(0, 0).distance(new Position(1, 0), new Position(0, 1)), E);
    }

}
