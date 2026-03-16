package leap.droidcord.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Vector;

import leap.droidcord.R;
import leap.droidcord.State;
import leap.droidcord.model.DirectMessage;

public class DMListAdapter extends BaseAdapter {

    private Context context;
    private State s;
    private Vector<DirectMessage> dms;
    private Drawable defaultAvatar;
    private int iconSize;
    private int pinnedIconSize;
    private int normalItemHeight;
    private int pinnedItemHeight;
    private int normalPaddingHorizontal;
    private int pinnedPaddingHorizontal;
    private int normalPaddingVertical;
    private int pinnedPaddingVertical;
    private float normalTextSize;
    private float pinnedTextSize;
    private long pinnedDmId;

    public DMListAdapter(Context context, State s, Vector<DirectMessage> dms) {
        this.context = context;
        this.s = s;
        this.dms = dms;
        this.defaultAvatar = context.getResources().getDrawable(R.drawable.ic_launcher);

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        iconSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, metrics);
        pinnedIconSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64, metrics);
        normalItemHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, metrics);
        pinnedItemHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 68, metrics);
        normalPaddingHorizontal = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, metrics);
        pinnedPaddingHorizontal = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, metrics);
        normalPaddingVertical = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, metrics);
        pinnedPaddingVertical = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, metrics);
        normalTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18, metrics);
        pinnedTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 22, metrics);
    }

    @Override
    public Object getItem(int position) {
        return dms.get(position);
    }

    @Override
    public int getCount() {
        return dms.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        final DirectMessage dm = (DirectMessage) getItem(position);

        if (convertView == null) {
            LayoutInflater layoutInflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = layoutInflater.inflate(R.layout.dm_list_item, null);

            viewHolder = new ViewHolder();
            viewHolder.icon = (ImageView) convertView.findViewById(R.id.dm_item_icon);
            viewHolder.name = (TextView) convertView.findViewById(R.id.dm_item_name);
            viewHolder.status = (TextView) convertView.findViewById(R.id.dm_item_status);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        boolean pinned = dm.id == pinnedDmId;
        s.icons.load(viewHolder.icon, defaultAvatar, dm, pinned ? pinnedIconSize : iconSize);
        viewHolder.name.setText(dm.name);
        // TODO: implement statuses, make them invisible for now
        viewHolder.status.setVisibility(View.GONE);
        applyPinnedStyle(convertView, viewHolder, pinned);

        return convertView;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    private static class ViewHolder {
        ImageView icon;
        TextView name;
        TextView status;
    }

    public void setPinnedDmId(long pinnedDmId) {
        this.pinnedDmId = pinnedDmId;
    }

    private void applyPinnedStyle(View root, ViewHolder viewHolder, boolean pinned) {
        root.setBackgroundResource(pinned ? R.drawable.pinned_item_background : android.R.color.transparent);
        root.setMinimumHeight(pinned ? pinnedItemHeight : normalItemHeight);
        root.setPadding(
                pinned ? pinnedPaddingHorizontal : normalPaddingHorizontal,
                pinned ? pinnedPaddingVertical : normalPaddingVertical,
                pinned ? pinnedPaddingHorizontal : normalPaddingHorizontal,
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
}
