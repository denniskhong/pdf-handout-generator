# PDF Handout Generator — Technical Reconstruction Specification

## 1. Document purpose

This document is a **code-independent reconstruction specification** for the desktop application **PDF Handout Generator**. It describes the program's product intent, user experience, architecture, state model, algorithms, layout behaviour, PDF generation pipeline, build process, packaging expectations, error handling, and known limitations in enough detail that another capable language model or developer can recreate the application without seeing the original Java source file.

This specification intentionally contains **no copy of the Java implementation** and no complete method bodies. Names of conceptual components, controls, fields, algorithms, constants, and responsibilities are included where they are needed to preserve behaviour.

---

## 2. Header metadata required in the reconstructed source

The reconstructed Java source must begin with a documentation header containing the following information:

- **Application name:** PDF Handout Generator
- **Version:** 1.0.0
- **Language:** Java
- **GUI toolkit:** Swing/AWT
- **PDF engine:** Apache PDFBox
- **Creator:** M365 Copilot (GPT-5 reasoning model)
- **Vibe-coder:** Dennis Khong
- **License:** MIT
- **Project repository:** https://github.com/denniskhong/pdf-handout-generator
- **Purpose:** Create multiple-slides-per-page PDF handouts from an input PDF, with live preview and print-oriented transformations.

The source header, About dialog, and README credits must preserve exactly these roles:

> Creator: M365 Copilot (GPT-5 reasoning model)
>
> Vibe-coder: Dennis Khong

Do not assign Dennis Khong any additional designation, and do not add other contributors.

Recommended package identity:

- Maven group: `com.denniskhong`
- Maven artifact: `pdf-handout-generator`
- Java package: `com.denniskhong.pdfhandout`
- Main class: `PdfHandoutApp`

The package identity may be changed only if necessary for repository compatibility, but it must remain internally consistent across the source path, package declaration, Maven manifest, and launcher behaviour.

---

## 3. Product intent

The application converts a presentation-style PDF into a handout PDF containing multiple source pages on each output page. It is primarily intended for lecture slides, which are commonly landscape-oriented, while the handout paper is commonly portrait-oriented.

The core product goals are:

1. Make N-up handout creation accessible through a conventional desktop GUI.
2. Preview the actual page arrangement before generation.
3. Preserve source PDF vectors and text in normal mode.
4. Support optional left alignment so unused cell width remains on the right for handwritten notes.
5. Support optional slide numbering.
6. Provide an optional ink-saver mode for decorative-background removal and all identifiable PDF text rendered in black.
7. Produce one cross-platform executable fat JAR when built.
8. Use only Swing/AWT for the GUI so the same application JAR can run on Linux, Windows, and macOS with Java 21 or newer.

The application must not require pdfjam, LaTeX, JavaFX, Ghostscript, LibreOffice, or platform-specific native application libraries.

---

## 4. Technology and runtime requirements

### 4.1 Language and Java version

- Implement in **Java 21 or newer**.
- Compile with the Maven compiler `release` set to 21.
- Use Java records where useful for immutable option snapshots.
- Use standard Java desktop APIs from `java.desktop`.

### 4.2 GUI framework

Use **Swing and AWT**, not JavaFX.

Required Swing/AWT capabilities include:

- `JFrame` as the main window.
- A conventional `JMenuBar` attached through the frame's menu-bar API.
- `JFileChooser` for opening and saving PDFs.
- `JPanel` with standard layout managers.
- `JComboBox`, `JSpinner`, `JCheckBox`, `JButton`, `JTextField`, `JTextArea`, `JLabel`, and `JScrollBar`.
- A custom `JPanel` that overrides painting for the preview.
- `SwingWorker` for slow PDF loading, rendering, and generation.
- The operating system's Swing look and feel when available, with the cross-platform look and feel as fallback.

Do not emulate a menu bar using an ordinary panel. The menu must appear as the normal full-width frame menu.

### 4.3 PDF library

Use **Apache PDFBox 3.x**. The reference dependency version is 3.0.8.

Required PDFBox capabilities:

- Load PDFs through the PDFBox 3 loader API.
- Render source pages into `BufferedImage` objects for preview and ink-saver processing.
- Import source pages as PDF Form XObjects in normal output mode.
- Create new output pages and paint forms, frames, numbers, or images onto them.
- Create image XObjects from processed `BufferedImage` objects in ink-saver mode.

### 4.4 Build system

