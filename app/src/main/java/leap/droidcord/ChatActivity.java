package leap.droidcord;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import leap.droidcord.model.Message;
import leap.droidcord.ui.MessageListAdapter;

public class ChatActivity extends Activity implements OnScrollListener {
    private static final int PICK_FILE_REQUEST = 1;
    private static final long MAX_ATTACHMENT_SIZE_BYTES = 8L * 1024L * 1024L;
    private static final long REALTIME_REFRESH_INTERVAL_MS = 3000L;
    private State s;
    private Context context;
    private EditText mMsgComposer;
    private Button mMsgSend;
    private ImageButton mMsgFile;
    private boolean isLoadingMore = false;
    private boolean isRefreshingNewMessages = false;
    private boolean realtimeUpdatesEnabled = false;
    private boolean canLoadMore = true;
    private final Runnable realtimeRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshNewMessages();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.activity_chat);

        s = MainActivity.s;
        context = this;
        s.channelIsOpen = true;

        s.messagesView = (ListView) findViewById(R.id.messages);
        s.messagesView.setOnScrollListener(this);
        mMsgComposer = (EditText) findViewById(R.id.msg_composer);
        mMsgSend = (Button) findViewById(R.id.msg_send);
        mMsgFile = (ImageButton) findViewById(R.id.msg_file);

        if (s.isDM) {
            setTitle("@" + s.selectedDm.toString());
            mMsgComposer.setHint(getResources().getString(
                    R.string.msg_composer_hint, "@" + s.selectedDm.toString()));
        } else {
            setTitle(s.selectedChannel.toString());
            mMsgComposer.setHint(getResources().getString(
                    R.string.msg_composer_hint, s.selectedChannel.toString()));
        }

        showProgress(true);

        s.api.aFetchMessages(0, 0, () -> {
            s.messagesAdapter = new MessageListAdapter(context, s, s.messages);
            s.runOnUiThread(() -> {
                s.messagesView.setAdapter(s.messagesAdapter);
                s.messagesView.setSelection(s.messagesAdapter.getCount() - 1);
                showProgress(false);

                // Pre-fetch avatars in background
                for (int i = 0; i < s.messages.size(); i++) {
                    s.icons.preFetch(s.messages.get(i).author, 64);
                }

                startRealtimeUpdates();
            });
        });

        mMsgSend.setOnClickListener((View v) -> {
            try {
                s.sendMessage = mMsgComposer.getText().toString();
                s.sendReference = 0;
                s.sendPing = false;
                s.api.aSendMessage(() -> s.runOnUiThread(this::refreshNewMessagesNow));
                mMsgComposer.setText("");
            } catch (Exception e) {
                s.error("Error sending mesage: " + e.getMessage());
                e.printStackTrace();
            }
        });

        mMsgFile.setOnClickListener((View v) -> openFilePicker());
    }

    @Override
    protected void onResume() {
        super.onResume();
        s.channelIsOpen = true;
        startRealtimeUpdates();
    }

    @Override
    protected void onPause() {
        stopRealtimeUpdates();
        s.channelIsOpen = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopRealtimeUpdates();
        super.onDestroy();
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (!isLoadingMore && canLoadMore && firstVisibleItem == 0 && totalItemCount > 0) {
            // Check if we are actually at the top (not just first item visible)
            View firstChild = view.getChildAt(0);
            if (firstChild != null && firstChild.getTop() == 0) {
                loadMoreMessages();
            }
        }
    }

    private void loadMoreMessages() {
        if (s.messages.size() == 0) return;

        isLoadingMore = true;
        showProgress(true);

        Message firstMsg = s.messages.get(0);
        final long firstId = firstMsg.id;
        final int oldSize = s.messages.size();

        s.api.aFetchMessages(firstId, 0, () -> {
            s.runOnUiThread(() -> {
                int newSize = s.messages.size();
                int itemsAdded = newSize - oldSize;

                if (itemsAdded == 0) {
                    canLoadMore = false;
                } else {
                    s.messagesAdapter.notifyDataSetChanged();
                    // Maintain scroll position:
                    // Set selection to the item that was previously at the top
                    s.messagesView.setSelection(itemsAdded);
                }

                isLoadingMore = false;
                showProgress(false);
            });
        });
    }

    private void startRealtimeUpdates() {
        if (s == null || s.handler == null) {
            return;
        }
        realtimeUpdatesEnabled = true;
        s.handler.removeCallbacks(realtimeRefreshRunnable);
        s.handler.postDelayed(realtimeRefreshRunnable, REALTIME_REFRESH_INTERVAL_MS);
    }

    private void stopRealtimeUpdates() {
        if (s == null || s.handler == null) {
            return;
        }
        realtimeUpdatesEnabled = false;
        s.handler.removeCallbacks(realtimeRefreshRunnable);
        isRefreshingNewMessages = false;
    }

    private void refreshNewMessagesNow() {
        if (s == null || s.handler == null) {
            return;
        }
        s.handler.removeCallbacks(realtimeRefreshRunnable);
        refreshNewMessages();
    }

    private void refreshNewMessages() {
        if (!realtimeUpdatesEnabled) {
            return;
        }

        if (isFinishing() || s == null || s.messagesView == null || s.messagesAdapter == null) {
            if (realtimeUpdatesEnabled) {
                startRealtimeUpdates();
            }
            return;
        }

        if (isLoadingMore || isRefreshingNewMessages) {
            if (realtimeUpdatesEnabled) {
                startRealtimeUpdates();
            }
            return;
        }

        isRefreshingNewMessages = true;

        final int oldSize = s.messages.size();
        final long lastMessageId = oldSize == 0 ? 0L : s.messages.get(oldSize - 1).id;
        final boolean shouldStickToBottom = shouldStickToBottom();

        s.api.aFetchMessages(0, lastMessageId, () -> s.runOnUiThread(() -> {
            try {
                int newSize = s.messages.size();
                if (newSize > oldSize) {
                    prefetchAvatars(oldSize, newSize);
                    s.messagesAdapter.notifyDataSetChanged();

                    if (shouldStickToBottom) {
                        s.messagesView.setSelection(s.messagesAdapter.getCount() - 1);
                    }
                }
            } finally {
                isRefreshingNewMessages = false;
                if (realtimeUpdatesEnabled) {
                    startRealtimeUpdates();
                }
            }
        }));
    }

    private boolean shouldStickToBottom() {
        if (s.messagesView == null || s.messagesAdapter == null) {
            return true;
        }

        int count = s.messagesAdapter.getCount();
        if (count == 0) {
            return true;
        }

        return s.messagesView.getLastVisiblePosition() >= count - 2;
    }

    private void prefetchAvatars(int startIndexInclusive, int endIndexExclusive) {
        for (int i = startIndexInclusive; i < endIndexExclusive; i++) {
            Message message = s.messages.get(i);
            if (message != null) {
                s.icons.preFetch(message.author, 64);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            Log.d("ChatActivity", "Selected URI: " + uri.toString());

            try {
                AttachmentMeta meta = resolveAttachmentMeta(uri);
                if (meta.size > MAX_ATTACHMENT_SIZE_BYTES) {
                    s.error(getString(R.string.error_file_too_large));
                    return;
                }

                File uploadFile = copyUriToCache(uri, meta.name);
                uploadAttachment(uploadFile, meta.name);
            } catch (AttachmentTooLargeException e) {
                s.error(getString(R.string.error_file_too_large));
            } catch (Exception e) {
                Log.e("ChatActivity", "Error preparing selected file", e);
                s.error(getString(R.string.error_file_open));
            }
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent,
                    getString(R.string.choose_file_manager)), PICK_FILE_REQUEST);
        } catch (ActivityNotFoundException e) {
            s.error(getString(R.string.error_no_file_manager));
        }
    }

    private void uploadAttachment(final File file, final String displayName) {
        showProgress(true);

        s.api.aSendAttachment(file.getAbsolutePath(), displayName, () -> {
            if (!file.delete()) {
                Log.w("ChatActivity", "Could not delete temp file: " + file.getAbsolutePath());
            }

            s.runOnUiThread(() -> {
                showProgress(false);
                s.api.aFetchMessages(0, 0, () -> {
                    s.runOnUiThread(() -> {
                        s.messagesAdapter.notifyDataSetChanged();
                        s.messagesView.setSelection(s.messagesAdapter.getCount() - 1);
                    });
                });
            });
        });
    }

    private AttachmentMeta resolveAttachmentMeta(Uri uri) {
        String name = null;
        long size = -1L;
        Cursor cursor = null;

        try {
            String[] projection = {
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE
            };
            cursor = getContentResolver().query(uri, projection, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex);
                }

                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            Log.w("ChatActivity", "Could not read metadata for URI: " + uri, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (name == null || name.trim().length() == 0) {
            String segment = uri.getLastPathSegment();
            name = (segment == null || segment.length() == 0)
                    ? "attachment.bin"
                    : segment;
        }

        return new AttachmentMeta(name, size);
    }

    private File copyUriToCache(Uri uri, String displayName) throws Exception {
        File uploadDir = new File(getCacheDir(), "uploads");
        if (!uploadDir.exists() && !uploadDir.mkdirs()) {
            throw new Exception("Could not create upload cache directory");
        }

        String safeName = sanitizeFileName(displayName);
        File outputFile = File.createTempFile("upload_", "_" + safeName, uploadDir);
        InputStream in = null;
        OutputStream out = null;
        long total = 0L;

        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) {
                throw new Exception("Could not open selected file");
            }

            out = new FileOutputStream(outputFile);
            byte[] buffer = new byte[8192];
            int read;

            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_ATTACHMENT_SIZE_BYTES) {
                    throw new AttachmentTooLargeException();
                }
                out.write(buffer, 0, read);
            }
            out.flush();
        } catch (AttachmentTooLargeException e) {
            outputFile.delete();
            throw e;
        } catch (Exception e) {
            outputFile.delete();
            throw e;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }

        return outputFile;
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "attachment.bin";
        }
        String sanitized = fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (sanitized.length() == 0) {
            return "attachment.bin";
        }
        return sanitized;
    }

    private static class AttachmentMeta {
        final String name;
        final long size;

        AttachmentMeta(String name, long size) {
            this.name = name;
            this.size = size;
        }
    }

    private static class AttachmentTooLargeException extends Exception {
    }

    private void showProgress(final boolean show) {
        this.setProgressBarVisibility(show);
        this.setProgressBarIndeterminate(show);
    }
}
