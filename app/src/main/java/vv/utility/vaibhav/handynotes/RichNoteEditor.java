package vv.utility.vaibhav.handynotes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One continuous Markdown editing surface shared by the note screen and widget editor. */
public class RichNoteEditor extends LinearLayout {
    private static final int TASK = 1, BULLET = 2, ORDERED = 3, QUOTE = 4, HIDDEN = 5;
    private static final Pattern TASK_LINE = Pattern.compile("^(\\s*[-*+]\\s+\\[)([ xX])(\\]\\s?)(.*)$");
    private static final Pattern HEADING_LINE = Pattern.compile("^(\\s*#{1,6}\\s+)(.*)$");
    private static final Pattern QUOTE_LINE = Pattern.compile("^(\\s*>\\s?)(.*)$");
    private static final Pattern ORDERED_LINE = Pattern.compile("^(\\s*)(\\d+)([.)]\\s+)(.*)$");
    private static final Pattern BULLET_LINE = Pattern.compile("^(\\s*[-*+]\\s+)(.*)$");
    private static final Pattern INLINE = Pattern.compile("(\\*\\*|__)(.+?)\\1|(?<!\\*)\\*([^*]+)\\*(?!\\*)|(?<!_)_([^_]+)_(?!_)|`([^`]+)`");

    private final EditText editor;
    private final int markerColor;
    private final int dpScale;
    private OnContentChangedListener contentChangedListener;
    private boolean applyingSpans;
    private int pressedTaskState = -1;
    private boolean suppressInputNormalization;
    private int insertedNewlineStart = -1;

    public interface OnContentChangedListener { void onContentChanged(String markdown); }
    public void setOnContentChangedListener(OnContentChangedListener listener) { contentChangedListener = listener; }

    public RichNoteEditor(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setPadding(dp(8), dp(6), dp(8), dp(6));
        setBackgroundResource(R.drawable.rounded_edittext);
        markerColor = getResources().getColor(R.color.primary, context.getTheme());
        dpScale = dp(1);

        HorizontalScrollView toolbarScroll = new HorizontalScrollView(context);
        toolbarScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbarScroll.addView(toolbar);
        addToolbarButton(toolbar, "Heading", "## ");
        addToolbarButton(toolbar, "Quote", "> ");
        addToolbarButton(toolbar, "• List", "- ");
        addToolbarButton(toolbar, "1. List", "1. ");
        addToolbarButton(toolbar, "☐ Task", "- [ ] ");
        addInlineButton(toolbar, "B", "**");
        addInlineButton(toolbar, "I", "*");
        addInlineButton(toolbar, "Code", "`");
        addView(toolbarScroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        View divider = new View(context);
        divider.setBackgroundColor(getResources().getColor(R.color.outline, context.getTheme()));
        addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, dp(1)));