Use Maven. The build must use the Maven Shade Plugin to create one executable fat JAR containing the application, PDFBox, and PDFBox's Java dependencies.

Required artifact names:

- Internal project version: use the single authoritative value in Section 2
- Fat JAR filename: `pdf-handout-generator-v1.jar`
- Release ZIP filename: `pdf-handout-generator-v1.zip`

---

## 5. Main-window design

### 5.1 Window properties

The main window must:

- Be titled `PDF Handout Generator`.
- Have a practical initial size of approximately 1120 × 860 pixels.
- Have a minimum size of approximately 920 × 700 pixels.
- Use the platform's normal window decorations.
- Close cleanly, releasing any loaded PDF document.
- Be positioned by the operating system rather than hard-coded to screen coordinates.

### 5.2 Overall layout

The frame contains:

1. A conventional menu bar.
2. A padded content panel using a `BorderLayout` with approximately 12-pixel horizontal and vertical gaps.
3. The following content regions:
   - North: input-file row.
   - West: top-aligned Layout panel.
   - Center: preview area and page scrollbar.
   - South: output path, generation controls, status, and log.

The content panel should use an empty border of approximately 12 pixels on every side.

### 5.3 Layout panel top alignment

A critical peculiarity is that a component in the west position of `BorderLayout` is normally stretched vertically. To keep the Layout panel visually anchored at the top:

- Place the actual Layout panel in the **north** position of a wrapper panel.
- Place that wrapper in the main content panel's west position.

The Layout panel must never appear vertically centered.

### 5.4 Layout panel natural height

The Layout panel must not use a fixed preferred height. Its preferred width is approximately 290 pixels, but Swing must calculate its natural height from all child controls and labels.

This is essential because system font sizes, look-and-feel metrics, and display scaling differ across machines. A fixed height can clip explanatory text or the page-count label.

The implementation may override preferred-size calculation to return:

- Fixed width: about 290 pixels.
- Natural height: the superclass-calculated height.

---

## 6. Menus and keyboard accelerators

### 6.1 File menu

The File menu contains:

- **Open PDF…**
  - Opens the source-PDF chooser.
  - Accelerator: the platform menu shortcut plus `O`.
  - This is Ctrl+O on Linux/Windows and Command+O on macOS.

- **Save Handout As…**
  - Disabled until an input PDF is loaded.
  - Opens the destination chooser and, if the user approves, immediately generates the handout using current settings.
  - Accelerator: platform menu shortcut plus `S`.

- Separator.

- **Exit**
  - Closes the application cleanly.
  - Accelerator: platform menu shortcut plus `Q`.

### 6.2 Help menu

The Help menu contains:

- **About PDF Handout Generator**
  - Opens an informational dialog.

### 6.3 About dialog

The About dialog must display:

- PDF Handout Generator and the authoritative application version.
- A concise statement that it creates multiple-slides-per-page PDF handouts.
- `Creator: M365 Copilot (GPT-5 reasoning model)`
- `Vibe-coder: Dennis Khong`
- `License: MIT`
- The project repository URL: https://github.com/denniskhong/pdf-handout-generator

The repository URL must be rendered as a clickable hyperlink. Activating it opens the URL in the user's default web browser through the Java Desktop browsing API. If desktop browsing is unavailable or fails, show a clear error dialog rather than failing silently.

Use a non-editable, transparent HTML-capable Swing component such as `JEditorPane` inside the About dialog so the hyperlink has conventional visual styling and keyboard/mouse activation.

---

## 7. Input-file row

The input row spans the content width above the options and preview.

It uses a left-to-right layout equivalent to:

- Label: `Input:`
- Read-only text field containing the absolute input path.
- Button: `Open PDF…`

The text field expands horizontally. The button retains its natural width.

Opening a PDF should also propose a default output path in the same directory. If the input is `lecture.pdf`, the default output is `lecture-handout.pdf`.

---

## 8. Layout controls

The Layout panel contains a heading and the following controls.

### 8.1 Heading

- Text: `Layout`
- Bold and visibly larger than ordinary labels, approximately 18 pt.

### 8.2 Paper size

A combo box with these exact logical choices:

- A3
- A4
- A5
- Letter
- Legal

Reference page dimensions in PostScript points:

- A3: 841.89 × 1190.55
- A4: 595.28 × 841.89
- A5: 419.53 × 595.28
- Letter: 612 × 792
- Legal: 612 × 1008

