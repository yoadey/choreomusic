# UI Acceptance Criteria (Onboarding, Color Picker, Waveform)

## Onboarding
- Displays exactly 4 pages with title, description, and icon.
- `Skip` closes onboarding immediately.
- `Done` is shown only on the final page and closes onboarding.
- On rotation, the current page index is preserved.

## Color Picker
- Dialog shows a visible color swatch for each selectable entry.
- Current color is preselected when dialog opens.
- Selecting a color invokes callback exactly once with the selected value.
- Caller can apply selected color to UI and persist it to model/DB.

## Waveform SeekBar
- Supports and renders all custom attrs from `waveform_attrs.xml`:
  - `wave_background_color`, `wave_progress_color`
  - `wave_width`, `wave_gap`, `wave_min_height`
  - `wave_corner_radius`, `wave_gravity`
  - `wave_progress`, `wave_visible_progress`
- Touch down/move updates progress deterministically relative to view width.
- Progress callback provides reproducible values for same touch coordinates.
