package com.terrain.commander;
import org.junit.Test;
import static org.junit.Assert.*;
public class TrackMatcherTest {
 @Test public void matchesArabicNamesAndRejectsUnrelatedTracks(){
  assertEquals(100,TrackMatcher.score("أغنيــة.MP3","اغنيه"));
  assertEquals(90,TrackMatcher.score("كاظم الساهر - يا طيور.mp3","يا طيور"));
  assertEquals(70,TrackMatcher.score("كاظم الساهر يا طيور.mp3","طيور كاظم"));
  assertEquals(0,TrackMatcher.score("كاظم الساهر يا طيور.mp3","فيروز"));
  assertEquals(0,TrackMatcher.score("song.mp3",""));
 }
}
