# Note detail toolbar

The note detail screen shows only its normal up/back navigation in the toolbar. Account and Export/Import actions stay on the home screen, where the shared `action_bar_menu` is inflated. `NoteView.onCreateOptionsMenu` intentionally does not inflate that shared menu.
