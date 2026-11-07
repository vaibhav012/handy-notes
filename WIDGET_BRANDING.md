# Build and widget icons

## Behavior

- Release builds use the existing Handy Notes launcher icon from `@drawable/icon`.
- Debug builds keep the same note artwork with a red `D` badge in the lower-right corner, making the installed debug app easy to distinguish from release.
- The home-screen widget shows a small, low-opacity note mark at its bottom-right. The note list reserves space at the bottom so its last row does not sit underneath the mark.

## Implementation notes

- The debug source-set manifest at `app/src/debug/AndroidManifest.xml` overrides the application icon using `tools:replace="android:icon"`; the main manifest continues to supply the release icon.
- `app/src/main/res/drawable/icon_debug.xml` layers the existing icon with `icon_debug_badge.xml`.
- `app/src/main/res/layout/widget_layout.xml` places the decorative mark over the widget body. Its vector artwork is defined in `ic_widget_brand_mark.xml` for release, and overridden in `app/src/debug/res/drawable/ic_widget_brand_mark.xml` to include the red debug badge for debug builds.