Default: A4.

### 8.3 Orientation

A combo box with:

- Portrait
- Landscape

Default: Portrait.

Landscape swaps the paper width and height. It does not independently rotate individual source slides.

### 8.4 Columns and rows

Two integer spinners:

- Columns: minimum 1, maximum 8, default 1, step 1.
- Rows: minimum 1, maximum 8, default 2, step 1.

The convention throughout the program is always:

> Columns × Rows

Examples:

- 1 × 2 means one column, two rows, two source slides on each output page.
- 2 × 3 means two columns, three rows, six source slides on each output page.

Never silently reinterpret this as rows × columns.

### 8.5 Frame checkbox

Label:

> Frame each slide

Default: selected.

When enabled, draw a thin black border around the actual placed slide rectangle, not around the entire grid cell.

### 8.6 Left-alignment checkbox

Label:

> Left-align slides in each cell

Default: not selected.

Behaviour:

- When off, horizontally center each proportionally scaled slide in its grid cell.
- When on, set the slide's additional horizontal centering offset to zero, placing it at the cell's left content boundary.
- Keep vertical centering unchanged.
- Do not reserve a fixed notes percentage.
- Any blank space on the right arises naturally from the source aspect ratio and cell dimensions.

This is especially useful for a 1-column layout where users want space to write notes on the right.

### 8.7 Slide-number checkbox

Label:

> Add slide numbers

Default: not selected.

Behaviour:

- Number each placed slide according to its one-based source-PDF page number.
- Place the number below the slide, horizontally centered relative to the placed slide.
- Use black text at approximately 9 pt.
- Reserve vertical number space in the cell before calculating maximum slide size.
- The number is not the output-page number.

### 8.8 Ink-saver checkbox

Label:

> Ink saver

Default: not selected.

No explanatory sentence is displayed beside the checkbox. A concise tooltip may state that Ink saver removes detected backgrounds and renders identifiable PDF text in black. Detailed behaviour belongs in this specification and the README.

### 8.9 Explanatory labels

The panel contains muted explanatory text for:

- Left alignment.
- Ink-saver behaviour.
- The Columns × Rows convention.

Use HTML-capable Swing labels or another wrapping mechanism so long text wraps within the panel width instead of being clipped horizontally.

### 8.10 Page-count label

Before loading:

> No PDF loaded

After loading, display both counts, for example:

> Source: 37 pages  
> Output: 19 pages

After generation, it may display:

> Source: 37 pages  
> Generated: 19 pages

Use the term **page**, not sheet, in the user interface.

---

## 9. Preview area

### 9.1 Structure

The center region contains:

- Custom-painted preview panel in the center.
- Vertical scrollbar at the right.
- Centered page-position label at the bottom.

The Preview panel must resize naturally with the main window and must not bleed beyond its allocated region.

### 9.2 Preview background and paper

- Preview workspace background: medium-light gray, approximately RGB 216/216/216.
- Output paper: white rectangle.
- Paper outline: gray.
- Keep approximately 20 pixels of visual padding around the paper.
- Scale the whole paper uniformly to fit both the available preview width and height.
- Center the paper in the preview panel.

### 9.3 Preview image source

- Render source PDF pages at approximately 96 DPI using PDFBox.
- Cache the rendered `BufferedImage` objects.
- Use a bounded access-ordered cache of roughly 24 pages to control memory usage.
- Load missing pages for the current output page using a `SwingWorker`.
- Show a simple loading placeholder while a page image is unavailable.

### 9.4 Preview layout fidelity

The preview must mirror output calculations for:

- Paper dimensions.
- Orientation.
- Margins.
- Columns and rows.
- Cell spacing.
- Proportional slide scaling.
- Horizontal centering or left alignment.
- Vertical centering.
- Number-caption space.
- Frames.
- Slide numbers.
- Ink-saver appearance.

### 9.5 Preview page label

Display beneath the preview:

> Page X of Y

where X is the current one-based output page and Y is the calculated output-page count.

---

## 10. Page navigation

All page-navigation methods update the same vertical scrollbar value. The scrollbar's adjustment listener is the authoritative mechanism that:

1. Updates `Page X of Y`.
2. Requests missing preview images.
3. Repaints the preview.

### 10.1 Scrollbar

Retain all conventional scrollbar interaction:

- Drag the thumb.
- Click the up/down arrow buttons.
- Click the track.

