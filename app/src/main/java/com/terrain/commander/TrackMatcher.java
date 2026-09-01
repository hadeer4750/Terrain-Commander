package com.terrain.commander;

import java.util.*;

/** Filename search: never silently substitutes an unrelated track. */
public final class TrackMatcher {
    public static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\.[a-z0-9]{2,5}$", "")
            .replaceAll("[\\u064B-\\u065F\\u0670\\u0640]", "")
            .replaceAll("[أإآ]", "ا").replace('ى','ي').replace('ة','ه')
            .replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }
    public static int score(String title, String query) {
        String t=normalize(title),q=normalize(query);
        if(q.isEmpty())return 0;
        if(t.equals(q))return 100;
        if(t.contains(q))return 90;
        for(String word:q.split(" "))if(!Arrays.asList(t.split(" ")).contains(word))return 0;
        return 70;
    }
}
