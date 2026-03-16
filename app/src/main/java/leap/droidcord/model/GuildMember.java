package leap.droidcord.model;

import java.util.Vector;

import cc.nnproject.json.JSONObject;

import leap.droidcord.State;

public class GuildMember extends User {
    String username;
    String name;
    String nickname;
    Vector<Role> roles;
    long permissions;

    public GuildMember(State s, Guild g, JSONObject data) {
        super(s, data.getObject("user"));
        username = data.getString("username", null);
        name = data.getString("global_name", username);

        if (data.has("nick"))
            nickname = data.getString("nick", null);

        try {
            if (data.has("roles"))
                roles = Role.parseGuildMemberRoles(g, data.getArray("roles"));
        } catch (Exception e) {
            e.printStackTrace();
            roles = new Vector<Role>();
        }

        if (s.iconType == State.ICON_TYPE_NONE)
            return;

        iconHash = data.getString("avatar", null);
    }

    public String getIconHash() {
        return iconHash;
    }

    public void iconLoaded(State s) {
    }

    public void largeIconLoaded(State s) {
        iconLoaded(s);
    }
}