Because Swing scrollbar maximum values are exclusive with respect to the visible amount, configure its range so that the final output page is reachable.

### 10.2 Mouse wheel

When the pointer is over the preview panel:

- One wheel notch upward moves to the previous output page.
- One wheel notch downward moves to the next output page.
- Stop at page 1 and the final page.
- Consume the wheel event after handling it.
- Do not remove or disable normal scrollbar interaction.

The mouse wheel is an additional convenience, not a replacement.

### 10.3 Keyboard navigation

The preview supports conventional page keys:

- Page Up: previous output page.
- Page Down: next output page.
- Home: first output page.
- End: final output page.

These should be Swing key bindings rather than a low-level global key listener. They should operate when the preview/navigation context is appropriate and must not interfere with normal text editing in a focused text field.

Arrow keys are not required because spinners, combo boxes, and text fields already use them.

---

## 11. Output and status region

### 11.1 Output row

The output row must align like the input row:

- Label: `Output:`
- Expandable output path field.
- Button: `Save as…`

The output field must begin at the same horizontal position as the input field. Use a shared three-column logic or equivalent alignment strategy. Do not allow BoxLayout indentation or label-width differences to make the output field appear shifted.

The output field is editable so a user may adjust the path manually.

### 11.2 Generate row

Below the output row:

- Left-aligned `Generate PDF` button.
- Status label to its right with modest spacing.

The Generate button is disabled until a source PDF is loaded and while a load/generation task is active.

### 11.3 Activity log

A read-only text area shows concise diagnostics such as:

- Loading/generation activity.
- Created output path.
- Number of output pages written.
- Preview-rendering errors.
- PDF close warnings.

Use a monospaced font. The log may wrap long lines.

---

## 12. Application state model

Maintain the following logical state:

- Loaded `PDDocument`, or null.
- `PDFRenderer` associated with the loaded document, or null.
- Absolute normalized input path, or null.
- Current paper size.
- Current orientation.
- Current column count.
- Current row count.
- Frame enabled state.
- Left-align enabled state.
- Slide-number enabled state.
- Ink-saver enabled state.
- Current output page selected by scrollbar.
- Bounded preview-image cache.
- Busy state for loading or generation.

Create an immutable options snapshot before starting PDF generation so that background work does not read mutable Swing controls directly.

Recommended snapshot fields:

- Paper size
- Orientation
- Columns
- Rows
- Frame
- Left alignment
- Slide numbers
- Ink saver

---

## 13. Loading workflow

When the user opens a PDF:

1. Show a PDF-filtered file chooser.
2. If cancelled, do nothing.
3. Close any previously loaded PDF safely.
4. Normalize and store the selected absolute path.
5. Display it in the input field.
6. Populate the default output path.
7. Clear the preview cache.
8. Set busy status to `Loading PDF…`.
9. Load the document in a `SwingWorker`.
10. On success:
    - Create a `PDFRenderer`.
    - Allow subsampling for efficient preview.
    - Enable Generate and Save menu actions.
    - Recalculate output-page range.
    - Request current preview images.
    - Repaint.
    - Set status to `Loaded`.
11. On failure:
    - Clear input state.
    - Show an error dialog.
    - Set status to `Load failed`.

Do not perform PDF loading on the Swing event-dispatch thread.

---

## 14. Geometry and N-up calculation

### 14.1 Output page count

Let:

- `N` = source-page count.
- `C` = columns.
- `R` = rows.
- `P` = C × R pages per output page.

Output-page count is ceiling division:

> (N + P − 1) / P

Use at least 1 as the UI scrollbar range even before a document is loaded.

### 14.2 Paper margin

Use an output margin of approximately 18 PostScript points on all sides.

### 14.3 Cell size

Usable paper size is paper size minus twice the margin.

- Cell width = usable width / columns.
- Cell height = usable height / rows.

Reserve an internal gap of approximately 3.5% of the smaller cell dimension on every side.

### 14.4 Number area

When slide numbering is enabled, reserve approximately:

- 3 points gap.
- 9 points font size.
- A few points additional descent/clearance.

Reference total: about 15 points.

This area is subtracted from available slide height before scale calculation.

### 14.5 Scaling

Preserve source aspect ratio.

Compute the scale as the smaller of:

- Available cell width / source width.
- Available cell height / source height.

Never crop the source slide.

### 14.6 Horizontal position

