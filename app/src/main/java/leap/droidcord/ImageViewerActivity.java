package leap.droidcord;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;

public class ImageViewerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_image_viewer);

        final String url = getIntent().getStringExtra("url");
        final String filename = getIntent().getStringExtra("name");
        
        if (url == null) {
            finish();
            return;
        }

        WebView webView = (WebView) findViewById(R.id.image_webview);
        WebSettings settings = webView.getSettings();
        
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        String html = "<html><head><style>body{margin:0;padding:0;background:#000;display:flex;justify-content:center;align-items:center;height:100vh;}img{max-width:100%;max-height:100%;object-fit:contain;}</style></head><body><img src=\"" + url + "\"></body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);

        ImageView saveButton = (ImageView) findViewById(R.id.viewer_save);
        saveButton.setOnClickListener((View v) -> {
            // Retrieve image from cache and save
            if (MainActivity.s != null) {
                Bitmap bitmap = MainActivity.s.attachments.get(null, new leap.droidcord.model.Attachment(MainActivity.s, url));
                if (bitmap != null) {
                    MainActivity.s.api.saveImageToStorage(bitmap, filename != null ? filename : "image.png");
                } else {
                    MainActivity.s.error("Imagen no encontrada en cache");
                }
            }
        });
    }
}
