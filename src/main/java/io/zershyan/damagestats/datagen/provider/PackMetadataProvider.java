package io.zershyan.damagestats.datagen.provider;

import io.zershyan.damagestats.datagen.init.DSKeyLang;
import net.minecraft.DetectedVersion;
import net.minecraft.data.PackOutput;
import net.minecraft.data.metadata.PackMetadataGenerator;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PackMetadataProvider extends PackMetadataGenerator {
    public PackMetadataProvider(PackOutput pOutput) {
        super(pOutput);
        add(PackMetadataSection.TYPE, new PackMetadataSection(
                DSKeyLang.Resource,
                DetectedVersion.BUILT_IN.getPackVersion(PackType.SERVER_DATA),
                Arrays.stream(PackType.values()).collect(
                        Collectors.toMap(Function.identity(), DetectedVersion.BUILT_IN::getPackVersion)
                )
        ));
    }
}
