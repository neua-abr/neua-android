package com.example.neua;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.TrackGroup;
import androidx.media3.exoplayer.source.chunk.MediaChunk;
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator;
import androidx.media3.exoplayer.trackselection.BaseTrackSelection;
import androidx.media3.exoplayer.upstream.BandwidthMeter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * NeUA: Uncertainty-Aware Neural Network Adaptive Bitrate Selection
 *
 * Implements the NeUA ABR algorithm for androidx.media3, corresponding to
 * the scheme described in:
 *   "Uncertainty-Aware Neural Network based Adaptive Bitrate Streaming
 *    over LTE and 5G NR", MDPI Electronics, 2025.
 *
 * Algorithm overview:
 *   1. Maintain a sliding window of observed throughput samples (w = 8).
 *   2. Estimate throughput mean (mu) and normalised uncertainty (sigma, CoV)
 *      from the window. This is a lightweight proxy for MC Dropout uncertainty;
 *      full BiLSTM inference can be added via TFLite (see README).
 *   3. Compute dynamic safety factor:
 *        alpha_dyn = alpha_min + (alpha_max - alpha_min) * exp(-k_sigma * sigma)
 *   4. Calibrated estimate: C_hat = alpha_dyn * mu  [Mbps]
 *   5. Select the highest bitrate tier r such that r <= C_hat.
 *   6. Apply counter-based hysteresis (M = 3) to suppress short-lived switches.
 */
public final class NeUATrackSelection extends BaseTrackSelection {

    // ── Algorithm parameters (Section 5.1 of the paper) ─────────────────────
    private static final int   WINDOW_SIZE = 8;      // w
    private static final float ALPHA_MIN   = 0.55f;  // alpha_min
    private static final float ALPHA_MAX   = 0.90f;  // alpha_max
    private static final float K_SIGMA     = 8.0f;   // lambda_sigma
    private static final int   M_HYST      = 3;      // hysteresis threshold M

    // ── State ─────────────────────────────────────────────────────────────────
    private final BandwidthMeter bandwidthMeter;
    private final Deque<Float>   throughputWindow;

    private int selectedIndex;   // current bitrate index (0 = highest quality)
    private int pendingIndex;    // up-switch candidate under hysteresis
    private int upCounter;       // consecutive up-recommendations

    // ── Constructor ───────────────────────────────────────────────────────────
    public NeUATrackSelection(TrackGroup group,
                              int[] tracks,
                              BandwidthMeter bandwidthMeter) {
        super(group, tracks);
        this.bandwidthMeter   = bandwidthMeter;
        this.throughputWindow = new ArrayDeque<>(WINDOW_SIZE + 1);
        this.selectedIndex    = tracks.length - 1;  // start conservative (lowest)
        this.pendingIndex     = selectedIndex;
        this.upCounter        = 0;
    }

    // ── BaseTrackSelection overrides ──────────────────────────────────────────

    @Override
    public int getSelectedIndex() {
        return selectedIndex;
    }

    @Override
    public int getSelectionReason() {
        return C.SELECTION_REASON_ADAPTIVE;
    }

    @Override
    @Nullable
    public Object getSelectionData() {
        return null;
    }

    @Override
    public void updateSelectedTrack(
            long playbackPositionUs,
            long bufferedDurationUs,
            long availableDurationUs,
            List<? extends MediaChunk> queue,
            MediaChunkIterator[] mediaChunkIterators) {

        // 1. Record throughput sample (bps → Mbps)
        long bps = bandwidthMeter.getBitrateEstimate();
        if (bps != BandwidthMeter.NO_ESTIMATE && bps > 0) {
            float mbps = bps / 1_000_000f;
            throughputWindow.addLast(mbps);
            if (throughputWindow.size() > WINDOW_SIZE) {
                throughputWindow.removeFirst();
            }
        }

        if (throughputWindow.size() < 2) {
            return;  // insufficient history
        }

        // 2. Compute mean and normalised CoV (proxy for MC Dropout sigma)
        float[] h = new float[throughputWindow.size()];
        int i = 0;
        for (float v : throughputWindow) h[i++] = v;

        float mu    = mean(h);
        float sigma = std(h) / (mu + 1e-6f);   // normalised uncertainty

        // 3. Dynamic safety factor: high sigma → alpha_dyn → alpha_min
        float alphaDyn = ALPHA_MIN
                + (ALPHA_MAX - ALPHA_MIN) * (float) Math.exp(-K_SIGMA * sigma);

        // 4. Calibrated throughput estimate [Mbps]
        float cHat = alphaDyn * mu;

        // 5. Highest feasible bitrate (tracks[0] = highest, tracks[n-1] = lowest)
        int candidate = tracks.length - 1;
        for (int j = 0; j < tracks.length; j++) {
            // getFormat(j).bitrate is in bps (int); convert to Mbps as float
            float trackMbps = getFormat(j).bitrate / 1_000_000f;
            if (trackMbps <= cHat) {
                candidate = j;
                break;
            }
        }

        // 6. Counter-based hysteresis (Section 4.2.3 of the paper)
        if (candidate < selectedIndex) {
            // Up-switch recommendation
            if (candidate != pendingIndex) {
                pendingIndex = candidate;
                upCounter    = 1;
            } else {
                upCounter++;
            }
            if (upCounter >= M_HYST) {
                selectedIndex = pendingIndex;
                upCounter     = 0;
            }
        } else if (candidate > selectedIndex) {
            // Down-switch: apply immediately (conservative)
            selectedIndex = candidate;
            pendingIndex  = candidate;
            upCounter     = 0;
        }
        // else: no change
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static float mean(float[] x) {
        float s = 0;
        for (float v : x) s += v;
        return s / x.length;
    }

    private static float std(float[] x) {
        float m = mean(x);
        float s = 0;
        for (float v : x) s += (v - m) * (v - m);
        return (float) Math.sqrt(s / x.length);
    }
}
