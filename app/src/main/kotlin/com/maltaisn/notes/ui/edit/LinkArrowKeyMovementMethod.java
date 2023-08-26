/*
 * Copyright 2023 Nicolas Maltais
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.maltaisn.notes.ui.edit;

import android.os.Build;
import android.text.Layout;
import android.text.NoCopySpan;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.textclassifier.TextLinks;
import android.widget.TextView;

import com.maltaisn.notes.ui.edit.adapter.EditEditText;

import java.lang.reflect.Method;

/**
 * Class that combines {@link LinkMovementMethod} and {@link ArrowKeyMovementMethod}.
 * This class only works when used with a {@link EditEditText}.
 */
public class LinkArrowKeyMovementMethod extends ArrowKeyMovementMethod {
    private static final int CLICK = 1;
    private static final int UP = 2;
    private static final int DOWN = 3;

    private static final int HIDE_FLOATING_TOOLBAR_DELAY_MS = 200;

    @Override
    public boolean canSelectArbitrarily() {
        return true;
    }

    @Override
    protected boolean handleMovementKey(TextView widget, Spannable buffer, int keyCode,
                                        int movementMetaState, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN &&
                            event.getRepeatCount() == 0 && action(CLICK, widget, buffer)) {
                        return true;
                    }
                }
                break;
        }
        return super.handleMovementKey(widget, buffer, keyCode, movementMetaState, event);
    }

    @Override
    protected boolean up(TextView widget, Spannable buffer) {
        if (action(UP, widget, buffer)) {
            return true;
        }

        return super.up(widget, buffer);
    }

    @Override
    protected boolean down(TextView widget, Spannable buffer) {
        if (action(DOWN, widget, buffer)) {
            return true;
        }

        return super.down(widget, buffer);
    }

    @Override
    protected boolean left(TextView widget, Spannable buffer) {
        if (action(UP, widget, buffer)) {
            return true;
        }

        return super.left(widget, buffer);
    }

    @Override
    protected boolean right(TextView widget, Spannable buffer) {
        if (action(DOWN, widget, buffer)) {
            return true;
        }

        return super.right(widget, buffer);
    }

    private static void linkOnClick(TextView widget, Spannable buffer, ClickableSpan link) {
        if (link instanceof URLSpan) {
            int start = buffer.getSpanStart(link);
            int end = buffer.getSpanEnd(link);
            String url = ((URLSpan) link).getURL();
            String text = buffer.subSequence(start, end).toString();
            ((EditEditText) widget).onLinkClicked(text, url);
        }
    }

    private boolean action(int what, TextView widget, Spannable buffer) {
        Layout layout = widget.getLayout();

        int padding = widget.getTotalPaddingTop() +
                widget.getTotalPaddingBottom();
        int areaTop = widget.getScrollY();
        int areaBot = areaTop + widget.getHeight() - padding;

        int lineTop = layout.getLineForVertical(areaTop);
        int lineBot = layout.getLineForVertical(areaBot);

        int first = layout.getLineStart(lineTop);
        int last = layout.getLineEnd(lineBot);

        ClickableSpan[] candidates = buffer.getSpans(first, last, ClickableSpan.class);

        int a = Selection.getSelectionStart(buffer);
        int b = Selection.getSelectionEnd(buffer);

        int selStart = Math.min(a, b);
        int selEnd = Math.max(a, b);

        if (selStart < 0) {
            if (buffer.getSpanStart(FROM_BELOW) >= 0) {
                selStart = selEnd = buffer.length();
            }
        }

        if (selStart > last) {
            selStart = selEnd = Integer.MAX_VALUE;
        }
        if (selEnd < first) {
            selStart = selEnd = -1;
        }

        switch (what) {
            case CLICK: {
                if (selStart == selEnd) {
                    return false;
                }

                ClickableSpan[] links = buffer.getSpans(selStart, selEnd, ClickableSpan.class);

                if (links.length != 1) {
                    return false;
                }

                ClickableSpan link = links[0];
                final int start = buffer.getSpanStart(link);
                final int end = buffer.getSpanEnd(link);
                linkOnClick(widget, buffer, link);
                break;
            }
            case UP: {
                int bestStart = -1;
                int bestEnd = -1;

                for (ClickableSpan clickableSpan : candidates) {
                    int end = buffer.getSpanEnd(clickableSpan);

                    if (end < selEnd || selStart == selEnd) {
                        if (end > bestEnd) {
                            bestStart = buffer.getSpanStart(clickableSpan);
                            bestEnd = end;
                        }
                    }
                }

                if (bestStart >= 0) {
                    Selection.setSelection(buffer, bestEnd, bestStart);
                    return true;
                }

                break;
            }
            case DOWN: {
                int bestStart = Integer.MAX_VALUE;
                int bestEnd = Integer.MAX_VALUE;

                for (ClickableSpan candidate : candidates) {
                    int start = buffer.getSpanStart(candidate);

                    if (start > selStart || selStart == selEnd) {
                        if (start < bestStart) {
                            bestStart = start;
                            bestEnd = buffer.getSpanEnd(candidate);
                        }
                    }
                }

                if (bestEnd < Integer.MAX_VALUE) {
                    Selection.setSelection(buffer, bestStart, bestEnd);
                    return true;
                }

                break;
            }
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer,
                                MotionEvent event) {
        int action = event.getAction();

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();

            x += widget.getScrollX();
            y += widget.getScrollY();

            Layout layout = widget.getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);

            ClickableSpan[] links = buffer.getSpans(off, off, ClickableSpan.class);

            if (links.length != 0) {
                ClickableSpan link = links[0];
                // Don't open the link if clicked on the first or last character.
                // This is very annoying as clicking near a link to add text next to it will open it,
                // including when clicking in the end margin very far off the link!
                final int start = buffer.getSpanStart(link);
                final int end = buffer.getSpanEnd(link);
                if (start != off && end != off) {
                    if (action == MotionEvent.ACTION_UP) {
                        linkOnClick(widget, buffer, link);
                    } else {
                        if (widget.getContext().getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P) {
                            // Selection change will reposition the toolbar. Hide it for a few ms for a
                            // smoother transition.
                            try {
                                Method method = View.class.getMethod("hideFloatingToolbar", int.class);
                                method.invoke(widget, HIDE_FLOATING_TOOLBAR_DELAY_MS);
                            } catch (Exception e) {
                                // reflection failed
                            }
                        }
                        Selection.setSelection(buffer,
                                buffer.getSpanStart(link),
                                buffer.getSpanEnd(link));
                    }
                }
                return true;
            }
        }

        return super.onTouchEvent(widget, buffer, event);
    }

    @Override
    public void initialize(TextView widget, Spannable text) {
        super.initialize(widget, text);
        text.removeSpan(FROM_BELOW);
    }

    @Override
    public void onTakeFocus(TextView view, Spannable text, int dir) {
        super.onTakeFocus(view, text, dir);

        if ((dir & View.FOCUS_BACKWARD) != 0) {
            text.setSpan(FROM_BELOW, 0, 0, Spannable.SPAN_POINT_POINT);
        } else {
            text.removeSpan(FROM_BELOW);
        }
    }

    public static MovementMethod getInstance() {
        if (sInstance == null) {
            sInstance = new LinkArrowKeyMovementMethod();
        }
        return sInstance;
    }

    private static LinkArrowKeyMovementMethod sInstance;
    private static final Object FROM_BELOW = new NoCopySpan.Concrete();
}