- Centered mode: cell content start plus half of unused width.
- Left-aligned mode: cell content start with no additional centering offset.

### 14.7 Vertical position

Always vertically center the slide within the available slide region above the number area.

### 14.8 Row ordering

Source pages are ordered left-to-right across a row, then top-to-bottom through rows.

The first source page for output page `k` is:

> (k − 1) × columns × rows

using zero-based source indices internally.

Remember that Swing preview coordinates start at top-left, while PDF output coordinates start at bottom-left. The output algorithm must invert row placement accordingly.

---

## 15. Normal PDF output mode

Normal mode is used when Ink-saver is disabled.

### 15.1 Preservation goal

- Preserve source vector graphics.
- Preserve source text as PDF content where possible.
- Avoid rasterising the source pages.

### 15.2 Generation procedure

1. Create a new target `PDDocument`.
2. Create a `LayerUtility` for the target.
3. For each calculated output page:
   - Add a new `PDPage` with the chosen paper rectangle.
   - Open one `PDPageContentStream`.
4. For each source page assigned to that output page:
   - Import the source page as a `PDFormXObject`.
   - Use the form bounding box as source dimensions.
   - Compute scale and position using the shared geometry rules.
   - Save graphics state.
   - Apply a matrix that scales the form and translates it into place, accounting for non-zero bounding-box origins.
   - Draw the form.
   - Restore graphics state.
   - Draw an optional frame.
   - Draw an optional source-page number.
5. Save the target document to the requested output path.
6. Close target resources reliably.

### 15.3 Slide-number font

Use a standard PDF font, typically Helvetica from the PDF standard 14 fonts. Calculate rendered text width from font metrics to center the number accurately.

---

## 16. Ink-saver mode

### 16.1 User-visible purpose

Ink-saver mode is intended for lecture handouts where decorative coloured or photographic backgrounds consume toner but add little informational value.

The intended visual transformation is:

- Dominant slide background → white.
- Every identifiable PDF text glyph → solid black, regardless of its original colour.
- Strongly coloured foreground charts/diagrams → retained where possible.
- Very pale foreground fills → lightened.

### 16.2 Important output trade-off

Ink-saver mode is raster-based because PDF files do not consistently expose semantic roles such as `background picture` or `text` in a simple, reliable way.

Therefore:

- Normal mode is vector-preserving.
- Ink-saver mode renders each source page at approximately 300 DPI.
- Processed pages are embedded as images in the output PDF.
- Text in Ink-saver output is no longer selectable.
- The original source PDF is never changed.

This limitation must be documented in the README and preferably exposed through a tooltip or help text.

### 16.3 Rendering resolution

Reference Ink-saver DPI: 300.

This is intended to retain good printed clarity while allowing pixel-level background analysis.

### 16.4 Background estimation

Estimate the background colour by sampling many evenly distributed pixels along all four page borders.

Reference approach:

- About 96 sample positions.
- At each position, sample top, bottom, left, and right borders.
- Average red, green, and blue components across samples.

The result is the estimated dominant border/background colour.

### 16.5 Pixel transformation

For each pixel:

1. Extract RGB components.
2. Calculate:
   - Maximum component.
   - Minimum component.
   - Perceived luminance using an approximately 0.299 / 0.587 / 0.114 weighting.
   - Manhattan colour distance from the estimated background.
3. Transform according to these rules.

#### Background removal

Set the pixel to white when either:

- It is sufficiently close to the estimated background colour, or
- It is already near-white.

Reference thresholds:

- Near-white component threshold: approximately 242 for each channel.
- Background component-distance constant: approximately 42, applied over the summed RGB distance.

#### All identifiable PDF text becomes black

Ink saver must not decide genuine PDF text colour from luminance or saturation. Use PDFBox text extraction to obtain glyph bounds for every identifiable PDF text object on the source page. During raster transformation, every non-background rendered pixel within those glyph bounds becomes solid black, regardless of whether the original text was white, red, blue, green, gray, or another colour.

The glyph-coordinate system must be mapped to the rendered image dimensions, accounting for the page crop box and rotation. Anti-aliased glyph pixels must also become black.

Pixel heuristics may remain as a fallback for text that was converted to outlines or flattened into an image, but semantic PDF text detection takes priority.

#### Pale foreground lightening

For non-background content with luminance above approximately 220, move each RGB component substantially toward white. A reference approach keeps only about one-third of its distance from white.

