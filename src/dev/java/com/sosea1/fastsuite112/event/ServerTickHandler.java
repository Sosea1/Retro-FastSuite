package com.sosea1.fastsuite112.event;

import com.sosea1.fastsuite112.test.FastSuiteSelfTest;
import com.sosea1.fastsuite112.dev.FastSuiteBenchmark;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * FML-bus handler for logical server ticks.
 *
 * <p>Legacy 1.12.2 game tick events are posted on {@code FMLCommonHandler.instance().bus()}, not
 * the normal {@code MinecraftForge.EVENT_BUS}; therefore this handler is registered explicitly
 * while a server is running instead of using {@code @Mod.EventBusSubscriber}.</p>
 */
public final class ServerTickHandler {
    public static final ServerTickHandler INSTANCE = new ServerTickHandler();

    private ServerTickHandler() {}

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        FastSuiteSelfTest.INSTANCE.tick();
        FastSuiteBenchmark.INSTANCE.tick();
    }
}
