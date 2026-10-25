# Widget navigation hit areas

The home-screen widget's previous and next header buttons use 40dp-wide clickable areas, increased from 32dp to make taps less likely to land on adjacent controls. Six dp of padding on each side preserves a compact arrow appearance inside the wider targets. Both buttons remain attached to their existing note-navigation actions in `WidgetManager`.
