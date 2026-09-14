# Source and behavior comparison

Research date: 2026-09-13. The project folder initially contained no implementation or usable Git repository. Fiji and QuPath are application installations, not source checkouts. The target is a Fiji plugin developed here; QuPath is only a reference. No core application code was patched.

## Versions and ownership

| Component | Installed evidence | Matching source |
|---|---|---|
| Fiji | GUI identifies 2.18.0 / 1.54p; `jars/ij-1.54p.jar`; bundled JDK 21.0.7 | Distribution [fiji/fiji](https://github.com/fiji/fiji/tree/feed450a3f74237b9eb427bb91da40969fdded36), current main snapshot, **not claimed to be the installed distribution commit** |
| ImageJ core | 1.54p JAR, compilation and GUI verification | [v1.54p, 03382be1a0b650010b077102b5e5b20eaa6a8967](https://github.com/imagej/ImageJ/tree/03382be1a0b650010b077102b5e5b20eaa6a8967) |
| QuPath | Runtime log: 0.7.0, build 2026-02-25, commit 04ccfa4; bundled Java 25.0.2 | [v0.7.0, 04ccfa4fb7d43e9b566393e08e83690b72248d44](https://github.com/qupath/qupath/tree/04ccfa4fb7d43e9b566393e08e83690b72248d44) |

The provided repository URLs resolved to those same owners. Fiji's distribution POM and README establish that ImageJ core and tool code live in separate repositories. Relevant tools here belong to `imagej/ImageJ`, not `fiji/fiji`.

API review: [ImageJ module API](https://imagej.net/ij/developer/api/ij/module-summary.html), [PlugInTool events](https://imagej.net/ij/developer/api/ij/ij/plugin/tool/PlugInTool.html), and [ImageCanvas transforms](https://imagej.net/ij/developer/api/ij/ij/gui/ImageCanvas.html). The public [QuPath API index](https://qupath.github.io/javadoc/docs/) identifies **0.6.0**, behind the installed 0.7.0. Its viewer URL could not be retrieved through the web tool. Instead, the installed `qupath-gui-fx-0.7.0-javadoc.jar` supplied matching viewer API documentation (extracted as `research/qupath/QuPathViewer-api.html`). It confirms image/component transforms, downsample, rotation and center-position APIs. Implementation decisions use exact source and the installed ImageJ JAR, not assumed online signatures.

## QuPath implementation

[WandToolEventHandler.createShape / getBrushDiameter](https://github.com/qupath/qupath/blob/04ccfa4fb7d43e9b566393e08e83690b72248d44/qupath-extension-processing/src/main/java/qupath/process/gui/WandToolEventHandler.java) uses a 149×149 rendered patch. Downsample is rounded to quarter steps, minimum 0.25. RGB is default; grayscale and Lab-distance are alternatives. Rendering follows display settings and optionally non-hierarchy overlays. Default Gaussian sigma is 4, kernel radius `ceil(2*sigma)`. RGB/gray tolerance is patch SD divided by sensitivity (default 2). OpenCV performs four-connected fixed-range flood fill from the center, bounded by a pressure-dependent circular mask. A 5×5 elliptical closing follows. External contours are buffered by half a pixel, scaled/translated and rounded to original coordinates; interior contours are discarded. Shortcut-without-Shift instead selects exact values without that smoothing/closing. Its cursor diameter is only one eighth of the nominal patch width.

[BrushToolEventHandler](https://github.com/qupath/qupath/blob/04ccfa4fb7d43e9b566393e08e83690b72248d44/qupath-gui-fx/src/main/java/qupath/lib/gui/viewer/tools/handlers/BrushToolEventHandler.java), particularly `getBrushDiameter`, `createShape`, `mousePressed`, `isSubtractMode` and `mouseReleased`, scales diameter by pen pressure and optionally viewer downsample. It joins positions and performs geometric union/difference. Alt subtracts outside selection mode; Shift suppresses creating a new object. Clicking outside an existing annotation can create another object, unlike this plugin's explicit replace policy. [PathPrefs](https://github.com/qupath/qupath/blob/04ccfa4fb7d43e9b566393e08e83690b72248d44/qupath-gui-fx/src/main/java/qupath/lib/gui/prefs/PathPrefs.java) defaults to diameter 50 and magnification scaling enabled.

[ViewerManager scroll routing](https://github.com/qupath/qupath/blob/04ccfa4fb7d43e9b566393e08e83690b72248d44/qupath-gui-fx/src/main/java/qupath/lib/gui/viewer/ViewerManager.java) normally zooms around the pointer and uses the platform shortcut modifier for overlay opacity. [ScrollEventPanningFilter](https://github.com/qupath/qupath/blob/04ccfa4fb7d43e9b566393e08e83690b72248d44/qupath-gui-fx/src/main/java/qupath/lib/gui/viewer/ScrollEventPanningFilter.java) handles configured touch/scroll gestures, including rotation. [QuPathViewer.KeyEventFilter](https://github.com/qupath/qupath/blob/04ccfa4fb7d43e9b566393e08e83690b72248d44/qupath-gui-fx/src/main/java/qupath/lib/gui/viewer/QuPathViewer.java) maintains Space state; drawing handlers check navigation state. These inspected routes do not provide the requested B-wheel resize chord. A right-bracket probe in the installed GUI did not visibly change brush size; no undocumented shortcut is asserted.

## ImageJ integration findings

- [ImageCanvas.mousePressed / mouseDragged / mouseReleased](https://github.com/imagej/ImageJ/blob/03382be1a0b650010b077102b5e5b20eaa6a8967/ij/gui/ImageCanvas.java): Space handling precedes plugin callbacks. Merely adding a `PlugInTool` cannot reliably own a Space transition midway through a stroke. `offScreenXD/YD`, magnification and source rectangle provide the applicable transform; no viewer rotation is supported here.
- [ImageWindow.mouseWheelMoved](https://github.com/imagej/ImageJ/blob/03382be1a0b650010b077102b5e5b20eaa6a8967/ij/gui/ImageWindow.java): native wheel scrolling and Ctrl/Shift-wheel zoom do not test `isConsumed`. Therefore a later listener that merely consumes wheel events would still permit duplicate behavior.
- [PlugInTool](https://github.com/imagej/ImageJ/blob/03382be1a0b650010b077102b5e5b20eaa6a8967/ij/plugin/tool/PlugInTool.java) supplies toolbar integration/options. [Toolbar.setTool(String)](https://github.com/imagej/ImageJ/blob/03382be1a0b650010b077102b5e5b20eaa6a8967/ij/gui/Toolbar.java) searches custom names containing “ Tool” first; otherwise a name containing “brush” selects the built-in brush. The delivered names include the suffix.
- [Wand.autoOutline](https://github.com/imagej/ImageJ/blob/03382be1a0b650010b077102b5e5b20eaa6a8967/ij/gui/Wand.java) is a contour tracer with tolerance/connectivity modes; it is not the bounded, zoom-sampled QuPath algorithm. [BrushTool](https://github.com/imagej/ImageJ/blob/03382be1a0b650010b077102b5e5b20eaa6a8967/ij/plugin/tool/BrushTool.java) is not reused for scientific-image selection.
- [ShapeRoi](https://github.com/imagej/ImageJ/blob/03382be1a0b650010b077102b5e5b20eaa6a8967/ij/gui/ShapeRoi.java) supports Java2D shapes and boolean geometry. Its stored shape is relative to ROI bounds; the adapter translates to global image coordinates before union/subtraction. [Roi](https://github.com/imagej/ImageJ/blob/03382be1a0b650010b077102b5e5b20eaa6a8967/ij/gui/Roi.java) supplies cloning and C/Z/T positions.
- [ImageProcessor](https://github.com/imagej/ImageJ/blob/03382be1a0b650010b077102b5e5b20eaa6a8967/ij/process/ImageProcessor.java) supplies raw `getf` and packed RGB pixel access. [CompositeImage.getChannelLut](https://github.com/imagej/ImageJ/blob/03382be1a0b650010b077102b5e5b20eaa6a8967/ij/CompositeImage.java) provides explicit channel LUT/display ranges.
- [Undo.setup / undo](https://github.com/imagej/ImageJ/blob/03382be1a0b650010b077102b5e5b20eaa6a8967/ij/Undo.java): ROI undo cannot represent an initially absent ROI and offers no general multi-stroke redo stack. The plugin consequently stores cloned native ROIs and handles the existing Undo command through `CommandListener`; other commands invalidate this history and retain native handling.

The compiled extension uses a shared AWT EventQueue for owned canvas mouse events, a KeyboardFocusManager dispatcher for held keys, and otherwise forwards native events. Hooks remain gated while another tool is selected; Stop unregisters them. This avoids replacing the canvas or patching core classes.

## Deliberate adaptations

| Aspect | Delivered behavior |
|---|---|
| Footprint | Adjustable 3–256 source-image pixels for both modes; cursor shows the full bound |
| Resolution | Exactly one processing cell per source pixel; independent of display magnification |
| Pixels | Raw current/selected channel only; LUT and brightness-independent; RGB component bounds |
| Topology | Four-connectivity, hole-preserving ROI paths; omit closing/external-contour hole filling |
| Controls | Inside-ROI union; outside replace; Shift add; Ctrl/Alt subtract; configurable Q-wheel; Space pan |
| Scope | One native Fiji selection, not QuPath's annotation-object hierarchy; no pen-pressure or Lab mode |

QuPath was actually launched and used with the supplied synthetic TIFF. Screenshots show a bounded wand selection curving around the dark hole, ordinary brush strokes across uniform background, approximately constant brush screen width at 1.61× and 7.90×, and zoom/Space navigation. These observations support the comparison but are not an automated pixel-exact equivalence test.

## Licenses

Original plugin implementation uses ImageJ/JDK only; no QuPath/OpenCV/JTS code or binaries are copied into its JAR. This is a behavioral reimplementation, with intentional differences above. Original project code is MIT licensed. Downloaded research copies retain headers and their upstream license files: QuPath GPL-3.0-or-later; ImageJ public domain; Fiji's distribution license and component exceptions are recorded in `research/fiji/LICENSE.txt` and `LICENSES`. Incorporating QuPath code in a future revision would require reviewing its GPL obligations; this artifact does not link it.
