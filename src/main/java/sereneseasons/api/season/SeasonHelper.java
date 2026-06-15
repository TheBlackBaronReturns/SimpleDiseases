// Compile-time stub — excluded from output JAR. Real implementation provided by Serene Seasons at runtime.
package sereneseasons.api.season;

import net.minecraft.world.level.Level;

public class SeasonHelper {
    public static ISeasonDataProvider dataProvider;

    public static ISeasonState getSeasonState(Level level) {
        if (!level.isClientSide()) return dataProvider.getServerSeasonState(level);
        return dataProvider.getClientSeasonState(level);
    }

    public interface ISeasonDataProvider {
        ISeasonState getServerSeasonState(Level level);
        ISeasonState getClientSeasonState(Level level);
    }
}
