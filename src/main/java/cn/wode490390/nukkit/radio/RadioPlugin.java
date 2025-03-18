package cn.wode490390.nukkit.radio;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityLevelChangeEvent;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.event.player.PlayerLocallyInitializedEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.form.element.ElementLabel;
import cn.nukkit.form.element.ElementToggle;
import cn.nukkit.form.response.FormResponse;
import cn.nukkit.form.response.FormResponseCustom;
import cn.nukkit.form.window.FormWindow;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.resourcepacks.ResourcePack;
import cn.nukkit.resourcepacks.ResourcePackManager;
import cn.nukkit.utils.Config;
import cn.wode490390.nukkit.radio.command.RadioAdminCommand;
import cn.wode490390.nukkit.radio.command.RadioCommand;
import cn.wode490390.nukkit.radio.resourcepack.MusicResourcePack;
import cn.wode490390.nukkit.radio.resourcepack.MusicResourcePackLoader;
import cn.wode490390.nukkit.radio.util.MetricsLite;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jaudiotagger.audio.ogg.util.OggInfoReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Stream;

public class RadioPlugin extends PluginBase implements Listener {

    private static RadioPlugin instance;

    private boolean autoplay = true;
    private boolean showNotification = true;

    private final IRadio global = new Radio();
    private final Map<String, IRadio> worldRadios = new HashMap<>();

    private final Map<Player, Boolean> playSetting = new HashMap<>();

    private final Long2IntMap uiWindows = new Long2IntOpenHashMap();

    public static RadioPlugin getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        try {
            new MetricsLite(this, 6082);
        } catch (Throwable ignore) {

        }

        this.saveDefaultConfig();
        Config config = this.getConfig();
        String node = "autoplay";
        try {
            this.autoplay = config.getBoolean(node, this.autoplay);
        } catch (Exception e) {
            this.logConfigException(node, e);
        }
        node = "play-mode";
        try {
            if (config.getString(node).trim().equalsIgnoreCase("random")) {
                this.global.setMode(IRadio.MODE_RANDOM);
            }
        } catch (Exception e) {
            this.logConfigException(node, e);
        }
        node = "show-notification";
        try {
            this.showNotification = config.getBoolean(node, this.showNotification);
        } catch (Exception e) {
            this.logConfigException(node, e);
        }

