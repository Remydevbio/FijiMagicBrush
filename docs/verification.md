# Verification results

## 2026-09-14 zoom-invariance update

The revised build passes **44 synthetic engine assertions**, a headless regression on the supplied multichannel image, **35 native Fiji interaction assertions**, and **7 native Fiji assertions on channel 2 of the supplied image**.

The supplied `example_multichannel.tif` is 914×899, 16-bit, three-channel data. On channel 2, seed `(648,349)` selected 4,799 pixels, of which 4,798 overlap the supplied rough ROI. Headless masks are exactly equal at 0.25×, 1× and 8×. Native Fiji checks passed up to 3×: channel 2 was active, the selection overlapped the rough ROI, Alt-click changed neither viewport nor window bounds, display-range changes produced zero different pixels, and high zoom produced zero different pixels.

The zoom bug came from dividing diameter and sampling interval by display magnification. Above 1×, multiple processing cells repeatedly accessed one source pixel; below 1×, pixels were skipped. Magnification also changed Gaussian scale, local statistics and ROI geometry. A second rounding issue could map a nominal image location to an adjacent pixel at fractional zoom. The fixed path uses ImageJ `offScreenX/Y`, source-pixel centers, one cell per raw selected-channel pixel, and image-pixel units throughout.

An unmodified stroke starting inside an ROI now unions with it; a stroke starting outside retains replacement behavior. Recent warmed-up engine runs averaged 6–10 ms per 149-image-pixel dab. The installed JAR was rebuilt from this source and verified in Fiji. Current logs are `build/gui-results.txt` and `build/example-gui-results.txt`. The sections below record the original 2026-09-13 baseline and QuPath comparison; where behavior differs, this update supersedes them.

Final testing on 2026-09-13, Linux/X11, Fiji 2.18.0 / ImageJ 1.54p and Java 21.0.7. QuPath reference: 0.7.0 / bundled Java 25.0.2.

## Build and engine

`./build.sh` passes **33 assertions** against the installed `ij-1.54p.jar`. It builds the distributable JAR without downloading dependencies. The only compiler warnings concern Java 8 target obsolescence under JDK 21. The JAR contains only plugin classes, menu configuration and license, not test harnesses or upstream research copies.

Tests cover 8-bit, 16-bit, float and RGB intensity selection; bounded footprints at multiple magnifications; dark holes; raw independence from brightness settings; nonfinite seed rejection; four-connectivity versus a diagonal island; one-pixel connectivity; image-edge clipping; unchanged input pixels; default rendered/smoothed behavior; min/max and fractional resize; global ROI coordinates; and undo from initially absent selection.

Final warmed-up benchmark: **9.16 ms mean** per 149-screen-pixel smart dab at 0.25× on a 4096×4096 float image (30 dabs, after 10 warmups). Across runs this varied approximately 8–13 ms. This measures the local sampling/filter/flood/geometry computation, not end-to-end screen latency. The local grid is at most 257² samples regardless of image dimensions. Raw sampling avoids asking the processor to compute display min/max. ROI union and complex long strokes can cost more than an isolated dab; virtual-stack loading is controlled by ImageJ.

Full output: [build and engine log](evidence/build-and-engine.txt).

## Real Fiji GUI

A Java `Robot` harness ran inside Fiji launched by the actual `./fiji` executable from its installation directory. Setup and assertions used ImageJ APIs; click/drag, modifiers, keyboard undo, wheel, focus changes and text entry used real native input. The fractional wheel check posts an AWT event with a noninteger delta, rather than claiming physical trackpad hardware was tested.

**32 GUI assertions passed at 100% scaling**, including actual movement of a cropped viewport with Space while preserving the ROI. **30 assertions passed at 200% Java2D scaling** with the graphics transform verified as 2×; the two later cropped-viewport assertions were only rerun at 100%.

The suite verifies that the plugin tool is actually active, smart seed/intensity/background/hole behavior, replace/add/subtract, Ctrl+Z/Ctrl+Y, whole-stroke undo, Escape rollback, Q-wheel without zoom, Space before/during drawing and release midway through panning, fast-drag interpolation, zoom behavior, native Ctrl-wheel zoom, ROI coordinate invariance, fractional wheel, ordinary Q text entry outside the canvas, focus-loss rollback, raw 16-bit/float/RGB behavior, composite channel selection and channel-change cancellation.

- [100% GUI log](evidence/gui-1x.txt)
- [200% GUI log](evidence/gui-2x.txt)
- [Fiji screenshot](evidence/fiji.png)
- [200% Fiji screenshot](evidence/fiji-2x.png)

Testing caught and corrected a custom-tool naming collision in the harness, test-expectation mutation, cursor refresh after zoom, and raw-mode display-range access. The desktop intercepts Alt-drag for moving windows, so native subtraction was verified with the provided Ctrl alternative. No desktop configuration was changed.

## Real QuPath comparison

QuPath was launched using the discovered `bin/QuPath`. The same synthetic 8-bit TIFF was opened through its native file dialog. Its wand was dragged along the bright ring; the bounded result followed intensity and curved around the dark hole. Its ordinary brush drew across the gradient background. Wheel input changed displayed magnification from 1.61× to 7.90×; a brush dab retained approximately 50 screen-pixel width, while its original-image extent shrank. Space-drag navigated the view.

- [Native wand result](evidence/qupath-wand.png)
- [Native brush result](evidence/qupath-brush.png)
- [Brush at higher zoom](evidence/qupath-brush-zoomed.png)
- [Zoom/pan view](evidence/qupath-zoom-pan.png)

A right-bracket size probe did not visibly alter the reference brush. Reference size-preference adjustment and modifier combinations were not exhaustively verified in QuPath; their documented behavior is source-based where indicated. No pixel-exact equivalence is claimed: channel rendering, grid mapping, hole preservation and adjustable footprint are intentional adaptations.

## Limits and reproducible further checks

- Physical trackpad hardware and noninteger OS scaling (such as 150%) remain untested; 100% and 200% Java2D rendering passed.
- Composite channel changes were exercised; separate Z/time changes use the same stack-index guard but have not each been driven through their native sliders.
- Virtual stacks, rotated third-party canvases, tablet pressure, extensive complex-ROI endurance and concurrent third-party pixel writers are outside this verification. Only standard ImageJ canvas transforms are supported.
- Rapid image/parameter/context changes cancel pending work. The suite covers channel, focus, resize and zoom changes, but not every possible third-party window/event integration.
- QuPath uses closing and external contours; this plugin intentionally preserves holes instead. Coarse local sampling can lose thin bridges. Inspect at higher zoom or reduce sigma when those features matter.

For manual review, open `test-images/synthetic-8.tif`, try B and W at 0.25×/1×/2×, and use Shift/Ctrl to add/subtract. Double-click W and compare rendered versus raw modes while changing brightness. In the composite fixture, compare channel 1 (structured) and channel 2 (blank). Hold Space midway through a stroke, pan, release Space, and confirm a fresh mouse press is needed to paint. Use Ctrl+Z/Y to step through entire strokes.
