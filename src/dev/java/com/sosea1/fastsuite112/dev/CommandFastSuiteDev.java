package com.sosea1.fastsuite112.dev;

import com.sosea1.fastsuite112.FastSuite112;
import com.sosea1.fastsuite112.recipe.RecipePerformanceTelemetry;
import com.sosea1.fastsuite112.recipe.FallbackMatchTelemetry;
import com.sosea1.fastsuite112.recipe.RecipeIndex;
import com.sosea1.fastsuite112.test.FastSuiteSelfTest;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/** Development-only validation/profiling command. Never packaged in the normal release JAR. */
public final class CommandFastSuiteDev extends CommandBase {
    @Override public String getName() { return "fastsuite-dev"; }
    @Override public String getUsage(ICommandSender sender) { return "/fastsuite-dev <test [quick|full|recipes|fuzz|replay]|bench [quick|full|status|cancel]|fallback [limit]|fallback-hot [limit]|fallback-hot-reset|profile [on|off]|profile-reset>"; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) throw new CommandException(getUsage(sender));
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        if (sub.equals("profile")) {
            if (args.length == 1) {
                RecipePerformanceTelemetry.Snapshot p = RecipePerformanceTelemetry.INSTANCE.snapshot();
                sender.sendMessage(new TextComponentString("[FastSuite dev] profiler=" + (p.enabled ? "ON" : "OFF") +
                    ", indexedSamples=" + p.totalSamples(RecipePerformanceTelemetry.Backend.INDEXED) +
                    ", vanillaSamples=" + p.totalSamples(RecipePerformanceTelemetry.Backend.VANILLA) +
                    String.format(java.util.Locale.ROOT, ", avg indexed=%.3f us, vanilla=%.3f us",
                        p.averageMicros(RecipePerformanceTelemetry.Backend.INDEXED),
                        p.averageMicros(RecipePerformanceTelemetry.Backend.VANILLA))));
                return;
            }
            boolean enabled;
            if (args[1].equalsIgnoreCase("on")) enabled = true;
            else if (args[1].equalsIgnoreCase("off")) enabled = false;
            else throw new CommandException("Use /fastsuite-dev profile on|off");
            RecipePerformanceTelemetry.INSTANCE.setEnabled(enabled);
            FallbackMatchTelemetry.INSTANCE.setEnabled(enabled);
            sender.sendMessage(new TextComponentString("[FastSuite dev] profiler " + (enabled ? "enabled" : "disabled")));
            return;
        }
        if (sub.equals("profile-reset")) {
            RecipePerformanceTelemetry.INSTANCE.reset();
            FallbackMatchTelemetry.INSTANCE.reset();
            sender.sendMessage(new TextComponentString("[FastSuite dev] profiler reset"));
            return;
        }
        if (sub.equals("fallback-hot-reset")) {
            FallbackMatchTelemetry.INSTANCE.reset();
            sender.sendMessage(new TextComponentString("[FastSuite dev] hot fallback profiler reset"));
            return;
        }
        if (sub.equals("fallback-hot")) {
            int limit = parseLimit(args, 20, "hot fallback group");
            FallbackMatchTelemetry.Snapshot snapshot = FallbackMatchTelemetry.INSTANCE.snapshot();
            String summary = "[FastSuite fallback-hot] enabled=" + snapshot.enabled +
                " | sampleEvery=" + snapshot.sampleEvery + " | groups=" + snapshot.groups.size();
            sender.sendMessage(new TextComponentString(summary));
            FastSuite112.LOGGER.info("[fallback-hot] {}", summary);
            int end = Math.min(limit, snapshot.groups.size());
            for (int i = 0; i < end; i++) {
                FallbackMatchTelemetry.Group group = snapshot.groups.get(i);
                String line = String.format(java.util.Locale.ROOT,
                    "[FastSuite fallback-hot] #%d calls=%d total=%.3f ms avg=%.3f us max=%.3f us failures=%d | %s",
                    i + 1, group.calls, group.totalMillis(), group.averageMicros(), group.maxMicros(),
                    group.failures, group.recipeClass);
                sender.sendMessage(new TextComponentString(line));
                FastSuite112.LOGGER.info("[fallback-hot] {}", line);
            }
            return;
        }
        if (sub.equals("fallback") || sub.equals("fallbacks")) {
            int limit = 20;
            if (args.length > 1) {
                try {
                    limit = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    throw new CommandException("Invalid fallback group limit: " + args[1]);
                }
                if (limit < 1 || limit > 100) throw new CommandException("Fallback group limit must be 1..100");
            }

            RecipeIndex.FallbackDiagnostics diagnostics = RecipeIndex.INSTANCE.getFallbackDiagnostics();
            String summary = "[FastSuite fallback] total=" + diagnostics.total +
                " / registry=" + RecipeIndex.INSTANCE.getRecipeCount() +
                " | constrained=" + diagnostics.constrainedTotal +
                " | opaque=" + diagnostics.opaqueTotal +
                " | providerFailures=" + diagnostics.providerFailures +
                " | reasons=" + diagnostics.byReason;
            sender.sendMessage(new TextComponentString(summary));
            FastSuite112.LOGGER.info("[fallback] {}", summary);
            int rank = 0;
            for (RecipeIndex.FallbackGroup group : diagnostics.topGroups(limit)) {
                rank++;
                StringBuilder line = new StringBuilder(192);
                line.append("[FastSuite fallback] #").append(rank)
                    .append(" x").append(group.count)
                    .append(' ').append(group.reason)
                    .append(" | recipe=").append(group.recipeClass);
                if (group.detailClass != null) line.append(" | detail=").append(group.detailClass);
                if (group.failureClass != null) line.append(" | threw=").append(group.failureClass);
                line.append(" | example=").append(group.exampleRecipe);
                String rendered = line.toString();
                sender.sendMessage(new TextComponentString(rendered));
                FastSuite112.LOGGER.info("[fallback] {}", rendered);
            }
            return;
        }
        if (sub.equals("bench") || sub.equals("benchmark")) {
            String modeStr = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "full";
            if (modeStr.equals("status")) {
                sender.sendMessage(new TextComponentString("[FastSuite bench] running=" + FastSuiteBenchmark.INSTANCE.isRunning()));
                return;
            }
            if (modeStr.equals("cancel")) {
                FastSuiteBenchmark.INSTANCE.cancel("requested by command");
                return;
            }
            FastSuiteBenchmark.Mode mode;
            if (modeStr.equals("quick")) mode = FastSuiteBenchmark.Mode.QUICK;
            else if (modeStr.equals("full")) mode = FastSuiteBenchmark.Mode.FULL;
            else throw new CommandException("Use /fastsuite-dev bench quick|full|status|cancel");
            FastSuiteBenchmark.INSTANCE.start(mode, sender, sender.getEntityWorld());
            return;
        }
        if (sub.equals("test")) {
            World world = sender.getEntityWorld();
            String modeStr = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "quick";
            FastSuiteSelfTest.TestMode mode;
            long seed = 0L;
            int fuzzCount = 0;
            if (modeStr.equals("quick")) mode = FastSuiteSelfTest.TestMode.QUICK;
            else if (modeStr.equals("full")) mode = FastSuiteSelfTest.TestMode.FULL;
            else if (modeStr.equals("recipes")) mode = FastSuiteSelfTest.TestMode.RECIPES;
            else if (modeStr.equals("fuzz")) {
                mode = FastSuiteSelfTest.TestMode.FUZZ;
                if (args.length > 2) {
                    try { fuzzCount = Integer.parseInt(args[2]); } catch (NumberFormatException e) { throw new CommandException("Invalid fuzz count: " + args[2]); }
                }
                if (args.length > 3) {
                    try { seed = Long.parseLong(args[3]); } catch (NumberFormatException e) { throw new CommandException("Invalid seed: " + args[3]); }
                }
            } else if (modeStr.equals("replay")) {
                if (args.length < 3) throw new CommandException("Usage: /fastsuite-dev test replay <seed>");
                mode = FastSuiteSelfTest.TestMode.REPLAY;
                try { seed = Long.parseLong(args[2]); } catch (NumberFormatException e) { throw new CommandException("Invalid seed: " + args[2]); }
            } else throw new CommandException("Unknown test mode: " + modeStr);
            FastSuiteSelfTest.INSTANCE.start(mode, sender, world, seed, fuzzCount);
            return;
        }
        throw new CommandException(getUsage(sender));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "test", "bench", "fallback", "fallback-hot", "fallback-hot-reset", "profile", "profile-reset");
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) return getListOfStringsMatchingLastWord(args, "quick", "full", "recipes", "fuzz", "replay");
        if (args.length == 2 && args[0].equalsIgnoreCase("bench")) return getListOfStringsMatchingLastWord(args, "quick", "full", "status", "cancel");
        if (args.length == 2 && args[0].equalsIgnoreCase("profile")) return getListOfStringsMatchingLastWord(args, "on", "off");
        return Collections.emptyList();
    }

    private static int parseLimit(String[] args, int defaultValue, String label) throws CommandException {
        if (args.length <= 1) return defaultValue;
        final int limit;
        try {
            limit = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new CommandException("Invalid " + label + " limit: " + args[1]);
        }
        if (limit < 1 || limit > 100) throw new CommandException(label + " limit must be 1..100");
        return limit;
    }
}
