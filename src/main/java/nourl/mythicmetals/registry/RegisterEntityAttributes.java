package nourl.mythicmetals.registry;

import net.minecraft.entity.attribute.ClampedEntityAttribute;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.util.registry.Registry;
import nourl.mythicmetals.MythicMetals;
import nourl.mythicmetals.utils.RegistryHelper;

public class RegisterEntityAttributes {

    public static final EntityAttribute EXPERIENCE_BOOST = createAttribute("experience_boost", 1.0D, 1.0D, 1024.0D);

    public static void init() {
        Registry.register(Registry.ATTRIBUTE, RegistryHelper.id("experience_boost"), EXPERIENCE_BOOST);
    }

    private static EntityAttribute createAttribute(final String name, double base, double min, double max) {
        return new ClampedEntityAttribute("attribute.name.generic." + MythicMetals.MOD_ID + '.' + name, base, min, max).setTracked(true);
    }
}