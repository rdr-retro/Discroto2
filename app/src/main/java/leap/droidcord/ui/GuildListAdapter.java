package leap.droidcord.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Vector;

import leap.droidcord.R;
import leap.droidcord.State;
import leap.droidcord.model.Channel;
import leap.droidcord.model.Guild;
import leap.droidcord.model.GuildMember;
import leap.droidcord.model.Role;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class GuildListAdapter extends BaseExpandableListAdapter {

    private Activity activity;
    private Context context;
    private State s;
    private Vector<Guild> guilds;
    private Drawable defaultAvatar;
    private int iconSize;
    private int pinnedIconSize;
    private int normalItemHeight;
    private int pinnedItemHeight;
    private int normalPaddingLeft;
    private int pinnedPaddingLeft;
    private int normalPaddingRight;
    private int pinnedPaddingRight;
    private int normalPaddingVertical;
    private int pinnedPaddingVertical;
    private float normalTextSize;
    private float pinnedTextSize;
    private Vector<Long> pinnedGuildIds = new Vector<Long>();

    public GuildListAdapter(Activity activity, Context context, State s, Vector<Guild> guilds) {
        this.activity = activity;
        this.context = context;
        this.s = s;
        this.guilds = guilds;
        this.defaultAvatar = context.getResources().getDrawable(R.drawable.ic_launcher);

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        iconSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, metrics);
        pinnedIconSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, metrics);
        normalItemHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, metrics);
        pinnedItemHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 68, metrics);
        normalPaddingLeft = resolveGroupPaddingLeft(metrics);
        pinnedPaddingLeft = normalPaddingLeft;
        normalPaddingRight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, metrics);
        pinnedPaddingRight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, metrics);
        normalPaddingVertical = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, metrics);
        pinnedPaddingVertical = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, metrics);
        normalTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18, metrics);
        pinnedTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 22, metrics);
    }

    private Vector<Channel> getChannelsFor(final Guild guild) {
        if (guild.channels != null)
            return guild.channels;

        showProgress(true);
        s.executor.execute(() -> {
            try {
                if (guild.roles == null)
                    guild.roles = Role.parseRoles(JSON.getArray(s.http.get("/guilds/" + guild.id + "/roles?droidcord=1")));

                if (guild.me == null)
                    guild.me = new GuildMember(s, guild, JSON.getObject(s.http.get("/guilds/" + guild.id + "/members/" + s.myUserId)));

                guild.channels = Channel.parseChannels(s, guild, JSON.getArray(s.http.get("/guilds/" + guild.id + "/channels")));

                s.runOnUiThread(() -> {
                    notifyDataSetChanged();
                    showProgress(false);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return new Vector<Channel>();
    }

    @Override
    public Object getChild(int position, int childPosition) {
        return getChannelsFor(guilds.get(position)).get(childPosition);
    }

    @Override
    public long getChildId(int position, int childPosition) {
        return childPosition;
    }

    @Override
    public View getChildView(int position, final int childPosition,
                             boolean isLastChild, View convertView, ViewGroup parent) {
        ChildViewHolder viewHolder;
        final Channel channel = (Channel) getChild(position, childPosition);

        if (convertView == null) {
            LayoutInflater layoutInflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = layoutInflater.inflate(R.layout.channel_list_item, null);

            viewHolder = new ChildViewHolder();
            viewHolder.name = (TextView) convertView.findViewById(R.id.channel_item_name);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ChildViewHolder) convertView.getTag();
        }

        viewHolder.name.setText(channel.toString());

        return convertView;
    }

    @Override
    public int getChildrenCount(int position) {
        return getChannelsFor(guilds.get(position)).size();
    }

    @Override
    public Object getGroup(int position) {
        return guilds.get(position);
    }

    @Override
    public int getGroupCount() {
        return guilds.size();
    }

    @Override
    public long getGroupId(int position) {
        return position;
    }

    @Override
    public View getGroupView(int position, boolean isExpanded,
                             View convertView, ViewGroup parent) {
        GroupViewHolder viewHolder;
        final Guild guild = (Guild) getGroup(position);

        if (convertView == null) {
            LayoutInflater layoutInflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = layoutInflater.inflate(R.layout.guild_list_item, null);

            viewHolder = new GroupViewHolder();
            viewHolder.icon = (ImageView) convertView.findViewById(R.id.guild_item_icon);
            viewHolder.name = (TextView) convertView.findViewById(R.id.guild_item_name);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (GroupViewHolder) convertView.getTag();
        }

        boolean pinned = isPinnedGuild(guild.id);
        s.icons.load(viewHolder.icon, defaultAvatar, guild, pinned ? pinnedIconSize : iconSize);
        viewHolder.name.setText(guild.name);
        applyPinnedStyle(convertView, viewHolder, pinned);

        return convertView;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isChildSelectable(int position, int childPosition) {
        return true;
    }

    private void showProgress(final boolean show) {
        ((Activity) context).setProgressBarVisibility(show);
        ((Activity) context).setProgressBarIndeterminate(show);
    }

    private static class ChildViewHolder {
        TextView name;
    }

    private static class GroupViewHolder {
        ImageView icon;
        TextView name;
    }

    public void setPinnedGuildIds(Vector<Long> pinnedGuildIds) {
        this.pinnedGuildIds.removeAllElements();
        if (pinnedGuildIds == null) {
            return;
        }

        for (int i = 0; i < pinnedGuildIds.size(); i++) {
            Long guildId = pinnedGuildIds.get(i);
            if (guildId != null && !this.pinnedGuildIds.contains(guildId)) {
                this.pinnedGuildIds.add(guildId);
            }
        }
    }

    private void applyPinnedStyle(View root, GroupViewHolder viewHolder, boolean pinned) {
        root.setBackgroundResource(pinned ? R.drawable.pinned_item_background : android.R.color.transparent);
        root.setMinimumHeight(pinned ? pinnedItemHeight : normalItemHeight);
        root.setPadding(
                pinned ? pinnedPaddingLeft : normalPaddingLeft,
                pinned ? pinnedPaddingVertical : normalPaddingVertical,
                pinned ? pinnedPaddingRight : normalPaddingRight,
                pinned ? pinnedPaddingVertical : normalPaddingVertical
        );

        ViewGroup.LayoutParams iconParams = viewHolder.icon.getLayoutParams();
        int iconDimen = pinned ? 44 : 32;
        float density = context.getResources().getDisplayMetrics().density;
        iconParams.width = (int) (iconDimen * density);
        iconParams.height = (int) (iconDimen * density);
        viewHolder.icon.setLayoutParams(iconParams);

        viewHolder.name.setTextSize(TypedValue.COMPLEX_UNIT_PX, pinned ? pinnedTextSize : normalTextSize);
        viewHolder.name.setTypeface(Typeface.DEFAULT, pinned ? Typeface.BOLD : Typeface.NORMAL);
    }

    private int resolveGroupPaddingLeft(DisplayMetrics metrics) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.expandableListPreferredItemPaddingLeft, value, true)) {
            return TypedValue.complexToDimensionPixelSize(value.data, metrics);
        }
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, metrics);
    }

    private boolean isPinnedGuild(long guildId) {
        for (int i = 0; i < pinnedGuildIds.size(); i++) {
            Long pinnedGuildId = pinnedGuildIds.get(i);
            if (pinnedGuildId != null && pinnedGuildId.longValue() == guildId) {
                return true;
            }
        }
        return false;
    }
}
