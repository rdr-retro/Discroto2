package leap.droidcord;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ListView;
import android.widget.Toast;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;

import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import leap.droidcord.data.API;
import leap.droidcord.data.FileCache;
import leap.droidcord.data.GuildInformation;
import leap.droidcord.data.Icons;
import leap.droidcord.data.Attachments;
import leap.droidcord.data.Messages;
import leap.droidcord.model.Channel;
import leap.droidcord.model.DirectMessage;
import leap.droidcord.model.Guild;
import leap.droidcord.ui.MessageListAdapter;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONObject;

public class State {
    public static final int ICON_TYPE_NONE = 0;
    public static final int ICON_TYPE_SQUARE = 1;
    public static final int ICON_TYPE_CIRCLE = 2;
    public static final int ICON_TYPE_CIRCLE_HQ = 3;

    public static final int TOKEN_TYPE_HEADER = 0;
    public static final int TOKEN_TYPE_JSON = 1;
    public static final int TOKEN_TYPE_QUERY = 2;

    public boolean use12hTime;
    public boolean useGateway;
    public int messageLoadCount;
    public boolean useJpeg;
    public int attachmentSize;
    public int iconType;
    public boolean autoReConnect;
    public boolean showMenuIcons;
    public int tokenType;
    public boolean useNameColors;
    public boolean showRefMessage;

    public Context c;

    public HTTP http;
    public API api;
    public GatewayThread gateway;
    public String cdn;

    public long myUserId;
    public boolean isLiteProxy;

    public Icons icons;
    public FileCache iconCache;
    public Attachments attachments;
    public GuildInformation guildInformation;
    public UnreadManager unreads;

    public Vector<Guild> guilds;
    public Guild selectedGuild;
    public Vector<Guild> subscribedGuilds;

    public Vector<Channel> channels;
    public Channel selectedChannel;
    public boolean channelIsOpen;

    public Messages messages;
    public Vector<String> typingUsers;
    public Vector<Long> typingUserIDs;

    public MessageListAdapter messagesAdapter;
    public ListView messagesView;

    // Parameters for message/reply sending
    public String sendMessage;
    public long sendReference; // ID of the message the user is replying to
    public boolean sendPing;

    public boolean isDM;
    public Vector<DirectMessage> directMessages;
    public DirectMessage selectedDm;

    public ExecutorService executor;
    public Handler handler;

    public State(Context c) {
        this.c = c;
        this.executor = Executors.newFixedThreadPool(4);
        this.handler = new Handler(Looper.getMainLooper());
        this.api = new API(this);
        this.subscribedGuilds = new Vector<Guild>();
        this.icons = new Icons(this);
        this.iconCache = new FileCache(c);
        this.attachments = new Attachments(this);
        this.guildInformation = new GuildInformation(this);
        this.unreads = new UnreadManager(this, c);
        this.guilds = new Vector<Guild>();
        this.channels = new Vector<Channel>();
        this.directMessages = new Vector<DirectMessage>();
        this.messages = new Messages();
        this.typingUsers = new Vector<String>();
        this.typingUserIDs = new Vector<Long>();
        this.iconType = ICON_TYPE_SQUARE;
    }

    public void login(String api, String gateway, String cdn, String token) {
        this.cdn = cdn;
        this.http = new HTTP(this, api, token);

        try {
            String meJson = this.http.get("/users/@me");
            JSONObject me;
            if (meJson.startsWith("[")) {
                me = cc.nnproject.json.JSON.getArray(meJson).getObject(0);
            } else {
                me = cc.nnproject.json.JSON.getObject(meJson);
            }
            this.myUserId = Long.parseLong(me.getString("id"));
        } catch (Exception e) {
            this.error("Error getting user ID: " + e.getMessage());
            e.printStackTrace();
        }

        if (useGateway) {
            this.gateway = new GatewayThread(this, gateway, token);
            this.gateway.start();
        }
    }

    public void error(final String message) {
        runOnUiThread(() -> {
            try {
                Toast.makeText(c, "Error: " + message, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                System.err.println("Error: " + message);
            }
        });
    }

    public void toast(final String message) {
        runOnUiThread(() -> {
            try {
                Toast.makeText(c, message, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                System.out.println(message);
            }
        });
    }

    public boolean gatewayActive() {
        return gateway != null && gateway.isAlive();
    }

    public void updateUnreadIndicators(boolean isDM, long chId) {
        /*if (isDM) {
            if (dmSelector != null) dmSelector.update(chId);
		} else {
			if (channelSelector != null) channelSelector.update(chId);
			if (guildSelector != null) guildSelector.update();
		}*/
    }

    public void platformRequest(String url) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        c.startActivity(browserIntent);
    }

    public void runOnUiThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            handler.post(runnable);
        }
    }

    private static final int DOWNLOAD_NOTIFICATION_ID = 1001;

    public void updateDownloadNotification(final int count) {
        if (count <= 0) {
            cancelDownloadNotification();
            return;
        }

        runOnUiThread(new Runnable() {
            public void run() {
                NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                
                Notification notification = new Notification(
                        android.R.drawable.stat_sys_download,
                        "Descargando datos de perfil...",
                        System.currentTimeMillis()
                );

                Intent notificationIntent = new Intent(c, MainActivity.class);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent contentIntent = PendingIntent.getActivity(c, 0, notificationIntent, 0);

                // Use reflection to call setLatestEventInfo to avoid compilation error on newer SDKs
                try {
                    java.lang.reflect.Method method = notification.getClass().getMethod("setLatestEventInfo", Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
                    method.invoke(notification, c, "Droidcord", count + " descargas pendientes", contentIntent);
                } catch (Exception e) {
                    // Fallback or ignore if fails
                }
                
                notification.flags |= Notification.FLAG_ONGOING_EVENT;

                nm.notify(DOWNLOAD_NOTIFICATION_ID, notification);
            }
        });
    }

    public void cancelDownloadNotification() {
        runOnUiThread(new Runnable() {
            public void run() {
                NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                nm.cancel(DOWNLOAD_NOTIFICATION_ID);
            }
        });
    }
}
