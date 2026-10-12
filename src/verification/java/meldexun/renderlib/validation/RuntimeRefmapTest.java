package meldexun.renderlib.validation;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.jar.JarFile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;
import org.spongepowered.asm.mixin.refmap.ReferenceMapper;

/** Reads the generated refmap using the actual game Mixin 0.7.11 on JDK 8. */
public final class RuntimeRefmapTest {
    public static void main(String[] args) throws Exception {
        JsonObject json;
        try (JarFile jar = new JarFile(args[0]);
                InputStreamReader reader = new InputStreamReader(jar.getInputStream(
                        jar.getJarEntry("mixins.renderlib.refmap.json")), StandardCharsets.UTF_8)) {
            json = new JsonParser().parse(reader).getAsJsonObject();
        }
        ReferenceMapper mapper = ReferenceMapper.read(new StringReader(json.toString()), "mixins.renderlib.refmap.json");
        if (mapper.isDefault()) throw new AssertionError("Runtime Mixin could not parse the refmap");
        mapper.setContext("searge");
        int count = 0;
        for (Map.Entry<String, JsonElement> mixin : json.getAsJsonObject("mappings").entrySet()) {
            for (Map.Entry<String, JsonElement> mapping : mixin.getValue().getAsJsonObject().entrySet()) {
                String expected = mapping.getValue().getAsString();
                String actual = mapper.remap(mixin.getKey(), mapping.getKey());
                if (!expected.equals(actual)) throw new AssertionError("Runtime remapping failed: " + mapping.getKey());
                MemberInfo.parse(actual).validate();
                count++;
            }
        }
        if (count == 0) throw new AssertionError("Empty refmap");
        System.out.println("Mixin 0.7.11 / Java 8 parsed and validated " + count + " SRG references in searge context.");
    }
}