#### Other foreground content

Preserve it unchanged.

### 16.6 Preview behaviour

When Ink-saver is selected:

- Apply the same transformation algorithm to the cached preview image during painting.
- Do not modify the cached original preview image.
- Toggling the checkbox should immediately repaint without rerendering the source PDF.

### 16.7 Ink-saver PDF embedding

For each source page in Ink-saver output mode:

1. Render at 300 DPI.
2. Transform through the Ink-saver algorithm.
3. Create a lossless PDF image object from the result.
4. Use processed-image pixel dimensions for aspect-ratio calculations.
5. Draw the image into the calculated slide rectangle.
6. Add optional frame and slide number normally.

### 16.8 Known Ink-saver limitations

Document these explicitly:

- A full-page photograph used as meaningful content may be mistaken for a background.
- Text embedded in images or converted to vector outlines cannot always be identified as PDF text; the pixel heuristic is only a fallback.
- Backgrounds that do not reach the page border may not be detected.
- The transformation is heuristic and must be judged through the preview.
- Raster output can increase file size.

The design principle is to keep the algorithm understandable and adjustable rather than pretending it is perfect semantic segmentation.

---

## 17. Mouse-wheel and keyboard implementation details

Create shared navigation helpers conceptually equivalent to:

- Set current page with bounds checking.
- Move current page by a signed integer delta.
- Jump to first or last page.

Every navigation path must ultimately set the vertical scrollbar value. Do not duplicate preview refresh logic in each input handler.

For a standard mouse wheel, use only the sign of the wheel rotation so one notch means one page, regardless of implementation-specific rotation magnitude.

---

## 18. Background threading and Swing safety

### 18.1 Event-dispatch thread

All Swing component interaction occurs on the event-dispatch thread.

### 18.2 SwingWorker tasks

Use separate `SwingWorker` instances for:

- Opening a PDF.
- Rendering uncached preview pages.
- Generating the handout.

### 18.3 Immutable task inputs

Before generation, capture an immutable options record and normalized output path. The background task must not repeatedly query Swing controls.

### 18.4 Cache synchronization

Synchronize access to the preview cache because background workers write to it while the preview painter reads from it.

### 18.5 Busy state

While loading or generating:

- Disable generation.
- Disable Save Handout As.
- Show an appropriate status message.

Restore controls after completion or failure.

---

## 19. Validation and error handling

### 19.1 Input validation

Do not generate unless a PDF is loaded.

### 19.2 Output validation

Before generation:

- Output path must not be blank.
- Output path must parse successfully.
- Output path must not equal the input path.
- Parent directory must exist.

### 19.3 Error presentation

On failure:

- Add a concise message to the log.
- Show a Swing error dialog.
- Update the status label.
- Re-enable controls where appropriate.

### 19.4 Resource cleanup

- Close previous source documents before opening replacements.
- Close the source PDF when the frame is disposed.
- Close target documents and content streams through structured resource management.
- Preserve interruption status after catching `InterruptedException`.

---

## 20. Build and packaging specification

### 20.1 Maven POM

The POM must:

- Declare Java 21 release compilation.
- Depend on PDFBox 3.0.8 or a compatible 3.x release.
- Set final build name to `pdf-handout-generator-v1`.
- Use Maven Shade Plugin during `package`.
- Set the fat JAR manifest main class to the actual Swing main class.
- Exclude dependency signature files such as `.SF`, `.DSA`, and `.RSA` from the shaded JAR.
- Avoid producing a dependency-reduced POM if that would complicate the source repository.

### 20.2 Source build files

The GitHub source repository contains:

- `pom.xml`
- `build.sh`
- `build.bat`
- `README.md`
- `tech_specs.md`
- `LICENSE`
- `.gitignore`
- Java source under Maven's standard `src/main/java` tree.

### 20.3 Build scripts

Both build scripts must:

1. Verify Java exists.
2. Verify Maven exists.
3. Run `mvn clean package`.
4. Create a temporary staging directory named `pdf-handout-generator-v1`.
5. Copy the fat JAR, README, technical specification, and licence.
6. Generate all three runtime launchers:
   - `run.sh`
   - `run.command`
   - `run.bat`
7. Mark Unix launchers executable where the host permits.
8. Create `target/pdf-handout-generator-v1.zip`.
9. Remove all intermediate Maven artifacts, including the original thin JAR and staging directory.
10. Leave only the final release ZIP in `target/`.

