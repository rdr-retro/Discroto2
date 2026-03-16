package leap.droidcord;

import android.app.AlertDialog;
import android.app.TabActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TabHost;
import android.widget.TabWidget;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import leap.droidcord.model.Channel;
import leap.droidcord.model.DirectMessage;
import leap.droidcord.model.Guild;
import leap.droidcord.ui.DMListAdapter;
import leap.droidcord.ui.GuildListAdapter;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONObject;

public class MainActivity extends TabActivity {
    private static final int MAX_PINNED_GUILDS = 4;
    private static final String PREF_PINNED_GUILD_ID = "pinnedGuildId";
    private static final String PREF_PINNED_GUILD_IDS = "pinnedGuildIds";
    private static final String PREF_PINNED_DM_ID = "pinnedDmId";

    public static State s;
    private Context mContext;
    private Vector<Long> pinnedGuildIds = new Vector<Long>();
    private long pinnedDmId;

    ExpandableListView mGuildsView;
    ExpandableListAdapter mGuildsAdapter;

    ListView mDmsView;
    ListAdapter mDmsAdapter;

    private class LoadInformationRunnable implements Runnable {
        private final AtomicInteger mLoadCount = new AtomicInteger(0);

        @Override
        public void run() {
            s.api.aFetchGuilds(() -> {
                movePinnedGuildsToTop();
                mGuildsAdapter = new GuildListAdapter(MainActivity.this, mContext, s, s.guilds);
                ((GuildListAdapter) mGuildsAdapter).setPinnedGuildIds(pinnedGuildIds);
                s.runOnUiThread(() -> {
                    mGuildsView.setAdapter(mGuildsAdapter);
                    if (mLoadCount.incrementAndGet() == 2)
                        showProgress(false);
                });
            });

            s.api.aFetchDirectMessages(() -> {
                movePinnedDmToTop();
                mDmsAdapter = new DMListAdapter(mContext, s, s.directMessages);
                ((DMListAdapter) mDmsAdapter).setPinnedDmId(pinnedDmId);
                s.runOnUiThread(() -> {
                    mDmsView.setAdapter(mDmsAdapter);
                    if (mLoadCount.incrementAndGet() == 2)
                        showProgress(false);
                });
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_PROGRESS);

        s = new State(this);
        mContext = this;

        TabHost tabHost = getTabHost();
        LayoutInflater.from(this).inflate(R.layout.activity_main, tabHost.getTabContentView(), true);
        tabHost.addTab(tabHost.newTabSpec("servers")
                .setIndicator("Servers", getResources().getDrawable(R.drawable.tab_imagen))
                .setContent(R.id.server_tab));
        tabHost.addTab(tabHost.newTabSpec("dm")
                .setIndicator("Direct Messages", getResources().getDrawable(R.drawable.tab_imagen))
                .setContent(R.id.dm_tab));
        tabHost.addTab(tabHost.newTabSpec("settings")
                .setIndicator("Settings", getResources().getDrawable(R.drawable.settings))
                .setContent(R.id.settings_tab));

        mGuildsView = (ExpandableListView) findViewById(R.id.servers);
        mDmsView = (ListView) findViewById(R.id.direct_messages);

        TabWidget tabWidget = tabHost.getTabWidget();
        int tabHeight = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 82, getResources().getDisplayMetrics());
        int iconSize = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 44, getResources().getDisplayMetrics());
        int iconOffset = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        int titleSpacing = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
        ViewGroup.LayoutParams tabWidgetParams = tabWidget.getLayoutParams();
        tabWidgetParams.height = tabHeight;
        tabWidget.setLayoutParams(tabWidgetParams);
        for (int i = 0; i < tabWidget.getChildCount(); i++) {
            View tab = tabWidget.getChildAt(i);
            ViewGroup.LayoutParams params = tab.getLayoutParams();
            params.height = tabHeight;
            tab.setLayoutParams(params);

            ImageView iconView = (ImageView) tab.findViewById(android.R.id.icon);
            if (iconView != null) {
                ViewGroup.MarginLayoutParams iconParams =
                        (ViewGroup.MarginLayoutParams) iconView.getLayoutParams();
                iconParams.width = iconSize;
                iconParams.height = iconSize;
                iconParams.topMargin = Math.max(0, iconParams.topMargin - iconOffset);
                iconView.setLayoutParams(iconParams);
                iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }

            TextView titleView = (TextView) tab.findViewById(android.R.id.title);
            if (titleView != null) {
                ViewGroup.MarginLayoutParams titleParams =
                        (ViewGroup.MarginLayoutParams) titleView.getLayoutParams();
                titleParams.topMargin += titleSpacing;
                titleView.setLayoutParams(titleParams);
            }
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        pinnedGuildIds = loadPinnedGuildIds(sp);
        pinnedDmId = sp.getLong(PREF_PINNED_DM_ID, 0L);
        if (TextUtils.isEmpty(sp.getString("token", null))) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        String apiUrl = sp.getString("api", null);
        String cdnUrl = sp.getString("cdn", null);
        boolean use_gateway = sp.getBoolean("useGateway", false);
        String gatewayUrl = sp.getString("gateway", null);
        String token = sp.getString("token", null);
        int token_type = sp.getInt("tokenType", 0);
        int msgLoadCount = sp.getInt("messageLoadCount", 0);

        try {
            s.useGateway = use_gateway;
            s.tokenType = token_type;
            s.messageLoadCount = msgLoadCount;
            s.login(apiUrl, gatewayUrl, cdnUrl, token);

            showProgress(true);
            s.executor.execute(new LoadInformationRunnable());
        } catch (Exception e) {
            e.printStackTrace();
        }

        mGuildsView.setOnChildClickListener((ExpandableListView parent, View v,
                                             int groupPosition, int childPosition,
                                             long id) -> {
            Intent intent = new Intent(mContext, ChatActivity.class);
            s.isDM = false;
            s.selectedDm = null;
            s.selectedGuild = (Guild) mGuildsAdapter.getGroup(groupPosition);
            s.selectedChannel = (Channel) mGuildsAdapter.getChild(groupPosition, childPosition);
            startActivity(intent);
            return true;
        });

        mGuildsView.setOnItemLongClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            long packedPosition = mGuildsView.getExpandableListPosition(position);
            int type = ExpandableListView.getPackedPositionType(packedPosition);
            if (type != ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
                return false;
            }

            int groupPosition = ExpandableListView.getPackedPositionGroup(packedPosition);
            Guild guild = (Guild) mGuildsAdapter.getGroup(groupPosition);
            showPinDialog(guild.name, () -> pinGuild(guild));
            return true;
        });

