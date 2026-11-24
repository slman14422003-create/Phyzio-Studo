package com.ast2012a.clinicalmaster;

import java.nio.charset.StandardCharsets;

/**
 * Sec-MS-GEC v2 token for the Edge read-aloud service.
 * Port of generate_sec_ms_gec_v2 from rany2/edge-tts 7.3.1 (LGPL-3.0), checked against its published vectors.
 */
final class EdgeToken {
    private EdgeToken() {
    }

    static final String VERSION = "2-143.0.3650.75";

    private static final long M = 0xffffffffL;
    private static final long[] SHA_INIT = {0x6a09e667L, 0xbb67ae85L, 0x3c6ef372L, 0xa54ff53aL, 0x510e527fL,
            0x9b05688cL, 0x1f83d9abL, 0x5be0cd19L};
    private static final long[] SHA_STATE = {0xfd9fbea2L, 0x91243ce7L, 0xbe499094L, 0xcd2e1e0aL, 0x6c006f49L,
            0x04bd35abL, 0xfeb0a7e4L, 0x03effbd2L};
    private static final long[] SHA_SCHEDULE = {0xeb24bf9eL, 0x1558691fL, 0xa0c09ee2L, 0x0cfa32baL, 0xa622ee8cL,
            0xd1dd9491L, 0xc0dd25a0L, 0xf9e70ab6L, 0xf0c31573L, 0x7058ce4eL, 0x7188e606L, 0x00e5ab33L, 0xd258b060L,
            0x72766564L};
    private static final long[] K = {
            0x428a2f98L, 0x71374491L, 0xb5c0fbcfL, 0xe9b5dba5L, 0x3956c25bL, 0x59f111f1L, 0x923f82a4L, 0xab1c5ed5L,
            0xd807aa98L, 0x12835b01L, 0x243185beL, 0x550c7dc3L, 0x72be5d74L, 0x80deb1feL, 0x9bdc06a7L, 0xc19bf174L,
            0xe49b69c1L, 0xefbe4786L, 0x0fc19dc6L, 0x240ca1ccL, 0x2de92c6fL, 0x4a7484aaL, 0x5cb0a9dcL, 0x76f988daL,
            0x983e5152L, 0xa831c66dL, 0xb00327c8L, 0xbf597fc7L, 0xc6e00bf3L, 0xd5a79147L, 0x06ca6351L, 0x14292967L,
            0x27b70a85L, 0x2e1b2138L, 0x4d2c6dfcL, 0x53380d13L, 0x650a7354L, 0x766a0abbL, 0x81c2c92eL, 0x92722c85L,
            0xa2bfe8a1L, 0xa81a664bL, 0xc24b8b70L, 0xc76c51a3L, 0xd192e819L, 0xd6990624L, 0xf40e3585L, 0x106aa070L,
            0x19a4c116L, 0x1e376c08L, 0x2748774cL, 0x34b0bcb5L, 0x391c0cb3L, 0x4ed8aa4aL, 0x5b9cca4fL, 0x682e6ff3L,
            0x748f82eeL, 0x78a5636fL, 0x84c87814L, 0x8cc70208L, 0x90befffaL, 0xa4506cebL, 0xbef9a3f7L, 0xc67178f2L};
    private static final long SHA_ADJ = 0xc9dc06a7L;
    private static final long SIGMA0_ADJ = 0x059c0b80L;
    private static final long SIGMA1_ADJ = 0x000b92c0L;
    private static final long[][] CS_COEF = {
            {0xe82e91b1L, 0xb32700d0L}, {0x44de24c6L, 0x88dff51dL}, {0x3beb65bcL, 0x29514038L},
            {0xc89c5933L, 0xb328cbdfL}, {0x8d1ec211L, 0xd405b4d5L}, {0x314ef5d5L, 0x26e55f3cL},
            {0xe92491b1L, 0x607801c6L}, {0xf0a5b9d5L, 0x82b2c1fbL}, {0x06064cf3L, 0xfa51a3daL},
            {0xda79fa60L, 0x207cc464L}, {0xbed20560L, 0x3c3c74f3L}, {0x89f9c9f8L, 0xd2d14b61L},
            {0x35fa38c6L, 0xbb04a2c1L}, {0xcb49e3c6L, 0xc5626167L}};
    private static final long CS_FACTOR = 0x82893933L;
    private static final long CS_ADJ0 = 0xf2f90b1aL;
    private static final long CS_ADJ1 = 0x0b1a0000L;

