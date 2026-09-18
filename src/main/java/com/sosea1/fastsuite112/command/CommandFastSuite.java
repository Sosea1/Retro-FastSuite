package com.sosea1.fastsuite112.command;

import com.sosea1.fastsuite112.FastSuite112;
import com.sosea1.fastsuite112.compat.CompatibilityStatus;
import com.sosea1.fastsuite112.compat.MixinHookTracker;
import com.sosea1.fastsuite112.config.FastSuiteConfig;
import com.sosea1.fastsuite112.recipe.RecipeIndex;
import com.sosea1.fastsuite112.recipe.RecipeRuntimeControl;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Small release command surface: status, integrity verification and troubleshooting only. */
public final class CommandFastSuite extends CommandBase {
    @Override
    public String getName() { return "fastsuite"; }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/fastsuite <stats|verify|mode [config|indexed|vanilla]|rebuild>";
    }

    @Override
    public int getRequiredPermissionLevel() { return 0; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("info")) {
            showStats(sender);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("verify")) {
            requireOperator(sender, "scan the live recipe registry");
            RecipeIndex.RegistryVerification verification = RecipeIndex.INSTANCE.verifyRegistrySnapshot();
            TextFormatting color = verification.matches ? TextFormatting.GREEN : TextFormatting.RED;
            sender.sendMessage(new TextComponentString(color + "[FastSuite] Registry snapshot " +
                (verification.matches ? "matches" : "DOES NOT MATCH") + " the live registry."));
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Indexed: " + TextFormatting.WHITE + verification.snapshot));
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Live: " + TextFormatting.WHITE + verification.current));
            if (!verification.matches) {
                sender.sendMessage(new TextComponentString(TextFormatting.RED +
                    "A mutation bypassed invalidation. Capture the responsible action/mod, then run /fastsuite rebuild."));
            }
            return;
        }

        if (sub.equals("mode")) {
            if (args.length == 1) {
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[FastSuite] Runtime mode: " + TextFormatting.WHITE +
                    RecipeRuntimeControl.getMode() + TextFormatting.GRAY + " (effective=" +
                    (RecipeRuntimeControl.isIndexActuallyEnabled() ? "INDEXED" : "VANILLA") + ")"));
                return;
            }
            requireOperator(sender, "change the runtime recipe backend");
            String value = args[1].toLowerCase(Locale.ROOT);
            RecipeRuntimeControl.Mode mode;
            if (value.equals("config") || value.equals("auto")) mode = RecipeRuntimeControl.Mode.CONFIG;
            else if (value.equals("indexed") || value.equals("fastsuite")) mode = RecipeRuntimeControl.Mode.INDEXED;
            else if (value.equals("vanilla") || value.equals("registry")) mode = RecipeRuntimeControl.Mode.VANILLA;
            else throw new CommandException("Unknown mode: " + args[1] + ". Use config, indexed, or vanilla.");
            RecipeRuntimeControl.setMode(mode);
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[FastSuite] Runtime backend set to " + mode +
                TextFormatting.GRAY + " (effective=" + (RecipeRuntimeControl.isIndexActuallyEnabled() ? "INDEXED" : "VANILLA") + ")."));
            return;
        }

        if (sub.equals("rebuild") || sub.equals("invalidate")) {
            requireOperator(sender, "rebuild the recipe index");
            RecipeIndex.INSTANCE.invalidate("Manual /fastsuite command");
            RecipeIndex.INSTANCE.ensureBuilt();
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[FastSuite] Recipe index rebuilt."));
            return;
        }

        throw new CommandException("Unknown subcommand. " + getUsage(sender));
    }

    private static void showStats(ICommandSender sender) {
        RecipeIndex.StatusSnapshot state = RecipeIndex.INSTANCE.getStatusSnapshot();
        CompatibilityStatus.Snapshot compat = CompatibilityStatus.snapshot();
        int indexed = state.recipeCount - state.fallbackRecipeCount;
        double indexedPercent = state.recipeCount == 0 ? 0.0D : indexed * 100.0D / state.recipeCount;

        sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== " + FastSuite112.NAME + " ==="));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Version: " + TextFormatting.WHITE + FastSuite112.VERSION));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Backend: " + TextFormatting.WHITE +
            (RecipeRuntimeControl.isIndexActuallyEnabled() ? "INDEXED" : "VANILLA") + TextFormatting.GRAY +
            " (override=" + RecipeRuntimeControl.getMode() + ")"));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Recipes: " + TextFormatting.WHITE + state.recipeCount +
            TextFormatting.GRAY + " (indexed=" + indexed + "/" + String.format(Locale.ROOT, "%.1f%%", indexedPercent) +
            ", fallback=" + state.fallbackRecipeCount + ", itemBuckets=" + state.bucketCount +
            ", exactMetaBuckets=" + state.exactMetadataBucketCount + ")"));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Index: " + TextFormatting.WHITE +
            (RecipeIndex.INSTANCE.isDirty() ? "DIRTY" : "ready") + TextFormatting.GRAY +
            ", generation=" + state.epoch + ", rebuilds=" + RecipeIndex.INSTANCE.getRebuildsTotal() +
            ", last=" + String.format(Locale.ROOT, "%.3f ms", RecipeIndex.INSTANCE.getLastRebuildNanos() / 1_000_000.0D)));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Compatibility: " + TextFormatting.WHITE +
            "CraftTweaker=" + yesNo(compat.craftTweakerInstalled) +
            ", UT=" + yesNo(compat.universalTweaksInstalled) +
            ", UT-cache=" + compat.universalTweaksCraftingCache +
            ", FastWorkbench=" + yesNo(compat.fastWorkbenchInstalled)));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Hooks: " + TextFormatting.WHITE +
            "vanilla=" + MixinHookTracker.formatHookState(MixinHookTracker.vanillaCraftingHookApplied, MixinHookTracker.vanillaCraftingHookActive) +
            ", UT=" + MixinHookTracker.formatHookState(MixinHookTracker.universalTweaksHookApplied, MixinHookTracker.universalTweaksHookActive) +
            ", registry=" + MixinHookTracker.formatHookState(MixinHookTracker.forgeRegistryHookApplied, MixinHookTracker.forgeRegistryHookActive) +
            ", ingredientFix=" + MixinHookTracker.formatHookState(MixinHookTracker.ingredientCacheFixApplied, MixinHookTracker.ingredientCacheFixActive)));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Candidate pruning: " + TextFormatting.WHITE +
            (FastSuiteConfig.advancedCandidatePruning ? "ON" : "OFF") + TextFormatting.GRAY +
            " (frequency-aware pivots=" + (FastSuiteConfig.frequencyAwarePivotSelection ? "ON" : "OFF") +
            ", load-balanced=" + (FastSuiteConfig.loadBalancedPivotSelection ? "ON" : "OFF") +
            ", exact-meta primary index=ON, compiled signatures=ON)"));
        RecipeIndex.FallbackDiagnostics fallbackDiagnostics = RecipeIndex.INSTANCE.getFallbackDiagnostics();
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Fallback reasons: " + TextFormatting.WHITE +
            fallbackDiagnostics.byReason));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Debug rebuild logging: " + TextFormatting.WHITE +
            (FastSuiteConfig.debugLogging ? "ON" : "OFF")));

        if (RecipeRuntimeControl.isIndexActuallyEnabled()) {
            if (!MixinHookTracker.hasAnyLookupHookApplied()) {
                sender.sendMessage(new TextComponentString(TextFormatting.RED +
                    "[WARNING] FastSuite index is enabled, but neither vanilla nor UT crafting hook is applied!"));
            } else if (!MixinHookTracker.hasAnyLookupHookActive()) {
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW +
                    "[FastSuite] Lookup hook is applied; waiting for first in-game crafting operation."));
            }
        }
    }

    private static String yesNo(boolean value) { return value ? "yes" : "no"; }

    private void requireOperator(ICommandSender sender, String action) throws CommandException {
        if (!sender.canUseCommand(2, getName())) throw new CommandException("You need permission level 2 to " + action + ".");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "stats", "verify", "mode", "rebuild");
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return getListOfStringsMatchingLastWord(args, "config", "indexed", "vanilla");
        }
        return Collections.emptyList();
    }
}
