package dev.sosea1.fastsuite112.dev;

import dev.sosea1.fastsuite112.event.ServerTickHandler;
import dev.sosea1.fastsuite112.recipe.RecipePerformanceTelemetry;
import dev.sosea1.fastsuite112.test.FastSuiteSelfTest;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;

/** Present only in the development JAR. Release builds discover no such class. */
public final class FastSuiteDevTools {
    private static boolean installed;
    private FastSuiteDevTools() {}

    public static void postInit() {
        if (installed) return;
        FMLCommonHandler.instance().bus().register(ServerTickHandler.INSTANCE);
        installed = true;
    }

    public static void serverStarting(FMLServerStartingEvent event) {
        RecipePerformanceTelemetry.INSTANCE.reset();
        event.registerServerCommand(new CommandFastSuiteDev());
    }

    public static void serverStopping(FMLServerStoppingEvent event) {
        FastSuiteSelfTest.INSTANCE.cancel("server stopping");
        FastSuiteBenchmark.INSTANCE.cancel("server stopping");
        RecipePerformanceTelemetry.INSTANCE.setEnabled(false);
    }
}