        Path musicPath = this.getDataFolder().toPath().resolve("music");
        try {
            if (!Files.isDirectory(musicPath, LinkOption.NOFOLLOW_LINKS)) {
                Files.deleteIfExists(musicPath);
                Files.createDirectory(musicPath);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        HashFunction hasher = Hashing.md5();
        List<ResourcePack> packs = new ObjectArrayList<>();
        try (Stream<Path> stream = Files.walk(musicPath, 1)) {
            stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && path.toString().toLowerCase().endsWith(".ogg")).forEach(path -> {
                try (InputStream fis = Files.newInputStream(path, StandardOpenOption.READ)) {
                    byte[] bytes = new byte[fis.available()];
                    fis.read(bytes);

                    String md5 = hasher.hashBytes(bytes).toString();
                    double seconds = new OggInfoReader().read(new RandomAccessFile(path.toFile(), "r")).getPreciseTrackLength();
                    String name = path.getFileName().toString();
                    IMusic music = new Music(md5, (long) Math.ceil(seconds * 1000), name.substring(0, name.length() - 4));

                    packs.add(new MusicResourcePack(md5, bytes));
                    this.global.addMusic(music);
                } catch (Exception ignore) {

                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (Stream<Path> stream = Files.walk(musicPath, 1)) {
            stream.filter(dirPath -> Files.isDirectory(dirPath, LinkOption.NOFOLLOW_LINKS)).forEach(rootPath -> {
                String fileName = rootPath.getFileName().toString();
                IRadio iRadio = this.worldRadios.computeIfAbsent(fileName, s -> new Radio());
                try (Stream<Path> dirStream = Files.walk(rootPath, 1)) {
                    dirStream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && path.toString().toLowerCase().endsWith(".ogg")).forEach(path -> {
                        try (InputStream fis = Files.newInputStream(path, StandardOpenOption.READ)) {
                            byte[] bytes = new byte[fis.available()];
                            fis.read(bytes);

                            String md5 = hasher.hashBytes(bytes).toString();
                            double seconds = new OggInfoReader().read(new RandomAccessFile(path.toFile(), "r")).getPreciseTrackLength();
                            String name = path.getFileName().toString();
                            IMusic music = new Music(md5, (long) Math.ceil(seconds * 1000), name.substring(0, name.length() - 4));

                            packs.add(new MusicResourcePack(md5, bytes));
                            iRadio.addMusic(music);
                        } catch (Exception ignore) {

                        }
                    });
                } catch (Exception ignored) {

                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (!packs.isEmpty()) {
            List<IMusic> playlist = this.global.getPlaylist();
            StringJoiner joiner = new StringJoiner(", ", "Successfully loaded " + playlist.size() + " music: ", "");
            playlist.forEach(music -> joiner.add(music.getName()));
            this.getLogger().info(joiner.toString());

            MusicResourcePackLoader musicResourcePackLoader = new MusicResourcePackLoader(packs);
            ResourcePackManager manager = this.getServer().getResourcePackManager();
            synchronized (manager) {
                manager.registerPackLoader(musicResourcePackLoader);
                manager.reloadPacks();
            }

            this.getServer().getPluginManager().registerEvents(this, this);
        }

        this.getServer().getCommandMap().register("radio", new RadioCommand(this));
        this.getServer().getCommandMap().register("radio", new RadioAdminCommand(this));
    }

    public boolean isShowNotification() {
        return this.showNotification;
    }

    @EventHandler
    public void onPlayerLocallyInitialized(PlayerLocallyInitializedEvent event) {
        if (this.autoplay) {
            final Player player = event.getPlayer();
            this.getServer().getScheduler().scheduleDelayedTask(this, () -> {
                if (this.worldRadios.containsKey(player.getLevel().getName())) {
                    this.worldRadios.get(player.getLevel().getName()).addListener(player);
                } else {
                    this.global.addListener(event.getPlayer());
                }
            }, 10);
        }
    }

    @EventHandler
    public void onPlayerLevelChange(EntityLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!this.playSetting.getOrDefault(player, true)) {
                return;
            }
            if (this.worldRadios.containsKey(event.getOrigin().getName())) {
                this.worldRadios.get(event.getOrigin().getName()).removeListener(player);
            }
            if (this.worldRadios.containsKey(event.getTarget().getName())) {
                this.global.removeListener(player);
                this.worldRadios.get(event.getTarget().getName()).addListener(player);
            } else if (!this.global.isListened(player) ) {
                this.global.addListener(player);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        this.global.removeListener(player);
        this.worldRadios.values().forEach(radio -> radio.removeListener(player));
        this.playSetting.remove(player);
        this.uiWindows.remove(player.getId());
    }

    @EventHandler
    public void onPlayerFormResponded(PlayerFormRespondedEvent event) {
        Player player = event.getPlayer();
        long id = player.getId();
        if (this.uiWindows.get(id) == event.getFormID()) {
            FormWindow window = event.getWindow();
            if (window instanceof FormWindowCustom) {
                FormWindowCustom modalWindow = (FormWindowCustom) window;
                if (modalWindow.getTitle().equals("Radio Manager")) {
                    if (!window.wasClosed()) {
                        FormResponse response = event.getResponse();
                        if (response instanceof FormResponseCustom) {
                            FormResponseCustom customResponse = (FormResponseCustom) response;
                            Object enable = customResponse.getResponse(1);
                            if (enable instanceof Boolean) {
                                Boolean b = (Boolean) enable;
                                this.playSetting.put(player, b);
                                IRadio radio = this.worldRadios.get(player.getLevel().getName());
                                if (radio == null) {
                                    radio = this.global;
                                }
                                if (b) {
                                    radio.addListener(player);
                                } else {
                                    radio.removeListener(player);
                                }
                            }

                        }
                    }
                    this.uiWindows.remove(id);
                }
            }
        }
    }

    public void showUI(Player player) {
        this.uiWindows.put(player.getId(), player.showFormWindow(new FormWindowCustom("Radio Manager", Arrays.asList(
                new ElementLabel("Radio Community Edition"), // 0
                new ElementToggle("Global Radio", this.playSetting.getOrDefault(player, true)) // 1
        ))));
    }

    public IRadio getGlobal() {
        return this.global;
    }

    public IRadio getWorldRadio(String world) {
        return this.worldRadios.get(world);
    }

    private void logConfigException(String node, Throwable t) {
        this.getLogger().warning("An error occurred while reading the configuration '" + node + "'. Use the default value.", t);
    }
}