### 20.4 Release ZIP contents

The ZIP contains exactly one top-level directory:

```text
pdf-handout-generator-v1/
├── pdf-handout-generator-v1.jar
├── run.sh
├── run.command
├── run.bat
├── README.md
├── tech_specs.md
└── LICENSE
```

### 20.5 Runtime launchers

All runtime launchers run the same command conceptually:

> java -jar pdf-handout-generator-v1.jar

They must resolve the JAR relative to the launcher's own directory, not the user's current working directory.

They must check that Java is available and display a useful message if it is not.

End users need Java 21 or newer but do not need Maven.

---

## 21. README requirements

The README must explain:

- Application purpose.
- Required Java version.
- Maven requirement for builders only.
- How to build on Linux/macOS and Windows.
- Where the release ZIP appears.
- How end users launch on each operating system.
- Columns × Rows convention.
- Normal vector-preserving output.
- Ink-saver rasterisation and its limitations.
- Credits containing exactly:
  - `Creator: M365 Copilot (GPT-5 reasoning model)`
  - `Vibe-coder: Dennis Khong`
- MIT licence.

---

## 22. Licence

Use the MIT License with Dennis Khong as the named copyright holder.

---

## 23. Acceptance criteria

A reconstruction is acceptable only if all the following are true.

### 23.1 Build

- `mvn clean package` succeeds with Java 21.
- The fat JAR runs using `java -jar` without a separate dependency directory.
- The build wrapper produces a single release ZIP and removes intermediates.

### 23.2 Cross-platform design

- No JavaFX dependency exists.
- No native PDF utility is required.
- Same JAR is used on Linux, Windows, and macOS.

### 23.3 UI

- Menu bar is conventional and frame-attached.
- About-dialog repository URL is clickable and opens the default browser.
- Layout panel is top-aligned and tall enough for all content.
- Input and output fields align consistently.
- Preview stays within its allocated area at all window sizes.
- Vertical scrollbar remains visible and functional.

### 23.4 Navigation

- Scrollbar dragging works.
- Scrollbar arrows work.
- Mouse wheel over preview changes one page per notch.
- Page Up/Page Down work.
- Home/End work.
- `Page X of Y` remains synchronized.

### 23.5 PDF output

- Paper size and orientation are correct.
- Columns × Rows convention is correct.
- Source order is left-to-right then top-to-bottom.
- Normal output preserves vectors.
- Left alignment removes only the extra centering offset.
- Frames outline slides.
- Slide numbers correspond to source page numbers.
- Output count is reported.

### 23.6 Ink saver

- Checkbox appears and defaults off.
- Preview updates immediately when toggled.
- Background-like pixels become white.
- All identifiable PDF text becomes solid black regardless of its original colour.
- Strongly coloured foreground content is generally retained.
- Ink-saver PDF output uses processed 300-DPI images.
- Normal mode remains vector-preserving.

---

## 24. Non-goals for the current release

The following are intentionally outside scope:

- OCR.
- Semantic recognition of every text object inside a flattened image.
- Perfect background segmentation.
- Editing individual source pages.
- Arbitrary page selection or reordering.
- Notes lines.
- Fixed-percentage notes columns.
- Password-protected PDF workflow.
- PDF accessibility structure preservation.
- Hyperlink-preservation guarantees in imposed output.
- A bundled Java runtime.
- Native installers.
- Touchpad gesture special handling.

---

## 25. Reconstruction guidance

A developer or language model reconstructing the application should proceed in this order:

1. Establish Maven, PDFBox, package identity, and executable shading.
2. Implement the Swing frame, menu, and static layout.
3. Implement file selection and asynchronous loading.
4. Implement page-count calculations and scrollbar range.
5. Implement preview rendering and cache.
6. Implement shared placement geometry.
7. Implement vector-preserving normal output.
8. Add frames and slide numbers.
9. Add mouse-wheel and keyboard navigation.
10. Add Ink-saver preview and raster output.
11. Add build scripts and release ZIP staging.
12. Test on presentations with 4:3 and 16:9 slides, portrait and landscape paper, multiple N-up layouts, dark backgrounds, photographic backgrounds, charts, and mixed-colour text.

The reconstruction should prioritize behavioural fidelity, predictable layout, and print usefulness over exact pixel-for-pixel reproduction of a particular Swing theme.

---

<!-- END OF FILE: tech_specs.md -->
