package leap.droidcord.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileCache {
    private static final String TAG = "FileCache";
    private File cacheDir;

    public FileCache(Context context) {
        cacheDir = new File(context.getCacheDir(), "icons");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }

    public void save(String key, Bitmap bitmap) {
        if (bitmap == null) return;
        File file = new File(cacheDir, key);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
        } catch (IOException e) {
            Log.e(TAG, "Error saving to cache: " + key, e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Bitmap get(String key) {
        File file = new File(cacheDir, key);
        if (file.exists()) {
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        }
        return null;
    }

    public boolean has(String key) {
        return new File(cacheDir, key).exists();
    }
}