        mDmsView.setOnItemClickListener((AdapterView<?> parent, View v, int position,
                                         long id) -> {
            Intent intent = new Intent(mContext, ChatActivity.class);
            s.isDM = true;
            s.selectedDm = (DirectMessage) mDmsAdapter.getItem(position);
            s.selectedGuild = null;
            s.selectedChannel = null;
            startActivity(intent);
        });

        mDmsView.setOnItemLongClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            DirectMessage dm = (DirectMessage) mDmsAdapter.getItem(position);
            showPinDialog(dm.name, () -> pinDirectMessage(dm));
            return true;
        });
    }

    public void showProgress(final boolean show) {
        this.setProgressBarVisibility(show);
        this.setProgressBarIndeterminate(show);
    }

    private void showPinDialog(String title, Runnable action) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setItems(new CharSequence[]{getString(R.string.action_pin_to_top)}, (dialog, which) -> {
            if (which == 0) {
                action.run();
            }
        });
        builder.show();
    }

    private void pinGuild(Guild guild) {
        if (guild == null) {
            return;
        }

        removePinnedGuildId(guild.id);
        pinnedGuildIds.insertElementAt(guild.id, 0);
        while (pinnedGuildIds.size() > MAX_PINNED_GUILDS) {
            pinnedGuildIds.removeElementAt(pinnedGuildIds.size() - 1);
        }
        savePinnedGuildIds();
        movePinnedGuildsToTop();

        if (mGuildsAdapter instanceof GuildListAdapter) {
            GuildListAdapter adapter = (GuildListAdapter) mGuildsAdapter;
            adapter.setPinnedGuildIds(pinnedGuildIds);
            adapter.notifyDataSetChanged();
        }

        s.toast(getString(R.string.item_pinned_to_top));
    }

    private void pinDirectMessage(DirectMessage dm) {
        if (dm == null) {
            return;
        }

        pinnedDmId = dm.id;
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putLong(PREF_PINNED_DM_ID, pinnedDmId)
                .commit();
        movePinnedDmToTop();

        if (mDmsAdapter instanceof DMListAdapter) {
            DMListAdapter adapter = (DMListAdapter) mDmsAdapter;
            adapter.setPinnedDmId(pinnedDmId);
            adapter.notifyDataSetChanged();
        }

        s.toast(getString(R.string.item_pinned_to_top));
    }

    private Vector<Long> loadPinnedGuildIds(SharedPreferences preferences) {
        Vector<Long> ids = new Vector<Long>();
        String rawPinnedIds = preferences.getString(PREF_PINNED_GUILD_IDS, null);
        if (!TextUtils.isEmpty(rawPinnedIds)) {
            String[] parts = rawPinnedIds.split(",");
            for (int i = 0; i < parts.length && ids.size() < MAX_PINNED_GUILDS; i++) {
                String part = parts[i] != null ? parts[i].trim() : null;
                if (TextUtils.isEmpty(part)) {
                    continue;
                }
                try {
                    long id = Long.parseLong(part);
                    if (id > 0L && !ids.contains(Long.valueOf(id))) {
                        ids.add(Long.valueOf(id));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (ids.isEmpty()) {
            long legacyPinnedGuildId = preferences.getLong(PREF_PINNED_GUILD_ID, 0L);
            if (legacyPinnedGuildId > 0L) {
                ids.add(Long.valueOf(legacyPinnedGuildId));
            }
        }

        return ids;
    }

    private void savePinnedGuildIds() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pinnedGuildIds.size(); i++) {
            Long id = pinnedGuildIds.get(i);
            if (id == null || id.longValue() <= 0L) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id.longValue());
        }

        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putString(PREF_PINNED_GUILD_IDS, builder.toString())
                .remove(PREF_PINNED_GUILD_ID)
                .commit();
    }

    private void removePinnedGuildId(long guildId) {
        for (int i = pinnedGuildIds.size() - 1; i >= 0; i--) {
            Long currentId = pinnedGuildIds.get(i);
            if (currentId != null && currentId.longValue() == guildId) {
                pinnedGuildIds.removeElementAt(i);
            }
        }
    }

    private void movePinnedGuildsToTop() {
        if (pinnedGuildIds.isEmpty() || s.guilds == null) {
            return;
        }

        Vector<Long> validPinnedIds = new Vector<Long>();
        int insertPosition = 0;
        for (int pinnedIndex = 0; pinnedIndex < pinnedGuildIds.size(); pinnedIndex++) {
            long pinnedGuildId = pinnedGuildIds.get(pinnedIndex).longValue();
            for (int guildIndex = 0; guildIndex < s.guilds.size(); guildIndex++) {
                Guild guild = s.guilds.get(guildIndex);
                if (guild != null && guild.id == pinnedGuildId) {
                    if (guildIndex != insertPosition) {
                        s.guilds.removeElementAt(guildIndex);
                        s.guilds.insertElementAt(guild, insertPosition);
                    }
                    validPinnedIds.add(Long.valueOf(pinnedGuildId));
                    insertPosition++;
                    break;
                }
            }
        }

        if (validPinnedIds.size() != pinnedGuildIds.size()) {
            pinnedGuildIds = validPinnedIds;
            savePinnedGuildIds();
        }
    }

    private void movePinnedDmToTop() {
        if (pinnedDmId == 0L || s.directMessages == null) {
            return;
        }

        for (int i = 0; i < s.directMessages.size(); i++) {
            DirectMessage dm = s.directMessages.get(i);
            if (dm != null && dm.id == pinnedDmId) {
                if (i > 0) {
                    s.directMessages.removeElementAt(i);
                    s.directMessages.insertElementAt(dm, 0);
                }
                return;
            }
        }
    }
}
