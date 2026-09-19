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
package com.tivo.kmttg.mux.mkv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

// EBML primitives for the Matroska writer. Master elements are built in memory and written
// with a known size rather than streamed with an unknown one: a cluster is a few MB at the
// sizes here, and a known size is what lets a player seek without scanning.
public class EbmlWriter {
   private final ByteArrayOutputStream out = new ByteArrayOutputStream();

   public byte[] toByteArray() { return out.toByteArray(); }
   public int size()           { return out.size(); }
   public void reset()         { out.reset(); }

   // Bytes an element id occupies. Public because a caller reserving space for a value it
   // will patch later has to predict the layout exactly; two copies of this rule that drift
   // apart write the patch into the middle of the file.
   public static int idLength(long id) {
      return id > 0xFFFFFFL ? 4 : id > 0xFFFFL ? 3 : id > 0xFFL ? 2 : 1;
   }

   // Bytes writeSize will use for this value. Same reason as idLength above.
   public static int sizeLength(long v) {
      int len = 1;
      while (len < 8 && v >= (1L << (7 * len)) - 1) len++;
      return len;
   }

   // Element IDs already carry their own length marker, so they go out verbatim.
   public void writeId(long id) {
      int len = idLength(id);
      for (int i = len - 1; i >= 0; i--) {
         out.write((int)(id >> (i * 8)) & 0xFF);
      }
   }

   // EBML variable length integer: a leading marker bit selects the width.
   public void writeSize(long v) {
      int len = sizeLength(v);
      long marker = 1L << (7 * len);
      long val = v | marker;
      for (int i = len - 1; i >= 0; i--) {
         out.write((int)(val >> (i * 8)) & 0xFF);
      }
   }

   // Same value in a fixed width, so it can be patched once the real size is known.
   public void writeSizeFixed(long v, int len) {
      long val = v | (1L << (7 * len));
      for (int i = len - 1; i >= 0; i--) {
         out.write((int)(val >> (i * 8)) & 0xFF);
      }
   }

   public static byte[] sizeFixedBytes(long v, int len) {
      byte[] b = new byte[len];
      long val = v | (1L << (7 * len));
      for (int i = 0; i < len; i++) {
         b[i] = (byte)(val >> ((len - 1 - i) * 8));
      }
      return b;
   }

   public void writeUInt(long id, long value) {
      int len = 1;
      while (len < 8 && (value >>> (len * 8)) != 0) len++;
      writeId(id);
      writeSize(len);
      for (int i = len - 1; i >= 0; i--) {
         out.write((int)(value >> (i * 8)) & 0xFF);
      }
   }

   public void writeString(long id, String s) {
      byte[] b;
      try {
         b = s.getBytes("UTF-8");
      } catch (java.io.UnsupportedEncodingException e) {
         b = s.getBytes();   // UTF-8 is always present; this is unreachable
      }
      writeId(id);
      writeSize(b.length);
      out.write(b, 0, b.length);
   }

   public void writeFloat64(long id, double value) {
      writeId(id);
      writeSize(8);
      long bits = Double.doubleToLongBits(value);
      for (int i = 7; i >= 0; i--) {
         out.write((int)(bits >> (i * 8)) & 0xFF);
      }
   }

   public void writeBinary(long id, byte[] data) {
      writeId(id);
      writeSize(data.length);
      out.write(data, 0, data.length);
   }

   // A nested master element whose children were built separately.
   public void writeMaster(long id, byte[] children) {
      writeId(id);
      writeSize(children.length);
      out.write(children, 0, children.length);
   }

   public void writeRaw(byte[] b) {
      out.write(b, 0, b.length);
   }

   public void writeByte(int b) {
      out.write(b & 0xFF);
   }

   public void writeTo(java.io.RandomAccessFile f) throws IOException {
      byte[] b = out.toByteArray();
      f.write(b);
      out.reset();
   }
}
