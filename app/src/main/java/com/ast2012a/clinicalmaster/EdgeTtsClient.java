package com.ast2012a.clinicalmaster;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SSLException;

final class EdgeTtsClient {

    private EdgeTtsClient() {
    }

    static void warmUp() {
        GoogleTtsClient.warmUp();
    }

    static final class Result {
        final byte[] audio;
        final int[] wordMs;
        final int[] wordChar;
        final int durationMs;

        Result(byte[] audio, int[] wordMs, int[] wordChar) {
            this(audio, wordMs, wordChar, 0);
        }

        Result(byte[] audio, int[] wordMs, int[] wordChar, int durationMs) {
            this.audio = audio;
            this.wordMs = wordMs;
            this.wordChar = wordChar;
            this.durationMs = durationMs;
        }
    }

    static final class Style {
        static final Style DEFAULT = new Style(0, 0, 0, 0);
        final int arRatePct;
        final int pitchHz;
        final int sentencePauseMs;
        final int commaPauseMs;

        Style(int arRatePct, int pitchHz, int sentencePauseMs, int commaPauseMs) {
            this.arRatePct = arRatePct;
            this.pitchHz = pitchHz;
            this.sentencePauseMs = sentencePauseMs;
            this.commaPauseMs = commaPauseMs;
        }
    }

    static final class Run {
        final int start;
        final int end;
        final String voice;

        Run(int start, int end, String voice) {
            this.start = start;
            this.end = end;
            this.voice = voice;
        }
    }

    static String sanitize(String s) {
        char[] a = s.toCharArray();
        for (int i = 0; i < a.length; i++) {
            char c = a[i];
            if (Character.isHighSurrogate(c) && i + 1 < a.length && Character.isLowSurrogate(a[i + 1])) {
                i++;
                continue;
            }
            if (c < 0x20 || (c >= 0x7F && c <= 0x9F) || Character.isSurrogate(c) || (c >= 0xE000 && c <= 0xF8FF)
                    || (c >= 0xFDD0 && c <= 0xFDEF) || c >= 0xFFF0 || c == '\u2028' || c == '\u2029') {
                a[i] = ' ';
            }
        }
        return new String(a);
    }

    static volatile String lastError = "";

    static final class ServiceException extends IOException {
        final int httpCode;

        ServiceException(String msg, int httpCode) {
            super(msg);
            this.httpCode = httpCode;
        }
    }

    static boolean isNetworkFailure(Throwable t) {
        while (t != null) {
            if (t instanceof UnknownHostException || t instanceof ConnectException
                    || t instanceof SocketTimeoutException || t instanceof SSLException
                    || t instanceof SocketException) {
                return true;
            }
            String m = t.getMessage();
            if (m != null) {
                String l = m.toLowerCase(Locale.ROOT);
                if (l.contains("timeout") || l.contains("unable to resolve") || l.contains("failed to connect")
                        || l.contains("network is unreachable")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    static Result synthesize(String text, String voice) throws IOException {
        return synthesize(text, voice, Style.DEFAULT);
    }

    static Result synthesize(String text, String voice, Style style) throws IOException {
        return GoogleTtsClient.synthesize(text, voice, style);
    }

    static Result synthesizeRuns(String text, List<Run> runs, String baseVoice, Style style) throws IOException {
        return GoogleTtsClient.synthesizeRuns(text, runs, baseVoice, style);
    }
}
