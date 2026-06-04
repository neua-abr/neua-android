# NeUA Demo Player

**Uncertainty-Aware Neural Network Adaptive Bitrate Streaming**
Android demo implementation using [androidx.media3](https://github.com/androidx/media).

---

## Overview

This repository provides a reference Android implementation of the **NeUA** ABR algorithm described in:

> Y.-m. Kang et al., "Uncertainty-Aware Neural Network based Adaptive Bitrate
> Streaming over LTE and 5G NR," *JNL*, 2026.

NeUA integrates a BiLSTM throughput predictor with Monte Carlo Dropout-based
uncertainty estimation into a dynamic safety factor, enabling more conservative
bitrate selection during high-uncertainty periods such as LTE handovers and 5G
beam-blockage events.

This repository is made available as a companion implementation to the above
paper. A full validation with live network MOS evaluation is planned as future
work.

---

## Repository Structure

```
app/src/main/java/com/example/neua/
  NeUATrackSelection.java        # Core ABR logic (uncertainty-aware selection)
  NeUATrackSelectionFactory.java # media3 ExoTrackSelection.Factory integration
  MainActivity.kt                # Demo player activity
```

---

## Algorithm

The implementation follows Section 4 of the paper:

1. Maintain a sliding window of observed throughput samples (`w = 8`).
2. Estimate mean throughput `mu` and normalised uncertainty `sigma`
   (coefficient of variation; full deployment uses TFLite BiLSTM inference).
3. Compute dynamic safety factor:
   ```
   alpha_dyn = alpha_min + (alpha_max - alpha_min) * exp(-k_sigma * sigma)
   ```
   where `alpha_min = 0.55`, `alpha_max = 0.90`, `k_sigma = 8.0`.
4. Calibrated estimate: `C_hat = alpha_dyn * mu`.
5. Select the highest bitrate tier satisfying `r <= C_hat`.
6. Apply counter-based hysteresis (`M = 3`) to suppress oscillation.

### Parameters

| Parameter | Value | Description |
|---|---|---|
| `w` | 8 | Throughput history window size |
| `alpha_min` | 0.55 | Minimum safety factor (high uncertainty) |
| `alpha_max` | 0.90 | Maximum safety factor (low uncertainty) |
| `k_sigma` | 8.0 | Uncertainty sensitivity coefficient |
| `M` | 3 | Hysteresis threshold |

---

## Integration

Replace the default `AdaptiveTrackSelection.Factory` in your media3 player:

```kotlin
val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()

val trackSelector = DefaultTrackSelector(
    context,
    NeUATrackSelectionFactory(bandwidthMeter)
)

val player = ExoPlayer.Builder(context)
    .setTrackSelector(trackSelector)
    .setBandwidthMeter(bandwidthMeter)
    .build()
```

---

## Full BiLSTM Inference (Future Work)

The current implementation uses coefficient of variation as a lightweight
proxy for MC Dropout uncertainty. To enable full BiLSTM inference:

1. Add the trained model to `app/src/main/assets/neua_bilstm_int8.tflite`.
2. Add the TFLite dependency to `build.gradle`:
   ```
   implementation 'org.tensorflow:tensorflow-lite:2.14.0'
   ```
3. Replace the statistical proxy in `NeUATrackSelection.java`
   with a TFLite interpreter call.

The INT8-quantized BiLSTM model (156 KB) achieves 0.8 ms inference latency
on a Snapdragon 778G SoC, as reported in Table 5 of the paper.

---

## Requirements

- Android minSdk 21 (Android 5.0+)
- androidx.media3 1.5.0
- Kotlin 1.9+

---

## License

Apache License 2.0
