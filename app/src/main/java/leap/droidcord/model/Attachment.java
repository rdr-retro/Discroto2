package leap.droidcord.model;

import leap.droidcord.State;
import leap.droidcord.Util;

import android.content.res.Resources;

import cc.nnproject.json.JSONObject;

public class Attachment {
    private static final String[] nonTextFormats = {".zip", ".rar", ".7z",
            ".exe", ".jar", ".apk", ".sis", ".sisx", ".bin", ".mp3", ".wav",
            ".ogg", ".m4a", ".amr", ".flac", ".mid", ".mmf", ".mp4", ".3gp"};

    public String url;
    public String previewUrl;
    public String name;
    public String mimeType;
    public int size;
    public boolean supported;
    public boolean isText;
    public boolean isLoading;
    public boolean isLoaded;

    public Attachment(State s, String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public Attachment(State s, JSONObject data) {
        String proxyUrl = data.getString("proxy_url");

        // Build CDN url - handle different proxy_url formats
        if (proxyUrl.startsWith("https://media.discordapp.net")) {
            url = s.cdn + proxyUrl.substring("https://media.discordapp.net".length());
        } else if (proxyUrl.startsWith("https://")) {
            url = "http://" + proxyUrl.substring("https://".length());
        } else {
            url = proxyUrl;
        }

        name = data.getString("filename", "Unnamed file");
        mimeType = data.getString("content_type", "text/plain");
        size = data.getInt("size", 0);

        // Attachments that aren't images or videos are unsupported
        // (cannot be previewed but can be viewed as text or downloaded)
        boolean isGif = name.toLowerCase().endsWith(".gif") || mimeType.equals("image/gif")
                || mimeType.startsWith("image/gif");
        if (!data.has("width") && !isGif) {
            supported = false;

            // Can be viewed as text if it's not one of the blacklisted file
            // extensions
            isText = (Util.indexOfAny(name.toLowerCase(), nonTextFormats, 0) == -1);

            return;
        }

        supported = true;
        int imageWidth = data.getInt("width", 0);
        int imageHeight = data.getInt("height", 0);

        int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;

        // For GIFs, request the original gif format so we get the actual image data
        // For others, use jpeg/png as before
        String format;
        if (isGif) {
            format = "png"; // Request as PNG (static first frame) for Android 2.3 compat
        } else {
            format = s.useJpeg ? "jpeg" : "png";
        }

        // Preview url is not using our own proxy, because media.discordapp.net
        // works over http
        if (proxyUrl.startsWith("https://")) {
            previewUrl = "http://" + proxyUrl.substring("https://".length());
            // Only add query params if URL doesn't already have them
            if (previewUrl.contains("?")) {
                previewUrl += "&format=" + format + "&width=" + screenWidth;
            } else {
                previewUrl += "?format=" + format + "&width=" + screenWidth;
            }
        } else {
            previewUrl = proxyUrl;
        }
    }
}