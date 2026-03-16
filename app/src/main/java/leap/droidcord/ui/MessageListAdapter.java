package leap.droidcord.ui;

import android.content.Context;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.text.util.Linkify;
import android.text.method.LinkMovementMethod;
import android.widget.BaseAdapter;
import android.text.ClipboardManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;

import leap.droidcord.R;
import leap.droidcord.State;
import leap.droidcord.Util;
import leap.droidcord.data.Messages;
import leap.droidcord.model.Attachment;
import leap.droidcord.model.Embed;
import leap.droidcord.model.Message;

public class MessageListAdapter extends BaseAdapter {

    private Context context;
    private State s;
    private Messages messages;
    private Drawable defaultAvatar;
    // serve nothing other than preventing calculating the pixel size every time
    // the item is shown on-screen
    private int iconSize;
    private int replyIconSize;

    public MessageListAdapter(Context context, State s, Messages messages) {
        this.context = context;
        this.s = s;
        this.messages = messages;
        this.defaultAvatar = context.getResources().getDrawable(R.drawable.ic_launcher);

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        iconSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, metrics);
        replyIconSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, metrics);
    }

    public Messages getData() {
        return this.messages;
    }

    @Override
    public Object getItem(int position) {
        return messages.get(position);
    }

    @Override
    public int getCount() {
        return messages.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private static final int[] NAME_COLORS = {
        0xFFE91E63, // Pink
        0xFF9C27B0, // Purple
        0xFF673AB7, // Deep Purple
        0xFF3F51B5, // Indigo
        0xFF2196F3, // Blue
        0xFF03A9F4, // Light Blue
        0xFF00BCD4, // Cyan
        0xFF009688, // Teal
        0xFF4CAF50, // Green
        0xFF8BC34A, // Light Green
        0xFFCDDC39, // Lime
        0xFFFFEB3B, // Yellow
        0xFFFFC107, // Amber
        0xFFFF9800, // Orange
        0xFFFF5722, // Deep Orange
        0xFF795548, // Brown
        0xFF607D8B  // Blue Gray
    };

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        Message message = (Message) getItem(position);

        if (convertView == null) {
            LayoutInflater layoutInflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = layoutInflater.inflate(R.layout.message, null);

            viewHolder = new ViewHolder(convertView);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        if (message.isStatus) {
            viewHolder.msg.setVisibility(View.GONE);
            viewHolder.status.setVisibility(View.VISIBLE);

            // TODO: integrate GuildInformation with status messages
            SpannableStringBuilder sb = new SpannableStringBuilder(
                    message.author.name + " " + message.content);
            sb.setSpan(new StyleSpan(Typeface.BOLD), 0,
                    message.author.name.length(),
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            viewHolder.statusText.setText(sb);
            viewHolder.statusTimestamp.setText(message.timestamp);
            viewHolder.root.setGravity(Gravity.CENTER_HORIZONTAL);
        } else {
            viewHolder.msg.setVisibility(View.VISIBLE);
            viewHolder.status.setVisibility(View.GONE);

            boolean isMe = (message.author.id == s.myUserId);
            viewHolder.root.setGravity(isMe ? Gravity.RIGHT : Gravity.LEFT);

            // Use spacers for wrapping logic
            viewHolder.spacerLeft.setVisibility(isMe ? View.VISIBLE : View.GONE);
            viewHolder.spacerRight.setVisibility(isMe ? View.GONE : View.VISIBLE);

            if (isMe) {
                if (viewHolder.msg.getChildAt(0) == viewHolder.spacerLeft) {
                    // Current order: [SL] [A] [B] [SR]
                    // Change to: [SL] [B] [A] [SR]
                    if (viewHolder.msg.getChildAt(1) == viewHolder.avatar) {
                        viewHolder.msg.removeView(viewHolder.avatar);
                        viewHolder.msg.addView(viewHolder.avatar, 2);
                    }
                }
                
                LinearLayout.LayoutParams bodyParams = (LinearLayout.LayoutParams) viewHolder.body.getLayoutParams();
                bodyParams.leftMargin = 0;
                bodyParams.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, context.getResources().getDisplayMetrics());
                viewHolder.body.setLayoutParams(bodyParams);
                viewHolder.body.setBackgroundResource(R.drawable.bubble_sent);

                viewHolder.author.setGravity(Gravity.RIGHT);
                viewHolder.content.setGravity(Gravity.RIGHT);
                viewHolder.metadata.setGravity(Gravity.RIGHT);
            } else {
                if (viewHolder.msg.getChildAt(0) == viewHolder.spacerLeft) {
                    // Ensure [A] is before [B]
                    if (viewHolder.msg.getChildAt(1) == viewHolder.body) {
                        viewHolder.msg.removeView(viewHolder.body);
                        viewHolder.msg.addView(viewHolder.body, 2);
                    }
                }
                
                LinearLayout.LayoutParams bodyParams = (LinearLayout.LayoutParams) viewHolder.body.getLayoutParams();
                bodyParams.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, context.getResources().getDisplayMetrics());
                bodyParams.rightMargin = 0;
                viewHolder.body.setLayoutParams(bodyParams);
                viewHolder.body.setBackgroundResource(R.drawable.bubble_recv);

                viewHolder.author.setGravity(Gravity.LEFT);
                viewHolder.content.setGravity(Gravity.LEFT);
                viewHolder.metadata.setGravity(Gravity.LEFT);
            }

            if (isMe) {
                viewHolder.avatar.setImageResource(R.drawable.ic_launcher);
                // Pink tint (Light Pink: #FFC0CB)
                viewHolder.avatar.setColorFilter(0xFFFFC0CB, PorterDuff.Mode.MULTIPLY);
            } else {
                viewHolder.avatar.clearColorFilter();
                s.icons.load(viewHolder.avatar, defaultAvatar, message.author, iconSize);
            }
            s.guildInformation.load(viewHolder.author, message.author);
            
            // Apply custom color logic
            if (isMe) {
                viewHolder.author.setTextColor(0xFFFFFFFF); // White for me
            } else if (!s.guildInformation.has(message.author)) {
                // If guild colors are not assigned yet, use ID-based hash
                int colorIndex = (int) (message.author.id % NAME_COLORS.length);
                if (colorIndex < 0) colorIndex += NAME_COLORS.length;
                viewHolder.author.setTextColor(NAME_COLORS[colorIndex]);
            }

            viewHolder.timestamp.setText(message.timestamp);

            viewHolder.body.setOnLongClickListener((View v) -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(R.string.message_actions_title);
                
                final String[] options;
                if (isMe) {
                    options = new String[] {
                        context.getString(R.string.action_copy),
                        context.getString(R.string.action_delete)
                    };
                } else {
                    options = new String[] {
                        context.getString(R.string.action_copy)
                    };
                }

                builder.setItems(options, (DialogInterface dialog, int which) -> {
                    String action = options[which];
                    if (action.equals(context.getString(R.string.action_copy))) {
                        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setText(message.content);
                        s.error(context.getString(R.string.msg_copied));
                    } else if (action.equals(context.getString(R.string.action_delete))) {
                        s.api.aDeleteMessage(message, () -> {
                            s.runOnUiThread(() -> {
                                notifyDataSetChanged();
                            });
                        });
                    }
                });
                builder.show();
                return true;
            });

            if (TextUtils.isEmpty(message.content))
                viewHolder.content.setVisibility(View.GONE);
            else {
                viewHolder.content.setVisibility(View.VISIBLE);
                viewHolder.content.setText(message.content);
                // Linkify first, then emoji on top
                viewHolder.content.setLinkTextColor(0xFF5865F2);
                Linkify.addLinks(viewHolder.content, Linkify.WEB_URLS);
                // Apply emoji AFTER linkify so spans aren't wiped
                EmojiCompat.apply(context, viewHolder.content);
                viewHolder.content.setMovementMethod(LinkMovementMethod.getInstance());
            }

            if (message.attachments != null && message.attachments.size() > 0) {
                viewHolder.attachments.removeAllViews();
                viewHolder.attachments.setVisibility(View.VISIBLE);
                
                final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                for (final Attachment attachment : message.attachments) {
                    if (attachment.supported) {
                        final View attachmentView = inflater.inflate(R.layout.attachment_item, null);
                        final ImageView attachmentIcon = (ImageView) attachmentView.findViewById(R.id.attachment_icon);
                        final ProgressBar attachmentProgress = (ProgressBar) attachmentView.findViewById(R.id.attachment_progress);
                        final ImageView attachmentImage = (ImageView) attachmentView.findViewById(R.id.attachment_image);
                        final ImageView attachmentSave = (ImageView) attachmentView.findViewById(R.id.attachment_save);

                        // Update view state based on attachment status
                        if (attachment.isLoaded) {
                            attachmentIcon.setVisibility(View.GONE);
                            attachmentProgress.setVisibility(View.GONE);
                            attachmentImage.setVisibility(View.VISIBLE);
                            attachmentSave.setVisibility(View.VISIBLE);
                            
                            s.attachments.load(attachmentImage, defaultAvatar, message, attachment);

                            // Open full-screen viewer on click
                            attachmentImage.setOnClickListener((View v2) -> {
                                android.content.Intent intent = new android.content.Intent(context, leap.droidcord.ImageViewerActivity.class);
                                intent.putExtra("url", attachment.previewUrl);
                                intent.putExtra("name", attachment.name);
                                context.startActivity(intent);
                            });
                        } else if (attachment.isLoading) {
                            attachmentIcon.setVisibility(View.GONE);
                            attachmentProgress.setVisibility(View.VISIBLE);
                            attachmentImage.setVisibility(View.GONE);
                            attachmentSave.setVisibility(View.GONE);
                        } else {
                            attachmentIcon.setVisibility(View.VISIBLE);
                            attachmentProgress.setVisibility(View.GONE);
                            attachmentImage.setVisibility(View.GONE);
                            attachmentSave.setVisibility(View.GONE);
                        }

                        // Trigger download
                        attachmentIcon.setOnClickListener((View v) -> {
                            attachmentIcon.setVisibility(View.GONE);
                            attachmentProgress.setVisibility(View.VISIBLE);
                            attachment.isLoading = true;
                            s.api.aFetchAttachment(attachment, () -> {
                                s.runOnUiThread(() -> {
                                    attachment.isLoading = false;
                                    attachment.isLoaded = true;
                                    // Refresh the list to show the image
                                    notifyDataSetChanged();
                                });
                            });
                        });

                        // Save image
                        attachmentSave.setOnClickListener((View v) -> {
                            Bitmap bitmap = s.attachments.get(message, attachment);
                            if (bitmap != null) {
                                s.api.saveImageToStorage(bitmap, attachment.name);
                            }
                        });

                        viewHolder.attachments.addView(attachmentView);
                    } else {
                        final View fileAttachmentView = inflater.inflate(R.layout.attachment_file_item, null);
                        final TextView fileName = (TextView) fileAttachmentView.findViewById(R.id.file_attachment_name);
                        final TextView fileMeta = (TextView) fileAttachmentView.findViewById(R.id.file_attachment_meta);

                        fileName.setText(attachment.name);

                        String meta = attachment.size > 0
                                ? Util.fileSizeToString(attachment.size)
                                : attachment.mimeType;
                        if (TextUtils.isEmpty(meta)) {
                            meta = "Archivo adjunto";
                        }
                        fileMeta.setText(meta);

                        fileAttachmentView.setOnClickListener((View v) -> {
                            fileAttachmentView.setEnabled(false);
                            s.api.aSaveAttachmentToStorage(attachment, () -> {
                                s.runOnUiThread(() -> {
                                    fileAttachmentView.setEnabled(true);
                                });
                            });
                        });

                        viewHolder.attachments.addView(fileAttachmentView);
                    }
                }
            } else {
                viewHolder.attachments.setVisibility(View.GONE);
            }

            // Render embed images (GIFs from links, thumbnails, etc.)
            if (message.embeds != null && message.embeds.size() > 0) {
                final LayoutInflater embedInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                for (final Embed embed : message.embeds) {
                    if (embed.image != null && embed.image.supported) {
                        final Attachment att = embed.image;
                        final View attachmentView = embedInflater.inflate(R.layout.attachment_item, null);
                        final ImageView attachmentIcon = (ImageView) attachmentView.findViewById(R.id.attachment_icon);
                        final ProgressBar attachmentProgress = (ProgressBar) attachmentView.findViewById(R.id.attachment_progress);
                        final ImageView attachmentImage = (ImageView) attachmentView.findViewById(R.id.attachment_image);
                        final ImageView attachmentSave = (ImageView) attachmentView.findViewById(R.id.attachment_save);

                        if (att.isLoaded) {
                            attachmentIcon.setVisibility(View.GONE);
                            attachmentProgress.setVisibility(View.GONE);
                            attachmentImage.setVisibility(View.VISIBLE);
                            attachmentSave.setVisibility(View.VISIBLE);
                            s.attachments.load(attachmentImage, defaultAvatar, message, att);
                            attachmentImage.setOnClickListener((View v2) -> {
                                android.content.Intent intent = new android.content.Intent(context, leap.droidcord.ImageViewerActivity.class);
                                intent.putExtra("url", att.previewUrl);
                                intent.putExtra("name", att.name != null ? att.name : "embed_image.png");
                                context.startActivity(intent);
                            });
                        } else if (att.isLoading) {
                            attachmentIcon.setVisibility(View.GONE);
                            attachmentProgress.setVisibility(View.VISIBLE);
                            attachmentImage.setVisibility(View.GONE);
                            attachmentSave.setVisibility(View.GONE);
                        } else {
                            attachmentIcon.setVisibility(View.VISIBLE);
                            attachmentProgress.setVisibility(View.GONE);
                            attachmentImage.setVisibility(View.GONE);
                            attachmentSave.setVisibility(View.GONE);
                        }

                        attachmentIcon.setOnClickListener((View v) -> {
                            attachmentIcon.setVisibility(View.GONE);
                            attachmentProgress.setVisibility(View.VISIBLE);
                            att.isLoading = true;
                            s.api.aFetchAttachment(att, () -> {
                                s.runOnUiThread(() -> {
                                    att.isLoading = false;
                                    att.isLoaded = true;
                                    notifyDataSetChanged();
                                });
                            });
                        });

                        attachmentSave.setOnClickListener((View v) -> {
                            Bitmap bitmap = s.attachments.get(message, att);
                            if (bitmap != null) {
                                s.api.saveImageToStorage(bitmap, att.name != null ? att.name : "embed_image.png");
                            }
                        });

                        viewHolder.attachments.setVisibility(View.VISIBLE);
                        viewHolder.attachments.addView(attachmentView);
                    }
                }
            }

            if (!message.showAuthor && message.recipient == null) {
                viewHolder.metadata.setVisibility(View.GONE);
                viewHolder.avatar.getLayoutParams().height = 0;
            } else {
                viewHolder.metadata.setVisibility(View.VISIBLE);
                viewHolder.avatar.getLayoutParams().height = iconSize;
            }

            if (message.recipient != null) {
                viewHolder.reply.setVisibility(View.VISIBLE);
                s.icons.load(viewHolder.replyAvatar, defaultAvatar, message.recipient, replyIconSize);
                s.guildInformation.load(viewHolder.replyAuthor, message.recipient);
                viewHolder.replyContent.setText(message.refContent);
            } else {
                viewHolder.reply.setVisibility(View.GONE);
            }
        }

        return convertView;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    private static class ViewHolder {
        LinearLayout root;
        LinearLayout msg;
        LinearLayout body;
        LinearLayout metadata;
        TextView author;
        TextView timestamp;
        TextView content;
        ImageView avatar;
        LinearLayout attachments;

        View reply;
        TextView replyAuthor;
        TextView replyContent;
        ImageView replyAvatar;

        View status;
        TextView statusText;
        TextView statusTimestamp;

        public ViewHolder(View view) {
            root = (LinearLayout) view;
            msg = (LinearLayout) view.findViewById(R.id.message);
            body = (LinearLayout) view.findViewById(R.id.msg_body);
            metadata = (LinearLayout) view.findViewById(R.id.msg_metadata);
            author = (TextView) view.findViewById(R.id.msg_author);
            timestamp = (TextView) view.findViewById(R.id.msg_timestamp);
            content = (TextView) view.findViewById(R.id.msg_content);
            avatar = (ImageView) view.findViewById(R.id.msg_avatar);
            attachments = (LinearLayout) view.findViewById(R.id.msg_attachments);

            reply = view.findViewById(R.id.msg_reply);
            replyAuthor = (TextView) view.findViewById(R.id.reply_author);
            replyContent = (TextView) view.findViewById(R.id.reply_content);
            replyAvatar = (ImageView) view.findViewById(R.id.reply_avatar);

            status = view.findViewById(R.id.status);
            statusText = (TextView) view.findViewById(R.id.status_text);
            statusTimestamp = (TextView) view
                    .findViewById(R.id.status_timestamp);
            
            spacerLeft = view.findViewById(R.id.msg_spacer_left);
            spacerRight = view.findViewById(R.id.msg_spacer_right);
        }

        View spacerLeft;
        View spacerRight;
    }
}