        editor = new EditText(context);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setSingleLine(false);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setTextColor(getResources().getColor(R.color.on_background, context.getTheme()));
        editor.setHintTextColor(getResources().getColor(R.color.outline, context.getTheme()));
        editor.setHint("Start writing…");
        editor.setTextSize(15);
        editor.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        editor.setPadding(dp(4), dp(8), dp(4), dp(8));
        editor.setVerticalScrollBarEnabled(true);
        editor.setNestedScrollingEnabled(true);
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!suppressInputNormalization && before == 0 && count == 1
                        && start < s.length() && s.charAt(start) == '\n') insertedNewlineStart = start;
            }
            @Override public void afterTextChanged(Editable text) {
                if (applyingSpans) return;
                if (insertedNewlineStart >= 0) {
                    int newlineStart = insertedNewlineStart;
                    insertedNewlineStart = -1;
                    normalizeInsertedNewline(text, newlineStart);
                }
                applyMarkdownSpans(text);
                if (contentChangedListener != null) contentChangedListener.onContentChanged(text.toString());
            }
        });
        editor.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressedTaskState = taskStateAt(event.getX(), event.getY());
                return pressedTaskState >= 0;
            }
            if (event.getAction() == MotionEvent.ACTION_UP && pressedTaskState >= 0) {
                int stateIndex = pressedTaskState;
                pressedTaskState = -1;
                Editable text = editor.getText();
                if (stateIndex < text.length()) {
                    text.replace(stateIndex, stateIndex + 1,
                            Character.toLowerCase(text.charAt(stateIndex)) == 'x' ? " " : "x");
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_CANCEL) pressedTaskState = -1;
            return false;
        });
        editor.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_ENTER) return insertContinuedLine();
            if (keyCode == KeyEvent.KEYCODE_DEL) return removePrefixAtLineStart();
            return false;
        });
        addView(editor, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        setContent("");
    }

    private void addToolbarButton(LinearLayout toolbar, String label, String prefix) {
        TextView button = new TextView(getContext());
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(markerColor);
        button.setPadding(dp(10), dp(9), dp(10), dp(9));
        button.setBackgroundResource(android.R.drawable.list_selector_background);
        button.setOnClickListener(v -> applyPrefix(prefix));
        toolbar.addView(button);
    }

    private void addInlineButton(LinearLayout toolbar, String label, String delimiter) {
        TextView button = new TextView(getContext());
        button.setText(label);
        button.setTextSize(12);
        button.setTextColor(markerColor);
        button.setPadding(dp(10), dp(9), dp(10), dp(9));
        button.setBackgroundResource(android.R.drawable.list_selector_background);
        button.setOnClickListener(v -> wrapSelection(delimiter));
        toolbar.addView(button);
    }

    private void wrapSelection(String delimiter) {
        Editable text = editor.getText();
        int start = Math.max(0, editor.getSelectionStart());
        int end = Math.max(start, editor.getSelectionEnd());
        String selected = text.subSequence(start, end).toString();
        text.replace(start, end, delimiter + selected + delimiter);
        if (selected.isEmpty()) editor.setSelection(start + delimiter.length());
        else editor.setSelection(start + delimiter.length(), start + delimiter.length() + selected.length());
        editor.requestFocus();
    }

    public void setContent(String content) {
        String markdown = migrateBlockDocument(content == null ? "" : content);
        suppressInputNormalization = true;
        editor.setText(markdown);
        editor.setSelection(editor.length());
        applyMarkdownSpans(editor.getText());
        suppressInputNormalization = false;
    }

    public String getContent() { return editor.getText().toString(); }
    public Editable getText() { return editor.getText(); }
    public void requestEditorFocus() { editor.requestFocus(); editor.setSelection(editor.length()); }

    private void applyPrefix(String prefix) {
        Editable text = editor.getText();
        int selectionStart = Math.max(0, editor.getSelectionStart());
        int lineStart = selectionStart == 0 ? 0 : text.toString().lastIndexOf('\n', selectionStart - 1) + 1;
        int lineEnd = text.toString().indexOf('\n', selectionStart);
        if (lineEnd < 0) lineEnd = text.length();
        Matcher task = TASK_LINE.matcher(text.subSequence(lineStart, lineEnd));
        Matcher heading = HEADING_LINE.matcher(text.subSequence(lineStart, lineEnd));
        Matcher quote = QUOTE_LINE.matcher(text.subSequence(lineStart, lineEnd));
        Matcher ordered = ORDERED_LINE.matcher(text.subSequence(lineStart, lineEnd));
        Matcher bullet = BULLET_LINE.matcher(text.subSequence(lineStart, lineEnd));
        int bodyOffset = 0;
        boolean sameStyle = false;
        if (task.matches()) { bodyOffset = task.start(4); sameStyle = prefix.startsWith("- ["); }
        else if (heading.matches()) { bodyOffset = heading.start(2); sameStyle = prefix.startsWith("#"); }
        else if (quote.matches()) { bodyOffset = quote.start(2); sameStyle = prefix.startsWith(">"); }
        else if (ordered.matches()) { bodyOffset = ordered.start(4); sameStyle = prefix.matches("\\d+.*"); }
        else if (bullet.matches()) { bodyOffset = bullet.start(2); sameStyle = prefix.startsWith("-") && !prefix.startsWith("- ["); }
        int replacementLength = sameStyle ? 0 : prefix.length();
        text.replace(lineStart, lineStart + bodyOffset, sameStyle ? "" : prefix);
        int cursor = Math.min(lineStart + replacementLength + Math.max(0, selectionStart - lineStart - bodyOffset), text.length());
        editor.setSelection(cursor);
        editor.requestFocus();
    }

    private boolean insertContinuedLine() {
        int start = editor.getSelectionStart(), end = editor.getSelectionEnd();
        if (start < 0 || end < 0) return false;
        Editable text = editor.getText();
        int cursor = Math.min(start, end);
        int lineStart = cursor == 0 ? 0 : text.toString().lastIndexOf('\n', cursor - 1) + 1;
        int lineEnd = text.toString().indexOf('\n', cursor);
        if (lineEnd < 0) lineEnd = text.length();
        String line = text.subSequence(lineStart, lineEnd).toString();
        String body = line;
        String continuation = "";
        Matcher task = TASK_LINE.matcher(line);
        Matcher heading = HEADING_LINE.matcher(line);
        Matcher quote = QUOTE_LINE.matcher(line);
        Matcher ordered = ORDERED_LINE.matcher(line);
        Matcher bullet = BULLET_LINE.matcher(line);
        if (task.matches()) { body = task.group(4); if (!body.trim().isEmpty()) continuation = "- [ ] "; }
        else if (quote.matches()) { body = quote.group(2); if (!body.trim().isEmpty()) continuation = "> "; }
        else if (ordered.matches()) {
            body = ordered.group(4);
            if (!body.trim().isEmpty()) {
                try { continuation = (Integer.parseInt(ordered.group(2)) + 1) + ". "; }
                catch (NumberFormatException ignored) { continuation = ordered.group(2) + ". "; }
            }
        } else if (bullet.matches()) { body = bullet.group(2); if (!body.trim().isEmpty()) continuation = "- "; }
        else if (heading.matches()) body = heading.group(2);

        if ((task.matches() || bullet.matches() || ordered.matches()) && body.trim().isEmpty()) {
            suppressInputNormalization = true;
            text.replace(lineStart, lineEnd, "\n");
            editor.setSelection(Math.min(lineStart + 1, text.length()));
            suppressInputNormalization = false;
            applyMarkdownSpans(text);
            if (contentChangedListener != null) contentChangedListener.onContentChanged(text.toString());
            return true;
        }
        String insertion = "\n" + continuation;
        suppressInputNormalization = true;
        text.replace(start, end, insertion);
        editor.setSelection(start + insertion.length());
        suppressInputNormalization = false;
        applyMarkdownSpans(text);
        if (contentChangedListener != null) contentChangedListener.onContentChanged(text.toString());
        return true;
    }

    private boolean removePrefixAtLineStart() {
        int start = editor.getSelectionStart(), end = editor.getSelectionEnd();
        if (start < 0 || start != end) return false;
        Editable text = editor.getText();
        int lineStart = start == 0 ? 0 : text.toString().lastIndexOf('\n', start - 1) + 1;
        int lineEnd = text.toString().indexOf('\n', start);
        if (lineEnd < 0) lineEnd = text.length();
        String line = text.subSequence(lineStart, lineEnd).toString();
        int prefixLength = 0;
        Matcher task = TASK_LINE.matcher(line);
        Matcher heading = HEADING_LINE.matcher(line);
        Matcher quote = QUOTE_LINE.matcher(line);
        Matcher ordered = ORDERED_LINE.matcher(line);
        Matcher bullet = BULLET_LINE.matcher(line);
        if (task.matches()) prefixLength = task.start(4);
        else if (heading.matches()) prefixLength = heading.start(2);
        else if (quote.matches()) prefixLength = quote.start(2);
        else if (ordered.matches()) prefixLength = ordered.start(4);
        else if (bullet.matches()) prefixLength = bullet.start(2);
        if (prefixLength == 0 || start != lineStart + prefixLength) return false;
        suppressInputNormalization = true;
        text.replace(lineStart, lineStart + prefixLength, "");
        editor.setSelection(lineStart);
        suppressInputNormalization = false;
        applyMarkdownSpans(text);
        if (contentChangedListener != null) contentChangedListener.onContentChanged(text.toString());
        return true;
    }

    private void normalizeInsertedNewline(Editable text, int newlineStart) {
        String source = text.toString();
        int lineStart = newlineStart == 0 ? 0 : source.lastIndexOf('\n', newlineStart - 1) + 1;
        int lineEnd = source.indexOf('\n', newlineStart + 1);
        if (lineEnd < 0) lineEnd = source.length();
        String before = source.substring(lineStart, newlineStart);
        String after = source.substring(Math.min(newlineStart + 1, source.length()), lineEnd);
        String line = before + after;
        Matcher task = TASK_LINE.matcher(line);
        Matcher heading = HEADING_LINE.matcher(line);
        Matcher quote = QUOTE_LINE.matcher(line);
        Matcher ordered = ORDERED_LINE.matcher(line);
        Matcher bullet = BULLET_LINE.matcher(line);
        String continuation = "";
        boolean emptyListItem = false;
        if (task.matches()) {
            emptyListItem = task.group(4).trim().isEmpty();
            if (!emptyListItem) continuation = "- [ ] ";
        } else if (quote.matches()) {
            if (!quote.group(2).trim().isEmpty()) continuation = "> ";
        } else if (ordered.matches()) {
            if (!ordered.group(4).trim().isEmpty()) {
                try { continuation = (Integer.parseInt(ordered.group(2)) + 1) + ". "; }
                catch (NumberFormatException ignored) { continuation = ordered.group(2) + ". "; }
            } else emptyListItem = true;
        } else if (bullet.matches()) {
            emptyListItem = bullet.group(2).trim().isEmpty();
            if (!emptyListItem) continuation = "- ";
        }
        if (continuation.isEmpty() && !emptyListItem) return;

        suppressInputNormalization = true;
        if (emptyListItem) {
            text.replace(lineStart, newlineStart, "");
            editor.setSelection(Math.min(lineStart + 1, text.length()));
        } else {
            text.insert(newlineStart + 1, continuation);
            editor.setSelection(newlineStart + 1 + continuation.length());
        }
        suppressInputNormalization = false;
    }

    private int taskStateAt(float touchX, float touchY) {
        if (editor.getLayout() == null) return -1;
        int vertical = (int) (touchY - editor.getTotalPaddingTop() + editor.getScrollY());
        if (vertical < 0) return -1;
        android.text.Layout layout = editor.getLayout();
        int visualLine = layout.getLineForVertical(vertical);
        int lineStart = layout.getLineStart(visualLine);
        int lineEnd = Math.min(layout.getLineEnd(visualLine), editor.length());
        if (lineStart >= lineEnd) return -1;
        Spannable text = editor.getText();
        float horizontal = touchX - editor.getTotalPaddingLeft() + editor.getScrollX();
        for (MarkdownMarkerSpan span : text.getSpans(lineStart, lineEnd, MarkdownMarkerSpan.class)) {
            if (span.kind != TASK) continue;
            int spanStart = text.getSpanStart(span);
            int spanEnd = text.getSpanEnd(span);
            if (spanStart < lineStart || spanStart >= lineEnd) continue;
            float left = layout.getPrimaryHorizontal(spanStart);
            float right = layout.getPrimaryHorizontal(spanEnd);
            if (horizontal >= Math.min(left, right) - dp(4)
                    && horizontal <= Math.max(left, right) + dp(4)) {
                String sourceLine = text.subSequence(spanStart, text.toString().indexOf('\n', spanStart) < 0
                        ? text.length() : text.toString().indexOf('\n', spanStart)).toString();
                Matcher task = TASK_LINE.matcher(sourceLine);
                if (task.matches()) return spanStart + task.start(2);
            }
        }
        return -1;
    }

    private void applyMarkdownSpans(Editable text) {
        applyingSpans = true;
        for (MarkdownMarkerSpan span : text.getSpans(0, text.length(), MarkdownMarkerSpan.class)) text.removeSpan(span);
        for (StyleSpan span : text.getSpans(0, text.length(), StyleSpan.class)) text.removeSpan(span);
        for (TypefaceSpan span : text.getSpans(0, text.length(), TypefaceSpan.class)) text.removeSpan(span);
        for (AbsoluteSizeSpan span : text.getSpans(0, text.length(), AbsoluteSizeSpan.class)) text.removeSpan(span);

        String source = text.toString();
        int lineStart = 0;
        while (lineStart <= source.length()) {
            int lineEnd = source.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = source.length();
            String line = source.substring(lineStart, lineEnd);
            Matcher task = TASK_LINE.matcher(line);
            Matcher heading = HEADING_LINE.matcher(line);
            Matcher quote = QUOTE_LINE.matcher(line);
            Matcher ordered = ORDERED_LINE.matcher(line);
            Matcher bullet = BULLET_LINE.matcher(line);
            if (task.matches()) {
                int markerEnd = task.start(4);
                text.setSpan(new MarkdownMarkerSpan(TASK, markerColor, dpScale, task.group(2).equalsIgnoreCase("x")), lineStart, lineStart + markerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (heading.matches()) {
                int markerEnd = heading.start(2);
                text.setSpan(new MarkdownMarkerSpan(HIDDEN, markerColor, dpScale, false), lineStart, lineStart + markerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (heading.start(2) < line.length()) {
                    text.setSpan(new StyleSpan(Typeface.BOLD), lineStart + heading.start(2), lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text.setSpan(new AbsoluteSizeSpan(21, true), lineStart + heading.start(2), lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else if (quote.matches()) {
                text.setSpan(new MarkdownMarkerSpan(QUOTE, markerColor, dpScale, false), lineStart, lineStart + quote.start(2), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (quote.start(2) < line.length()) text.setSpan(new StyleSpan(Typeface.ITALIC), lineStart + quote.start(2), lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (ordered.matches()) {
                text.setSpan(new MarkdownMarkerSpan(ORDERED, markerColor, dpScale, false), lineStart, lineStart + ordered.start(4), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (bullet.matches()) {
                text.setSpan(new MarkdownMarkerSpan(BULLET, markerColor, dpScale, false), lineStart, lineStart + bullet.start(2), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            lineStart = lineEnd + 1;
        }

        Matcher inline = INLINE.matcher(source);
        while (inline.find()) {
            int group = inline.group(2) != null ? 2 : inline.group(3) != null ? 3 : inline.group(4) != null ? 4 : 5;
            if (group == 5) text.setSpan(new TypefaceSpan("monospace"), inline.start(group), inline.end(group), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            else text.setSpan(new StyleSpan(group == 2 ? Typeface.BOLD : Typeface.ITALIC), inline.start(group), inline.end(group), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int delimiterLength = group == 2 ? inline.group(1).length() : 1;
            text.setSpan(new MarkdownMarkerSpan(HIDDEN, markerColor, dpScale, false), inline.start(group) - delimiterLength, inline.start(group), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new MarkdownMarkerSpan(HIDDEN, markerColor, dpScale, false), inline.end(group), inline.end(group) + delimiterLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        applyingSpans = false;
    }

    public static String preview(String content) {
        return renderMarkdown(content).toString().trim().replaceAll("\\s+", " ");
    }

    /** Creates styled, marker-free content for the app widget and note previews. */
    public static CharSequence renderMarkdown(String content) {
        String source = migrateBlockDocument(content == null ? "" : content);
        StringBuilder output = new StringBuilder();
        String[] lines = source.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) output.append('\n');
            String line = lines[i];
            Matcher task = TASK_LINE.matcher(line);
            Matcher heading = HEADING_LINE.matcher(line);
            Matcher quote = QUOTE_LINE.matcher(line);
            Matcher ordered = ORDERED_LINE.matcher(line);
            Matcher bullet = BULLET_LINE.matcher(line);
            if (task.matches()) output.append(task.group(2).equalsIgnoreCase("x") ? "☑ " : "☐ ").append(stripInline(task.group(4)));
            else if (heading.matches()) output.append(stripInline(heading.group(2)));
            else if (quote.matches()) output.append("▏ ").append(stripInline(quote.group(2)));
            else if (ordered.matches()) output.append(ordered.group(1)).append(ordered.group(2)).append(ordered.group(3)).append(stripInline(ordered.group(4)));
            else if (bullet.matches()) output.append("•  ").append(stripInline(bullet.group(2)));
            else output.append(stripInline(line));
        }
        SpannableString result = new SpannableString(output.toString());
        Matcher inline = INLINE.matcher(source);
        String plain = result.toString();
        while (inline.find()) {
            int group = inline.group(2) != null ? 2 : inline.group(3) != null ? 3 : inline.group(4) != null ? 4 : 5;
            String value = inline.group(group);
            int start = plain.indexOf(value);
            if (start < 0) continue;
            if (group == 5) result.setSpan(new TypefaceSpan("monospace"), start, start + value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            else result.setSpan(new StyleSpan(group == 2 ? Typeface.BOLD : Typeface.ITALIC), start, start + value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return result;
    }

    private static String stripInline(String value) {
        return value.replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("__(.*?)__", "$1")
                .replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "$1")
                .replaceAll("(?<!_)_([^_]+)_(?!_)", "$1")
                .replaceAll("`([^`]+)`", "$1");
    }

    private static String migrateBlockDocument(String source) {
        try {
            JSONObject document = new JSONObject(source);
            if (document.optInt("version", -1) != 1) return source;
            JSONArray blocks = document.getJSONArray("blocks");
            StringBuilder migrated = new StringBuilder();
            for (int i = 0; i < blocks.length(); i++) {
                if (i > 0) migrated.append('\n');
                JSONObject block = blocks.getJSONObject(i);
                String kind = block.optString("kind", "paragraph");
                String body = block.optString("text", "");
                switch (kind) {
                    case "heading": migrated.append("## "); break;
                    case "quote": migrated.append("> "); break;
                    case "bullet": migrated.append("- "); break;
                    case "checklist": migrated.append("- [").append(block.optBoolean("checked") ? "x" : " ").append("] "); break;
                    default: break;
                }
                migrated.append(body);
            }
            return migrated.toString();
        } catch (Exception ignored) { return source; }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static class MarkdownMarkerSpan extends ReplacementSpan {
        private final int kind, color, boxSize;
        private final boolean checked;
        MarkdownMarkerSpan(int kind, int color, int density, boolean checked) {
            this.kind = kind; this.color = color; this.checked = checked;
            this.boxSize = Math.round(17 * density);
        }
        @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (kind == HIDDEN) return 0;
            if (kind == ORDERED) return Math.round(paint.measureText(text, start, end)) + boxSize / 3;
            return boxSize + boxSize / 2;
        }
        @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            if (kind == HIDDEN) return;
            Paint marker = new Paint(paint);
            marker.setColor(color);
            marker.setStrokeWidth(Math.max(1, boxSize / 10f));
            if (kind == TASK) {
                float left = x + boxSize / 8f;
                float topBox = (top + bottom - boxSize) / 2f;
                marker.setStyle(Paint.Style.STROKE);
                canvas.drawRoundRect(left, topBox, left + boxSize, topBox + boxSize, boxSize / 5f, boxSize / 5f, marker);
                if (checked) {
                    marker.setStyle(Paint.Style.STROKE);
                    marker.setStrokeCap(Paint.Cap.ROUND);
                    Path tick = new Path();
                    tick.moveTo(left + boxSize * .22f, topBox + boxSize * .52f);
                    tick.lineTo(left + boxSize * .43f, topBox + boxSize * .73f);
                    tick.lineTo(left + boxSize * .80f, topBox + boxSize * .28f);
                    canvas.drawPath(tick, marker);
                }
            } else if (kind == BULLET) {
                marker.setStyle(Paint.Style.FILL);
                canvas.drawCircle(x + boxSize / 3f, (top + bottom) / 2f, boxSize / 5f, marker);
            } else if (kind == QUOTE) {
                marker.setStyle(Paint.Style.STROKE);
                marker.setStrokeWidth(boxSize / 5f);
                canvas.drawLine(x + boxSize / 3f, top + boxSize / 5f, x + boxSize / 3f, bottom - boxSize / 5f, marker);
            } else if (kind == ORDERED) {
                marker.setStyle(Paint.Style.FILL);
                canvas.drawText(text, start, end, x, y, marker);
            }
        }
    }
}
