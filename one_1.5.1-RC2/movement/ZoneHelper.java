/*
 * Copyright 2015, 2016 Barun Saha (http://barunsaha.me)
 *
 * Distributed under the original license of the ONE simulator.
 */
package movement;

import core.Coord;
import java.util.ArrayList;

/**
 * Helper class to aid with various zone related calculations.
 * 
 * Solution to Exercise 4.9, Chapter 4, #TheOMNBook.
 * 
 * @author barun
 */
public class ZoneHelper {

    // Width (along x-axis) of the world
    private static int worldWidth = 5000;
    private static int worldHeight = 5000;
    private static int zoneWidth = 10;
    private static int zoneHeight = 10;
    private static int xZoneMax = worldWidth / zoneWidth;
    private static int yZoneMax = worldHeight / zoneHeight;

    private ZoneHelper() {
    }

    public static void init(int wLen, int wHt, int zLen, int zHt) {
        worldWidth = wLen;
        worldHeight = wHt;
        zoneWidth = zLen;
        zoneHeight = zHt;
        xZoneMax = worldWidth / zoneWidth;
        yZoneMax = worldHeight / zoneHeight;
    }

    public static int getNZones() {
        return (worldWidth / zoneWidth) * (worldHeight / zoneHeight);
    }

    /**
     * Get the zone within which a point is located
     */
    public static int[] getZoneID(Coord point) {
        int[] id = {0, 0};
        int ix = (int) (Math.floor(point.getX() / zoneWidth));
        int iy = (int) (Math.floor(point.getY() / zoneHeight));
        id[0] = ix;
        id[1] = iy;

        return id;
    }

    public static boolean isPointInZone(Coord point, int[] zone) {
        boolean status = false;
        double x = point.getX();
        double y = point.getY();
        int ix = zone[0];
        int iy = zone[1];

        if ((x >= ix * zoneWidth) && (x < (ix + 1) * zoneWidth) 
                && (y >= iy * zoneHeight) && (y < (iy + 1) * zoneHeight)) {
            status = true;
        }

        return status;
    }

    public static ArrayList<int[]> getLeftNeighbouringZones(int[] zone, 
            int nearness) {
        ArrayList<int[]> neighbours = new ArrayList<int[]>();
        int xid = zone[0];
        int yid = zone[1];

        for (int li = 1; li <= nearness && li >= 0; li++) {
            int[] leftZone = {xid - li, yid};
            neighbours.add(leftZone);
        }

        return neighbours;
    }

    public static ArrayList<int[]> getNeighbouringZones(int[] zone, 
            int nearness) {
        ArrayList<int[]> neighbours = new ArrayList<int[]>();
        int xid = zone[0];
        int yid = zone[1];

        int[] zid1 = {xid - 1, yid - 1};
        neighbours.add(zid1);
        int[] zid2 = {xid - 1, yid};
        neighbours.add(zid2);
        int[] zid3 = {xid - 1, yid + 1};
        neighbours.add(zid3);
        int[] zid4 = {xid, yid + 1};
        neighbours.add(zid4);
        int[] zid5 = {xid + 1, yid + 1};
        neighbours.add(zid5);
        int[] zid6 = {xid + 1, yid};
        neighbours.add(zid6);
        int[] zid7 = {xid + 1, yid - 1};
        neighbours.add(zid7);
        int[] zid8 = {xid, yid - 1};
        neighbours.add(zid8);

        ArrayList<int[]> neighbours_filtered = new ArrayList<int[]>();

        if (nearness > 0) {
            for (int[] id : neighbours) {
                int[] zid = {id[0], id[1]};

                if (zid[0] >= 0 && zid[0] < xZoneMax && zid[1] >= 0 
                        && zid[1] < yZoneMax) {
                    neighbours_filtered.add(zid);
                }
            }
        }

        return neighbours_filtered;
    }

    public static Coord getZoneCenter(int[] zone) {
        Coord c;
        double xLeftBottom;
        double yLeftBottom;
        double xTopRight;
        double yTopRight;

        xLeftBottom = zone[0] * zoneWidth;
        yLeftBottom = zone[1] * zoneHeight;
        xTopRight = (zone[0] + 1) * zoneWidth;
        yTopRight = (zone[1] + 1) * zoneHeight;

        double xx = (xTopRight + xLeftBottom) / 2;
        double yy = (yTopRight + yLeftBottom) / 2;

        c = new Coord(xx, yy);

        return c;
    }
}