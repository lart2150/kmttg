package com.tivo.kmttg.mux;

import java.io.ByteArrayOutputStream;

// Synthetic elementary stream bytes for the sink tests. The parsers read real headers bit by
// bit, so a fixture has to be a real header: these are the same constructions MuxCodecTest
// and H264ParserTest already build, gathered where a whole recording can be assembled out of
// them.
class MuxStreams {
   // stream_type bytes as a PMT states them
   static final int MPEG2   = 0x02;
   static final int H264    = 0x1B;
   static final int AC3     = 0x81;
   static final int PRIVATE = 0x97;      // TiVo's private data stream

   // Real pids, from the reference recordings: video, audio and private data.
   static final int VIDEO_PID   = 0x31;
   static final int AUDIO_PID   = 0x34;
   static final int PRIVATE_PID = 0x37;

   // sequence_header_code, then width (12b), height (12b), aspect, frame_rate_code 4
   // (29.97), bit_rate_value all ones - which means the stream declines to state one.
   static byte[] sequenceHeader(int width, int height, int aspectCode) {
      return new byte[]{
         0, 0, 1, (byte)0xB3,
         (byte)(width >> 4),
         (byte)(((width & 0xF) << 4) | ((height >> 8) & 0xF)),
         (byte)(height & 0xFF),
         (byte)((aspectCode << 4) | 4),
         (byte)0xFF, (byte)0xFF, (byte)0xF0, 0
      };
   }

   // temporal_reference 0 (10b) then picture_coding_type (3b), left aligned in byte 1
   static byte[] picture(int codingType) {
      return new byte[]{0, 0, 1, 0x00, 0x00, (byte)(codingType << 3), 0, 0};
   }

   // The unit a recording opens with: a sequence header ahead of an I picture, which is the
   // only shape that lets a track describe itself.
   static byte[] mpeg2Key(int width, int height, int aspectCode) {
      return concat(sequenceHeader(width, height, aspectCode), picture(1));
   }

   static byte[] mpeg2Inter() {
      return picture(2);
   }

   // syncword, crc1, then fscod=0 (48 kHz), frmsizecod, bsid=8, bsmod=0, acmod, lfe.
   static byte[] ac3Frame(int frmsizecod, int acmod, boolean lfe, int length) {
      byte[] f = new byte[length];
      f[0] = 0x0B; f[1] = 0x77;
      f[4] = (byte)(frmsizecod & 0x3F);                       // fscod=0, frmsizecod
      f[5] = (byte)(8 << 3);                                  // bsid 8, bsmod 0
      // lfeon does not sit at a fixed bit: what precedes it depends on acmod, so it is built
      // positionally rather than assumed.
      int bits = acmod, n = 3;
      if ((acmod & 0x1) != 0 && acmod != 0x1) { bits <<= 2; n += 2; }   // cmixlev
      if ((acmod & 0x4) != 0)                 { bits <<= 2; n += 2; }   // surmixlev
      if (acmod == 0x2)                       { bits <<= 2; n += 2; }   // dsurmod
      bits = (bits << 1) | (lfe ? 1 : 0); n += 1;
      f[6] = (byte)(bits << (8 - n));
      return f;
   }

   // 384 kb/s stereo at 48 kHz, which is what every reference recording carries.
   static byte[] ac3Stereo() {
      return ac3Frame(28, 2, false, 1536);
   }

   static byte[] ac3FiveOne() {
      return ac3Frame(28, 7, true, 1536);
   }

   // One payload unit holding several syncframes under a single PTS - the ordinary shape of
   // AC-3 in a transport stream, not a corner case.
   static byte[] ac3Unit(int frames) {
      byte[][] parts = new byte[frames][];
      for (int i = 0; i < frames; i++) parts[i] = ac3Stereo();
      return concat(parts);
   }

   // Real parameter sets from Cradle 2 the Grave: 720x480 Main at SAR 40:33, NTSC 16:9.
   static final byte[] SPS_720x480 = {
      (byte)0x67, (byte)0x4D, (byte)0x40, (byte)0x1E, (byte)0x9A, (byte)0x52, (byte)0x81,
      (byte)0x68, (byte)0xF2, (byte)0xC1, (byte)0x54, (byte)0x83, (byte)0x03, (byte)0x03,
      (byte)0x20, (byte)0x00, (byte)0x00, (byte)0x7D, (byte)0x20, (byte)0x00, (byte)0x1D,
      (byte)0x4C, (byte)0x12, (byte)0x80
   };
   static final byte[] PPS       = {0x68, (byte)0xEE, 0x3C, (byte)0x80};
   static final byte[] AUD       = {0x09, 0x10};
   // slice_type ue=7, I for all slices; ue=5 is P. Broadcast H.264 signals random access
   // with an I slice rather than an IDR.
   static final byte[] I_SLICE   = {0x41, (byte)0x88};
   static final byte[] P_SLICE   = {0x41, (byte)0x98};
   // Too short to parse: the shape a parameter set has when the unit carrying it was cut.
   static final byte[] BROKEN_SPS = {0x67, 0x01, 0x02};

   // Annex-B: every NAL behind a start code, which is how a transport stream carries them.
   static byte[] accessUnit(byte[]... nals) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      for (byte[] nal : nals) {
         out.write(0); out.write(0); out.write(1);
         out.write(nal, 0, nal.length);
      }
      return out.toByteArray();
   }

   // Bytes no parser can make a header out of: no start code, no syncword. What a stream
   // looks like to a muxer when it is the wrong codec, or scrambled.
   static byte[] undescribable(int length) {
      byte[] b = new byte[length];
      for (int i = 0; i < length; i++) b[i] = 0x55;
      return b;
   }

   // ISO 639 language descriptor, tag 0x0A: the three letter code and an audio type byte.
   static byte[] languageDescriptor(String code) {
      byte[] d = new byte[6];
      d[0] = 0x0A;
      d[1] = 4;
      for (int i = 0; i < 3; i++) d[2+i] = (byte)code.charAt(i);
      return d;
   }

   static byte[] concat(byte[]... parts) {
      int n = 0;
      for (byte[] p : parts) n += p.length;
      byte[] out = new byte[n];
      int at = 0;
      for (byte[] p : parts) {
         System.arraycopy(p, 0, out, at, p.length);
         at += p.length;
      }
      return out;
   }
}
