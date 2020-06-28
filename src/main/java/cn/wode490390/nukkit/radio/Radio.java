package cn.wode490390.nukkit.radio;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.Level;
import cn.nukkit.network.protocol.PlaySoundPacket;
import cn.nukkit.network.protocol.StopSoundPacket;
import cn.nukkit.utils.TextFormat;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.*;

public class Radio implements IRadio {

    private byte mode = MODE_ORDER;
    private final List<IMusic> playlist = new ObjectArrayList<>();

    private final HashMap<Player, IMusic> playings = new HashMap<>();
    private final HashMap<Player, Timer> timers = new HashMap<>();

    @Override
    public byte getMode() {
        return this.mode;
    }

    @Override
    public void setMode(byte mode) {
        this.mode = mode;
    }

    @Override
    public List<IMusic> getPlaylist() {
        return this.playlist;
    }

    @Override
    public void addMusic(IMusic music) {
        this.playlist.add(music);
    }

    public synchronized void next(Player player) {
        this.next(player, null);
    }

    @Override
    public synchronized void next(Player player, Level level) {
        if (level == null) {
            level = player.getLevel();
        }
        IMusic newIMusic = null;
        if (this.playings.containsKey(player)) {
            stop(null, player);
            newIMusic = getIMusic(this.playings.get(player), level);
        }
        if (newIMusic == null) {
            newIMusic = getIMusic(null, level);
        }
        if (newIMusic == null) {
            newIMusic = this.playlist.get(RadioPlugin.getRNG().nextInt(this.playlist.size()));
        }
        if (newIMusic == null) return;
        this.playings.put(player, newIMusic);
        play(newIMusic, player);
        TimerTask updater = new TimerTask() {
            @Override
            public void run() {
                Radio.this.next(player);
            }
        };
        if (this.timers.containsKey(player)) {
            this.timers.get(player).cancel();
        }
        this.timers.put(player, new Timer("Radio " + player.getName(), true));
        this.timers.get(player).schedule(updater, newIMusic.getDuration());
    }

    private IMusic getIMusic(IMusic iMusic, Level level) {
        for (IMusic newIMusic : this.playlist) {
            if (iMusic != null && iMusic.getMD5().equals(newIMusic.getMD5())) {
                continue;
            }
            String[] s = newIMusic.getName().split("]");
            if (s.length > 0 &&
                    s[0].replace("[", "").trim().equals(level.getName())) {
                return newIMusic;
            }
        }
        return null;
    }

    @Override
    public void addListener(Player player, Level level) {
        this.next(player, level);
    }

    @Override
    public void removeListener(Player player) {
        stop(null, player);
    }

    @Override
    public String toString() {
        return "Radio(" + this.playlist + ")";
    }

    public static void play(IMusic music, Player... players) {
        if (players == null || players.length == 0) {
            return;
        }
        Preconditions.checkNotNull(music, "music");

        String message = TextFormat.YELLOW + "Now playing: " + TextFormat.BOLD + music.getName();
        for (Player player : players) {
            PlaySoundPacket pk = new PlaySoundPacket();
            pk.name = music.getIdentifier();
            pk.pitch = 1;
            pk.volume = 1;
            pk.x = player.getFloorX();
            pk.y = player.getFloorY();
            pk.z = player.getFloorZ();
            player.dataPacket(pk);
            player.sendActionBar(message, 20, 60, 20);
        }
    }

    public static void stop(String identifier, Player... players) {
        if (players == null || players.length == 0) {
            return;
        }

        StopSoundPacket pk = new StopSoundPacket();
        if (identifier == null) {
            pk.stopAll = true;
            pk.name = "";
        } else {
            pk.name = identifier;
        }

        if (players.length == 1) {
            players[0].dataPacket(pk);
        } else {
            Server.broadcastPacket(players, pk);
        }
    }

}
