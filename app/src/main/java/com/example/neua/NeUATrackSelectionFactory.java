package com.example.neua;

import androidx.media3.common.TrackGroup;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.trackselection.FixedTrackSelection;
import androidx.media3.exoplayer.upstream.BandwidthMeter;

/**
 * Factory that produces {@link NeUATrackSelection} instances for use with
 * {@link androidx.media3.exoplayer.trackselection.DefaultTrackSelector}.
 *
 * <pre>{@code
 *   BandwidthMeter bwMeter = new DefaultBandwidthMeter.Builder(context).build();
 *   DefaultTrackSelector selector = new DefaultTrackSelector(
 *       context,
 *       new NeUATrackSelectionFactory(bwMeter)
 *   );
 *   ExoPlayer player = new ExoPlayer.Builder(context)
 *       .setTrackSelector(selector)
 *       .setBandwidthMeter(bwMeter)
 *       .build();
 * }</pre>
 */
public final class NeUATrackSelectionFactory implements ExoTrackSelection.Factory {

    private final BandwidthMeter bandwidthMeter;

    public NeUATrackSelectionFactory(BandwidthMeter bandwidthMeter) {
        this.bandwidthMeter = bandwidthMeter;
    }

    @Override
    public ExoTrackSelection[] createTrackSelections(
            ExoTrackSelection.Definition[] definitions,
            BandwidthMeter bandwidthMeter) {

        ExoTrackSelection[] selections = new ExoTrackSelection[definitions.length];
        for (int i = 0; i < definitions.length; i++) {
            ExoTrackSelection.Definition def = definitions[i];
            if (def == null) {
                selections[i] = null;
            } else if (def.tracks.length > 1) {
                // Multiple quality tracks: use NeUA adaptive selection
                selections[i] = new NeUATrackSelection(
                        def.group, def.tracks, this.bandwidthMeter);
            } else {
                // Single track: fixed selection (audio, subtitles, etc.)
                selections[i] = new FixedTrackSelection(
                        def.group, def.tracks[0]);
            }
        }
        return selections;
    }
}
