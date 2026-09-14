# Fiji intensity selection tools

Compiled ImageJ plugin providing **Selection Brush Tool** (B icon) and **Intensity Smart Brush Tool** (W icon). Native area ROIs preserve holes and disconnected regions. Source pixels are never written.

The intensity-aware behavior is based on QuPath's Wand tool, particularly its local smoothing, seed-connected flood fill, and intensity-similarity approach. This project is an independent Fiji/ImageJ implementation and does not copy QuPath source code. QuPath and its contributors are credited as the behavioral and algorithmic reference; exact source links and implementation differences are documented in [docs/research.md](docs/research.md).

Code generated with OpenAI Codex, then built and tested against Fiji/ImageJ and the supplied multichannel image.

## Install and run

Tested with the installed Fiji **2.18.0 / ImageJ 1.54p**, bundled Zulu **21.0.7** JDK, Linux/X11. No Maven, network, or added libraries are needed to build.

For a ready-to-install build, copy `dist/Intensity_Selection_Tools.jar` into Fiji's `plugins/` directory and restart Fiji.

```sh
cd /home/rbonnav/Documents/Codex/2026-09-13-brushtoolFiji
./build.sh
./install.sh
```

Restart Fiji, then choose **Plugins → Selection Tools → Install Brush and Smart Brush**. Click the **B** or **W** toolbar icon. Double-click either icon for settings. **Stop Brush and Smart Brush** removes the shared event routing, keyboard dispatcher, command listener, worker, timer and footprint overlay. Re-run Install to enable again. Settings currently last for the enabled session.

`FIJI_DIR`, `JAVA_HOME`, and `IJ_JAR` can override the defaults in `build.sh`. The plugin is compiled for Java 8 bytecode, but the tested Fiji distribution requires its bundled Java 21 runtime. Other ImageJ versions have not been GUI-tested.

## Controls

| Input | Action |
|---|---|
| Left click/drag outside the ROI | Replace existing selection with one complete stroke |
| Left click/drag starting inside the ROI | Union the stroke with the current ROI |
| Shift + left drag | Add to selection |
| Ctrl + left drag | Subtract; recommended on this Linux desktop |
| Alt + left drag | Subtract when the window manager passes Alt through |
| Space + left drag | Temporary pan; does not paint |
| Space during painting | Cancel that uncommitted stroke, restore starting ROI, then pan |
| Release Space during a drag | Stop panning; release mouse before starting another stroke |
| Hold Q + wheel | Smooth size adjustment, 3–256 pixels at 100% zoom |
| Ordinary wheel / Ctrl-wheel | Existing ImageJ scroll / zoom behavior |
| Escape | Cancel pending stroke and restore starting ROI |
| Ctrl+Z or Edit → Undo | Undo one stroke |
| Ctrl+Y / Ctrl+Shift+Z | Redo one stroke |
| Double-click tool icon | Fixed/adaptive size, raw channel, smoothing, sensitivity, tolerance, resize key |

Space overrides resize. Alt overrides Shift; Ctrl also subtracts. The Linux window manager intercepted Alt-drag during testing, so Ctrl is provided without altering desktop settings. Q is intercepted only while held over an image canvas with these tools selected; text fields and other tools retain their bindings. Resize key may be changed to A–W in the tool options. Wheel deltas are multiplicative, including fractional trackpad deltas.

History keeps 30 strokes for the current image/plane context. Changing image/channel/Z/time or invoking another ImageJ command cancels pending work and resets plugin history; native commands then retain their own undo behavior. External ROI replacement also resets history. Line/point selections must be cleared before using these area tools.

The internal AWT `Area` is rasterized to a source-resolution binary mask and converted with ImageJ's topology-aware `ThresholdToSelection`; holes and disconnected regions are retained. For development diagnosis, the options dialog can log a pixel-level comparison between that internal mask and the final ImageJ ROI. Each completed or previewed update reports internal, ROI, extra, and missing pixel counts in ImageJ's Log window. Leave this disabled during normal use.

## Sampling and geometry

The cyan circle is the **full local footprint**. Double-click either tool and enable **Adaptive diameter (constant displayed size, QuPath-style)** to make its image-space diameter change inversely with magnification. For example, an 80-pixel setting uses 320 image pixels at 25% zoom, 80 at 100%, and 20 at 400%, so the displayed footprint remains about 80 screen pixels. Fixed mode preserves the original behavior: the footprint grows on screen when zooming in and shrinks when zooming out. Status shows the active size mode and both diameters.

Diameter is stored as its value at 100% zoom. In fixed mode, `Dimage = Dsetting`; in adaptive mode, `Dimage = max(1, Dsetting / m)`. In both modes, `Dscreen = Dimage * m`. ImageJ's `offScreenX/Y` methods identify the exact source pixel under the pointer; processing begins at that pixel center.

Smart mode uses an odd-sized grid with exactly one grid cell per source pixel. Fixed mode does not use display magnification and produces the same source-pixel mask at every zoom. Adaptive mode uses magnification only to calculate the circular footprint; it never resamples the rendered canvas or repeats subpixel samples. Gaussian sigma and intensity comparisons remain in raw source-pixel units.

A separable Gaussian (default sigma 4 source pixels, radius `ceil(2*sigma)`, reflected borders) precedes four-connected, fixed-seed region growing. A pixel qualifies when its raw channel value differs from the smoothed seed by at most local standard deviation / sensitivity (default 2). Alternatively enable absolute tolerance in raw channel units (default 10). Sigma 0 disables smoothing. Nonfinite pixels are barriers and a nonfinite seed selects nothing. The circular footprint and image boundary clip the result.

The smart brush always reads the underlying processor. Channel 0 means the active channel; a positive channel number locks sampling to that channel on the current Z/T plane. LUT, brightness/contrast, overlay and pan cannot affect segmentation. Zoom affects only footprint size when adaptive mode is enabled. Grayscale 8/16/32-bit values retain their native units. RGB images use their three stored 0–255 components.

Processing settings and magnification freeze at stroke start. Zoom may occur between strokes; fixed mode keeps the same mask, while adaptive mode recalculates the next stroke's footprint. Resizing, focus loss and image/channel/Z/T changes cancel the uncommitted stroke. The worker coalesces pending pointer updates and interpolates between them; under load very fast curved motion can be simplified.

## Verification and reference

- [Verification results](docs/verification.md), including native GUI and engine checks.
- [Source-based QuPath comparison](docs/research.md), exact commits, API references and deviations.
- `test-images/`: generated grayscale, RGB and composite TIFF fixtures.
- `test/GuiHarness.java`: reproducible native Robot interaction tests inside Fiji.
- `test/ExampleGuiHarness.java`: native checks on the supplied multichannel image, channel 2.

To reproduce GUI tests (they move the pointer and focus **test windows**):

```sh
./build.sh
cd /home/rbonnav/Documents/Codex/Fiji
./fiji --allow-multiple --class-path /home/rbonnav/Documents/Codex/2026-09-13-brushtoolFiji/build/classes --run /home/rbonnav/Documents/Codex/2026-09-13-brushtoolFiji/test/gui.ijm
```

Do not interact with the desktop until `build/gui-results.txt` reports completion. The test uses only generated images, and leaves them open for inspection. `Synthetic.main` regenerates the fixtures. Research source copies are excluded from the plugin JAR.
