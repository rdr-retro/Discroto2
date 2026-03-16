package leap.droidcord.model;

import cc.nnproject.json.JSONObject;

public class Embed {
    public String title;
    public String description;
    public Attachment image;

    public Embed(leap.droidcord.State s, JSONObject data) {
        title = data.getString("title", null);
        description = data.getString("description", null);
        
        JSONObject img = null;
        try {
            if (data.has("image")) img = data.getObject("image");
            else if (data.has("thumbnail")) img = data.getObject("thumbnail");
        } catch (Exception e) {}
        
        if (img != null && img.has("proxy_url")) {
            try {
                // Set default filename/content_type if missing
                if (!img.has("filename")) {
                    String purl = img.getString("proxy_url");
                    String fname = "embed_image.png";
                    if (purl.contains(".gif") || (img.has("content_type") && img.getString("content_type", "").contains("gif"))) {
                        fname = "embed_image.gif";
                        if (!img.has("content_type")) img.put("content_type", "image/gif");
                    }
                    img.put("filename", fname);
                }
                if (!img.has("content_type")) img.put("content_type", "image/png");
                image = new Attachment(s, img);
            } catch (Exception e) {
                image = null;
            }
        }
    }
}