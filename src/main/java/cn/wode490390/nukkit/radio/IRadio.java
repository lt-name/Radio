package cn.wode490390.nukkit.radio;

import cn.nukkit.Player;
import cn.nukkit.level.Level;

import java.util.List;

public interface IRadio {

    byte MODE_ORDER = 0;
    byte MODE_RANDOM = 1;

    byte getMode();

    void setMode(byte mode);

    List<IMusic> getPlaylist();

    void addMusic(IMusic music);

    void next(Player player, Level level);

    void addListener(Player player, Level level);

    void removeListener(Player player);
}
