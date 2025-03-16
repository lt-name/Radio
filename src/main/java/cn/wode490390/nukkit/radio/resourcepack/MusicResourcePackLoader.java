package cn.wode490390.nukkit.radio.resourcepack;

import cn.nukkit.resourcepacks.ResourcePack;
import cn.nukkit.resourcepacks.loader.ResourcePackLoader;

import java.util.List;

/**
 * @author LT_Name
 */
public class MusicResourcePackLoader implements ResourcePackLoader {

    private final List<ResourcePack> packs;

    public MusicResourcePackLoader(List<ResourcePack> packs) {
        this.packs = packs;
    }

    @Override
    public List<ResourcePack> loadPacks() {
        return this.packs;
    }
}
