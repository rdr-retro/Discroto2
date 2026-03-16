package leap.droidcord;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONException;
import android.util.Log;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONObject;

public class HTTP {
    State s;
    String api;
    public String token;

    public HTTP(State s, String api, String token) {
        this.s = s;
        this.api = api;
        this.token = token.trim();
    }

    private static byte[] readBytes(InputStream inputStream, int initialSize,
            int bufferSize, int expandSize) throws IOException {
        if (initialSize <= 0)
            initialSize = bufferSize;
        byte[] buf = new byte[initialSize];
        int count = 0;
        byte[] readBuf = new byte[bufferSize];
        int readLen;
        while ((readLen = inputStream.read(readBuf)) != -1) {
            if (count + readLen > buf.length) {
                byte[] newbuf = new byte[count + expandSize];
                System.arraycopy(buf, 0, newbuf, 0, count);
                buf = newbuf;
            }
            System.arraycopy(readBuf, 0, buf, count, readLen);
            count += readLen;
        }
        if (buf.length == count) {
            return buf;
        }
        byte[] res = new byte[count];
        System.arraycopy(buf, 0, res, 0, count);
        return res;
    }

    public HttpURLConnection openConnection(String url) throws IOException {
        String fullUrl = (api + "/api/v9" + url).replace("//api", "/api");

        HttpURLConnection c = (HttpURLConnection) new URL(fullUrl)
                .openConnection();
        c.setDoOutput(true);

        c.addRequestProperty("Content-Type", "application/json");
        c.addRequestProperty("Authorization", token);

        return c;
    }

    public String sendRequest(HttpURLConnection c) throws Exception {
        InputStream is = null;
        is = c.getErrorStream();
        if (is == null)
            is = c.getInputStream();

        try {
            int respCode = c.getResponseCode();
            Log.d("HTTP", "Request URI: " + c.getURL().toString());
            Log.d("HTTP", "Response Code: " + respCode);

            // Read response
            StringBuffer stringBuffer = new StringBuffer();
            int ch;
            while ((ch = is.read()) != -1) {
                stringBuffer.append((char) ch);
            }
            String response = stringBuffer.toString().trim();

            if (respCode == HttpURLConnection.HTTP_OK) {
                if (response.length() == 0) {
                    throw new Exception("Empty response from " + c.getURL().toString());
                }
                return response;
            }
            if (respCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw new Exception("Check your token (401)");
            }

            if (response.length() == 0) {
                throw new Exception("HTTP error " + respCode + " from " + c.getURL().toString());
            }

            try {
                JSONObject json = JSON.getObject(response);
                String message = json.getString("message");
                throw new Exception(message);
            } catch (JSONException e) {
                throw new Exception("HTTP error " + respCode + ": " + response);
            }
        } finally {
            if (is != null)
                is.close();
        }
    }

    private String sendData(String method, String url, String data)
            throws Exception {
        HttpURLConnection c = null;
        OutputStream os = null;
        try {
            c = openConnection(url);
            c.setRequestMethod(method);

            if (method.equals("GET")) {
                c.setDoOutput(false);
                c.setDoInput(true);
            } else {
                c.setDoOutput(true);
                c.setDoInput(true);
            }

            byte[] b;
            try {
                b = data.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                b = data.getBytes();
            }

            c.setRequestProperty("Content-Length", String.valueOf(b.length));
            os = c.getOutputStream();
            os.write(b);

            return sendRequest(c);
        } finally {
            if (os != null)
                os.close();
        }
    }

    private String sendJson(String method, String url, JSONObject data)
            throws Exception {
        return sendData(method, url, data.toString());
    }

    public String get(String url) throws Exception {
        HttpURLConnection c = null;
        c = openConnection(url);
        c.setRequestMethod("GET");
        c.setDoOutput(false);
        c.setDoInput(true);
        return sendRequest(c);
    }

    public String post(String url, String data) throws Exception {
        return sendData("POST", url, data);
    }

    public String get(String url, String data) throws Exception {
        return sendData("GET", url, data);
    }

    public String post(String url, JSONObject data) throws Exception {
        return sendJson("POST", url, data);
    }

    public String get(String url, JSONObject data) throws Exception {
        return sendJson("GET", url, data);
    }

    public Bitmap getImage(String url) throws IOException {
        byte[] b = getBytes(url);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(b, 0, b.length, options);

        options.inSampleSize = calculateInSampleSize(options, 800, 800);
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeByteArray(b, 0, b.length, options);
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public byte[] getBytes(String url) throws IOException {
        HttpURLConnection hc = null;
        InputStream in = null;
        try {
            hc = open(url);
            int r;
            if ((r = hc.getResponseCode()) >= 400) {
                Log.e("HTTP", "Image fetch failed: " + r + " " + url);
                throw new IOException("HTTP " + r);
            }
            in = hc.getErrorStream();
            if (in == null)
                in = hc.getInputStream();
            return readBytes(in, (int) hc.getContentLength(), 1024, 2048);
        } finally {
            if (in != null)
                in.close();
        }
    }

    public void downloadToFile(String url, File file) throws IOException {
        HttpURLConnection hc = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            hc = open(url);
            int responseCode = hc.getResponseCode();
            if (responseCode >= 400) {
                Log.e("HTTP", "File download failed: " + responseCode + " " + url);
                throw new IOException("HTTP " + responseCode);
            }

            in = hc.getErrorStream();
            if (in == null) {
                in = hc.getInputStream();
            }

            out = new FileOutputStream(file);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } finally {
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
        }
    }

    private HttpURLConnection open(String path) throws IOException {
        if (path != null && path.contains("://")) {
            String protocol = path.substring(0, path.indexOf("://") + 3);
            String rest = path.substring(path.indexOf("://") + 3);
            path = protocol + rest.replace("//", "/");
        }
        URL url = new URL(path);
        HttpURLConnection hc = (HttpURLConnection) url.openConnection();

        hc.setRequestMethod("GET");
        hc.setDoInput(true);
        hc.setRequestProperty("User-Agent", "Droidcord/1.0");
        hc.setRequestProperty("Authorization", token);
        hc.setRequestProperty("Accept", "image/*, */*");

        return hc;
    }
}
