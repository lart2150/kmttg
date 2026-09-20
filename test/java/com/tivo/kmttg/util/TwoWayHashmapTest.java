package com.tivo.kmttg.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

// Two Hashtables kept back to back, used for lookups that have to work either
// way round (channel number to name, and back). Nothing is ever removed, so
// rebinding leaves the old reverse entry standing - that is pinned below
// because callers that reuse a key depend on which way the stale entry points.
public class TwoWayHashmapTest {

   @Test
   void addMakesTheValueReachableFromEitherSide() {
      TwoWayHashmap<String,Integer> map = new TwoWayHashmap<String,Integer>();
      map.add("Bolt", 1);
      map.add("Roamio", 2);
      assertEquals(Integer.valueOf(1), map.getV("Bolt"));
      assertEquals(Integer.valueOf(2), map.getV("Roamio"));
      assertEquals("Bolt", map.getK(1));
      assertEquals("Roamio", map.getK(2));
   }

   @Test
   void missingEntriesReturnNullInBothDirections() {
      TwoWayHashmap<String,Integer> map = new TwoWayHashmap<String,Integer>();
      map.add("Bolt", 1);
      assertNull(map.getV("Premiere"));
      assertNull(map.getK(99));
   }

   @Test
   void rebindingAKeyLeavesTheOldReverseEntryBehind() {
      TwoWayHashmap<String,Integer> map = new TwoWayHashmap<String,Integer>();
      map.add("Bolt", 1);
      map.add("Bolt", 2);
      assertEquals(Integer.valueOf(2), map.getV("Bolt"));
      assertEquals("Bolt", map.getK(2));
      // The forward side moved on but the reverse side still answers for 1
      assertEquals("Bolt", map.getK(1));
   }

   @Test
   void reusingAValueRepointsTheReverseLookupOnly() {
      TwoWayHashmap<String,Integer> map = new TwoWayHashmap<String,Integer>();
      map.add("Bolt", 1);
      map.add("Roamio", 1);
      assertEquals("Roamio", map.getK(1));
      // Both keys still map forward to the shared value
      assertEquals(Integer.valueOf(1), map.getV("Bolt"));
      assertEquals(Integer.valueOf(1), map.getV("Roamio"));
   }

   @Test
   void nullsAreRejectedByTheBackingHashtables() {
      TwoWayHashmap<String,Integer> map = new TwoWayHashmap<String,Integer>();
      assertThrows(NullPointerException.class, () -> map.add(null, 1));
      assertThrows(NullPointerException.class, () -> map.add("Bolt", null));
      assertThrows(NullPointerException.class, () -> map.getV(null));
      assertThrows(NullPointerException.class, () -> map.getK(null));
   }
}
