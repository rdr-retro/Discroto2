package leap.droidcord.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Locale;

/**
 * Provides emoji rendering for Android 2.3 by replacing Unicode emoji
 * characters with inline Twemoji PNG images using Html.fromHtml + ImageGetter.
 */
public class EmojiCompat {
    private static final String TAG = "EmojiCompat";
    private static final Hashtable<String, Bitmap> cache = new Hashtable<String, Bitmap>();
    private static final Hashtable<String, Boolean> emojiAssets = new Hashtable<String, Boolean>();
    private static boolean emojiAssetsLoaded = false;
    private static int maxEmojiCodepoints = 1;
    private static final int VS16 = 0xFE0F;

    private static class EmojiMatch {
        final String assetName;
        final int nextIndex;

        EmojiMatch(String assetName, int nextIndex) {
            this.assetName = assetName;
            this.nextIndex = nextIndex;
        }
    }

    private static class EmojiReplace {
        final int start;
        final int end;
        final String assetName;

        EmojiReplace(int start, int end, String assetName) {
            this.start = start;
            this.end = end;
            this.assetName = assetName;
        }
    }

    private static synchronized void ensureEmojiAssetsLoaded(Context context) {
        if (emojiAssetsLoaded) return;

        try {
            String[] files = context.getAssets().list("emoji");
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    String file = files[i];
                    if (file == null) continue;

                    String lower = file.toLowerCase(Locale.US);
                    if (!lower.endsWith(".png")) continue;

                    String name = lower.substring(0, lower.length() - 4);
                    emojiAssets.put(name, Boolean.TRUE);

                    int cpCount = 1;
                    for (int j = 0; j < name.length(); j++) {
                        if (name.charAt(j) == '-') cpCount++;
                    }
                    if (cpCount > maxEmojiCodepoints) maxEmojiCodepoints = cpCount;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error indexing emoji assets", e);
        }

        Log.d(TAG, "Emoji assets indexed: " + emojiAssets.size() + ", maxCodepoints=" + maxEmojiCodepoints);
        emojiAssetsLoaded = true;
    }

    private static boolean hasEmojiAsset(String name) {
        return name != null && emojiAssets.get(name) != null;
    }

    private static String toAssetName(int[] codepoints, int length, boolean stripVariationSelector) {
        StringBuilder out = new StringBuilder(length * 6);
        boolean hasValue = false;
        for (int i = 0; i < length; i++) {
            int cp = codepoints[i];
            if (stripVariationSelector && cp == VS16) continue;
            if (hasValue) out.append('-');
            out.append(Integer.toHexString(cp));
            hasValue = true;
        }
        return hasValue ? out.toString() : null;
    }

    private static boolean containsVariationSelector(int[] codepoints, int length) {
        for (int i = 0; i < length; i++) {
            if (codepoints[i] == VS16) return true;
        }
        return false;
    }

    private static EmojiMatch findEmojiMatch(String text, int startIndex) {
        if (emojiAssets.size() == 0) return null;

        int[] codepoints = new int[maxEmojiCodepoints];
        int[] nextIndexes = new int[maxEmojiCodepoints];

        int count = 0;
        int i = startIndex;
        while (i < text.length() && count < maxEmojiCodepoints) {
            int cp = Character.codePointAt(text, i);
            i += Character.charCount(cp);
            codepoints[count] = cp;
            nextIndexes[count] = i;
            count++;
        }

        for (int len = count; len > 0; len--) {
            String name = toAssetName(codepoints, len, false);
            if (hasEmojiAsset(name)) {
                return new EmojiMatch(name, nextIndexes[len - 1]);
            }

            if (containsVariationSelector(codepoints, len)) {
                String normalized = toAssetName(codepoints, len, true);
                if (hasEmojiAsset(normalized)) {
                    return new EmojiMatch(normalized, nextIndexes[len - 1]);
                }
            }
        }

        return null;
    }

    private static Drawable getEmojiDrawable(Context context, TextView tv, String source) {
        try {
            int size = (int) (tv.getTextSize() * 1.2f);
            if (size <= 0) size = 24;

            String cacheKey = source + "@" + size;
            Bitmap bmp = cache.get(cacheKey);
            if (bmp == null) {
                InputStream is = null;
                try {
                    is = context.getAssets().open("emoji/" + source + ".png");
                    bmp = BitmapFactory.decodeStream(is);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
                if (bmp != null) {
                    Bitmap scaled = Bitmap.createScaledBitmap(bmp, size, size, true);
                    if (scaled != bmp) bmp.recycle();
                    bmp = scaled;
                    cache.put(cacheKey, bmp);
                }
            }
            if (bmp != null) {
                BitmapDrawable d = new BitmapDrawable(context.getResources(), bmp);
                d.setBounds(0, 0, bmp.getWidth(), bmp.getHeight());
                return d;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading emoji: " + source, e);
        }
        return null;
    }

    /**
     * Apply emoji rendering to a TextView.
     * Replaces Unicode emoji chars in the text with <img> tags,
     * then uses Html.fromHtml with a custom ImageGetter to render them.
     */
    public static void apply(final Context context, TextView tv) {
        try {
            ensureEmojiAssetsLoaded(context);
            if (emojiAssets.size() == 0) return;

            CharSequence text = tv.getText();
            if (text == null || text.length() == 0) return;

            String str = text.toString();
            ArrayList<EmojiReplace> replacements = new ArrayList<EmojiReplace>();

            int index = 0;
            while (index < str.length()) {
                EmojiMatch match = findEmojiMatch(str, index);
                if (match != null) {
                    replacements.add(new EmojiReplace(index, match.nextIndex, match.assetName));
                    index = match.nextIndex;
                } else {
                    int cp = Character.codePointAt(str, index);
                    index += Character.charCount(cp);
                }
            }

            if (replacements.size() == 0) return;

            SpannableStringBuilder builder = (text instanceof Spanned)
                    ? new SpannableStringBuilder(text)
                    : new SpannableStringBuilder(str);

            int applied = 0;
            for (int i = replacements.size() - 1; i >= 0; i--) {
                EmojiReplace replace = replacements.get(i);
                Drawable d = getEmojiDrawable(context, tv, replace.assetName);
                if (d == null) continue;

                builder.replace(replace.start, replace.end, "\uFFFC");
                builder.setSpan(new ImageSpan(d, ImageSpan.ALIGN_BOTTOM),
                        replace.start,
                        replace.start + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                applied++;
            }

            if (applied > 0) {
                tv.setText(builder);
                Log.d(TAG, "Applied emoji spans: " + applied);
            } else {
                Log.d(TAG, "Detected emoji candidates, but none could be rendered");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying emoji", e);
        }
    }
}
