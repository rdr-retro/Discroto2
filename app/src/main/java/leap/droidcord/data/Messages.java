package leap.droidcord.data;

import java.util.Vector;

import leap.droidcord.model.Message;

public class Messages {
    private Vector<Message> messages;

    public Messages() {
        this.messages = new Vector<Message>();
    }

    public Message get(int index) {
        if (index < 0 || index >= messages.size())
            return null;
        return messages.get(index);
    }

    public void add(Message message) {
        if (message != null && indexOf(message.id) == -1)
            messages.add(message);
    }

    public void prepend(Message message) {
        if (message != null && indexOf(message.id) == -1)
            messages.insertElementAt(message, 0);
    }

    public void cluster() {
        if (messages.size() > 1) {
            Message previous = null;
            long clusterStart = 0;
            for (int i = 0; i < messages.size(); i++) {
                Message message = (Message) messages.get(i);
                message.showAuthor = message.shouldShowAuthor(previous, clusterStart);
                if (message.showAuthor)
                    clusterStart = message.id;
                previous = message;
            }
        }
    }

    public void reset() {
        messages.clear();
    }

    public void remove(Message message) {
        if (message != null)
            messages.removeElement(message);
    }

    public int size() {
        return messages.size();
    }

    private int indexOf(long messageId) {
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message != null && message.id == messageId) {
                return i;
            }
        }
        return -1;
    }
}
