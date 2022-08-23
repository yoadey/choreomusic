package de.yoadey.choreomusic.service;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import de.yoadey.choreomusic.model.Track;

@RunWith(MockitoJUnitRunner.class)
public class PlaybackControlTest {

    @InjectMocks
    public PlaybackControl service;

    @Mock
    public ExoPlayer player;

    @Test
    public void testVolumeMain() {
        checkVolume(5000L, 4000L, 0.2f,
                10000L, 4000L, 0.7f,
                7500L, 1.0f);
    }

    @Test
    public void testVolumeFadeIn() {
        checkVolume(5000L, 4000L, 0.2f,
                10000L, 4000L, 0.7f,
                4000L,  0.6f);
    }

    @Test
    public void testVolumeFadeOut() {
        checkVolume(5000L, 4000L, 0.2f,
                10000L, 4000L, 0.7f,
                11000L,  0.85f);
    }

    @Test
    public void testVolumeFadeBetween() {
        checkVolume(5000L, 4000L, 0.2f,
                10000L, 4000L, 0.7f,
                1000L,  0.45f);
    }

    @Test
    public void testVolumeFadeBetweenEarly() {
        checkVolume(5000L, 4000L, 0.2f,
                10000L, 4000L, 0.7f,
                13500L,  0.575f);
    }

    private void checkVolume(long startPosition, long inTime, float inVolume, long endPosition, long outTime, float outVolume, long position, float expectedVolume) {
        service.setLoopStart(new Track(0L,0, startPosition, "", 0));
        service.setLeadInTime(inTime);
        service.setLeadInVolume(inVolume);
        service.setLoopEnd(new Track(1L,0, endPosition, "", 0));
        service.setLeadOutTime(outTime);
        service.setLeadOutVolume(outVolume);

        Mockito.when(player.getCurrentPosition()).thenReturn(position);

        service.checkVolume();

        Mockito.verify(player).setVolume(expectedVolume);
    }
}
