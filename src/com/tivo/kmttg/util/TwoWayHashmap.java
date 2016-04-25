/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 *
 * This file is part of kmttg package.
 *
 * kmttg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this project.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tivo.kmttg.util;

import java.util.Hashtable;
import java.util.Map;

public class TwoWayHashmap<K extends Object, V extends Object> {
   private Map<K,V> forward = new Hashtable<K, V>();
   private Map<V,K> backward = new Hashtable<V, K>();

   public synchronized void add(K key, V value) {
      forward.put(key, value);
      backward.put(value, key);
   }

   public synchronized V getV(K key) {
      return forward.get(key);
   }

   public synchronized K getK(V key) {
      return backward.get(key);
   }
}
