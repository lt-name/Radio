package cn.wode490390.nukkit.radio.util;

import cn.nukkit.utils.Config;

/**
 * @author LT_Name
 */
public class PlayerSetting {

    private final String name;
    private final Config config;

    private boolean enabled;

    public PlayerSetting(String name, Config config) {
        this.name = name;
        this.config = config;

        this.enabled = config.getBoolean("enabled", true);
    }


    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void save() {
        config.set("enabled", this.enabled);
        config.save();
    }
}