    private static long rol16(long v) {
        v &= M;
        return ((v << 16) | (v >>> 16)) & M;
    }

    private static long rotr(long v, int n) {
        return ((v >>> n) | (v << (32 - n))) & M;
    }

    private static long sigma0(long v) {
        return ((v >>> 7) ^ (v << 25) ^ (v >>> 18) ^ (v << 14) ^ (v >>> 3)) & M;
    }

    private static long sigma1(long v) {
        return ((v >>> 17) ^ (v << 15) ^ (v >>> 19) ^ (v << 13) ^ (v >>> 10)) & M;
    }

    private static long bswap(long v) {
        return ((v & 0xffL) << 24) | ((v & 0xff00L) << 8) | ((v >>> 8) & 0xff00L) | ((v >>> 24) & 0xffL);
    }

    private static long beInt(byte[] b, int from, int to) {
        long r = 0;
        for (int i = from; i < to; i++) r = (r << 8) | (b[i] & 0xffL);
        return r;
    }

    private static long[] rounds(long[] st, long[] w, int first) {
        long a = st[0], b = st[1], c = st[2], d = st[3], e = st[4], f = st[5], g = st[6], h = st[7];
        for (int i = first; i < 64; i++) {
            long k = (first == 14 && i == 14) ? SHA_ADJ : K[i];
            long t1 = h + (rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)) + (g ^ (e & (f ^ g))) + k + w[i];
            long t2 = (rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)) + ((a & b) | (c & (a | b)));
            h = g;
            g = f;
            f = e;
            e = (d + t1) & M;
            d = c;
            c = b;
            b = a;
            a = (t1 + t2) & M;
        }
        return new long[]{a, b, c, d, e, f, g, h};
    }

    private static void expand(long[] w, int first) {
        for (int i = first; i < 64; i++) {
            w[i] = (sigma1(w[i - 2]) + w[i - 7] + sigma0(w[i - 15]) + w[i - 16]) & M;
        }
    }

    private static long[] shaInit(byte[] head) {
        long[] w = new long[64];
        w[14] = beInt(head, 0, 3);
        w[15] = beInt(head, 3, 7);
        for (int i = 16; i < 30; i++) {
            long v = SHA_SCHEDULE[i - 16];
            if (i - 2 >= 14) v += i == 16 ? (sigma1(w[i - 2]) ^ SIGMA1_ADJ) : sigma1(w[i - 2]);
            if (i - 7 >= 14) v += w[i - 7];
            if (i - 15 >= 14) v += i == 29 ? (sigma0(w[i - 15]) ^ SIGMA0_ADJ) : sigma0(w[i - 15]);
            w[i] = v & M;
        }
        w[30] = (sigma1(w[28]) + w[23] + sigma0(w[15]) + w[14] + SHA_ADJ - K[14]) & M;
        expand(w, 31);
        long[] working = rounds(SHA_STATE, w, 14);
        long[] out = new long[8];
        for (int i = 0; i < 8; i++) out[i] = (SHA_INIT[i] + working[i]) & M;
        return out;
    }

    private static long[] shaDigest(byte[] data) {
        long length = 57L + data.length;
        int padLen = (int) ((((55 - length) % 64) + 64) % 64);
        int restLen = data.length - 7;
        byte[] padded = new byte[restLen + 1 + padLen + 8];
        System.arraycopy(data, 7, padded, 0, restLen);
        padded[restLen] = (byte) 0x80;
        long bits = length * 8;
        for (int i = 0; i < 8; i++) padded[padded.length - 1 - i] = (byte) (bits >>> (8 * i));
        long[] state = shaInit(java.util.Arrays.copyOf(data, 7));
        for (int o = 0; o < padded.length; o += 64) {
            long[] w = new long[64];
            for (int j = 0; j < 16; j++) w[j] = beInt(padded, o + j * 4, o + j * 4 + 4);
            expand(w, 16);
            long[] working = rounds(state, w, 0);
            long[] ns = new long[8];
            for (int i = 0; i < 8; i++) ns[i] = (state[i] + working[i]) & M;
            state = ns;
        }
        return state;
    }

    private static long[] wordsLe(byte[] prefixed) {
        int n = (prefixed.length + 3) / 4;
        if (n % 2 != 0) n++;
        long[] w = new long[n];
        for (int i = 0; i < n; i++) {
            long v = 0;
            for (int k = 3; k >= 0; k--) {
                int p = i * 4 + k;
                v = (v << 8) | (p < prefixed.length ? (prefixed[p] & 0xffL) : 0);
            }
            w[i] = v;
        }
        return w;
    }

    private static long[] cs64(byte[] data, long[] state) {
        long keyA = bswap(state[0]) | 1L;
        long keyB = bswap(state[1]) | 1L;
        long[] factors = {(keyA * CS_FACTOR) & M, (keyB * CS_FACTOR) & M};
        byte[] prefixed = new byte[data.length + 1];
        System.arraycopy(data, 0, prefixed, 1, data.length);
        long[] words = wordsLe(prefixed);
        int total = CS_COEF.length + words.length;
        long[] terms = new long[total];
        long[] offsets = new long[total];
        for (int i = 0; i < CS_COEF.length; i++) {
            terms[i] = (CS_COEF[i][0] * factors[i % 2]) & M;
            offsets[i] = CS_COEF[i][1];
        }
        for (int i = 0; i < words.length; i++) {
            long word = words[i];
            long term = word * (i % 2 == 0 ? keyA : keyB);
            long offset = rol16(word) * (i % 2 == 0 ? 0xef0569fbL : 0xc31713dbL);
            if (i == 0) {
                term += CS_ADJ0 * factors[0];
                offset += CS_ADJ1;
            }
            terms[CS_COEF.length + i] = term & M;
            offsets[CS_COEF.length + i] = offset & M;
        }
        long swapAcc = 0, swapLast = 0, st = 0, acc = 0;
        for (int i = 0; i < total; i += 2) {
            long x = terms[i], y = terms[i + 1];
            long t = rol16((offsets[i] + x) & M);
            long q = (t * 0x76f50f34L) & M;
            swapAcc += (rol16(q) * 0xc3efea97L + q) & M;
            t = rol16((offsets[i + 1] + y) & M);
            q = (t * 0x1881ce3cL) & M;
            swapLast = (rol16(q) * 0x35bd1ec9L + q) & M;
            swapAcc = (swapAcc + swapLast) & M;
            st = (st * keyA + x) & M;
            st = (rol16(st) * 0xcf98b111L) & M;
            st = (rol16(st) * 0x87085b9fL) & M;
            st = (rol16(st) * 0x12ceb96dL) & M;
            st = (rol16(st) * 0x257e1d83L) & M;
            acc += st;
            t = (st * keyB + y) & M;
            t = (rol16(t) * 0xa27416f5L) & M;
            t = (rol16(t) * 0xd38396ffL) & M;
            st = (rol16((rol16(t) * 0x7c932b89L) & M) * 0xbfa49f69L) & M;
            acc = (acc + st) & M;
        }
        return new long[]{swapLast ^ st, swapAcc ^ acc};
    }

    private static String hex8(long v) {
        String s = Long.toHexString(v & M).toUpperCase(java.util.Locale.US);
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < 8; i++) sb.append('0');
        return sb.append(s).toString();
    }

    /** Token for the 5-minute window that holds the given unix time (seconds). */
    static String token(String clientToken, long unixSeconds) {
        long ticks = unixSeconds + 11644473600L;
        ticks -= ticks % 300L;
        byte[] data = (java.math.BigInteger.valueOf(ticks).multiply(java.math.BigInteger.valueOf(10000000L))
                .toString() + clientToken).getBytes(StandardCharsets.US_ASCII);
        long[] state = shaDigest(data);
        long[] c = cs64(data, state);
        StringBuilder sb = new StringBuilder();
        sb.append(hex8(bswap(c[0]))).append(hex8(bswap(c[1]))).append('-');
        for (long s : state) sb.append(hex8(s));
        return sb.toString();
    }
}
