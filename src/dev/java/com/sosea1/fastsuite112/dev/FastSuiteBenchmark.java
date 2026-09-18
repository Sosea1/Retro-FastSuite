package com.sosea1.fastsuite112.dev;

import com.sosea1.fastsuite112.FastSuite112;
import com.sosea1.fastsuite112.config.FastSuiteConfig;
import com.sosea1.fastsuite112.recipe.RecipeIndex;
import com.sosea1.fastsuite112.test.FastSuiteSelfTest;
import com.sosea1.fastsuite112.test.ReferenceCraftingScanner;
import net.minecraft.block.BlockPlanks;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Development-only benchmark modeled after the benchmark harness shipped with modern FastSuite.
 *
 * <p>The measured workloads deliberately bypass Universal Tweaks / FastWorkbench result caches and
 * compare the FastSuite112 candidate index directly against a full CraftingManager.REGISTRY scan.
 * This isolates the miss-path this mod is responsible for.</p>
 *
 * <p>Modern FastSuite reports five canonical inputs, with 10,000 measured lookups for both
 * match-all (Polymorph-style) and short-circuit first-match workloads. We keep the same five
 * semantic scenarios, adapted to 1.12.2 item/meta conventions. Execution is sliced across logical
 * server ticks only to avoid watchdog stalls on very large registries; timing is still performed
 * around each individual lookup with System.nanoTime(), matching the upstream measurement shape.</p>
 */
public final class FastSuiteBenchmark {
    public static final FastSuiteBenchmark INSTANCE = new FastSuiteBenchmark();

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final long TICK_BUDGET_NANOS = 8_000_000L;
    private static volatile int blackhole;

    private Session session;

    private FastSuiteBenchmark() {}

    public enum Mode {
        QUICK(1_000, 200),
        FULL(10_000, 1_000);

        final int iterations;
        final int warmupIterations;

        Mode(int iterations, int warmupIterations) {
            this.iterations = iterations;
            this.warmupIterations = warmupIterations;
        }
    }

    public void start(Mode mode, ICommandSender sender, World world) {
        if (world == null || world.isRemote) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED +
                "[FastSuite bench] Benchmark must run on the logical server."));
            return;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED +
                "[FastSuite bench] A benchmark is already running."));
            return;
        }

        try {
            RecipeIndex.INSTANCE.ensureBuilt();
            Session created = new Session(mode == null ? Mode.FULL : mode, sender, world);
            session = created;
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW +
                "[FastSuite bench] Starting upstream-compatible recipe benchmark: " +
                created.mode.iterations + " measured lookups per backend/workload, " +
                created.mode.warmupIterations + " warmup."));
            sender.sendMessage(new TextComponentString(TextFormatting.GRAY +
                "[FastSuite bench] Recipe registry: " + RecipeIndex.INSTANCE.getRecipeCount() +
                ". UT/FastWorkbench result caches are bypassed intentionally."));
        } catch (RuntimeException | LinkageError failure) {
            RUNNING.set(false);
            session = null;
            throw failure;
        }
    }

    public void tick() {
        Session current = session;
        if (current == null) return;

        try {
            if (current.tick()) finish(current);
        } catch (Throwable throwable) {
            ReferenceCraftingScanner.rethrowFatal(throwable);
            FastSuite112.LOGGER.error("Error during FastSuite benchmark", throwable);
            current.sender.sendMessage(new TextComponentString(TextFormatting.RED +
                "[FastSuite bench] Aborted: " + throwable.getClass().getName() +
                (throwable.getMessage() == null ? "" : ": " + throwable.getMessage())));
            clear(current);
        }
    }

    public void cancel(String reason) {
        Session current = session;
        if (current == null) return;
        current.sender.sendMessage(new TextComponentString(TextFormatting.YELLOW +
            "[FastSuite bench] Cancelled" + (reason == null ? "." : ": " + reason)));
        clear(current);
    }

    public boolean isRunning() {
        return session != null;
    }

    private void finish(Session current) {
        try {
            current.printResults();
        } finally {
            clear(current);
        }
    }

    private void clear(Session current) {
        if (session == current) session = null;
        current.restoreConfig();
        RUNNING.set(false);
    }

    private static final class Session {
        private final Mode mode;
        private final ICommandSender sender;
        private final World world;
        private final BenchCase[] cases;
        private final double[][][] micros;
        private final int[][] candidateCounts;
        private final boolean previousIndexedCrafting;
        private final long startedNanos;

        private int caseIndex;
        private int workloadIndex;
        private int backendIndex;
        private int warmupDone;
        private int measuredDone;
        private long measuredNanos;
        private boolean configRestored;

        private Session(Mode mode, ICommandSender sender, World world) {
            this.mode = mode;
            this.sender = sender;
            this.world = world;
            this.previousIndexedCrafting = FastSuiteConfig.indexedCrafting;
            this.startedNanos = System.nanoTime();
            this.cases = createCases();
            this.micros = new double[cases.length][Workload.values().length][Backend.values().length];
            this.candidateCounts = new int[cases.length][3];

            // Benchmark the index even if the user's normal config currently has it disabled.
            FastSuiteConfig.indexedCrafting = true;
            try {
                ensureRealNoMatchCase();
                verifyCanonicalCases();
                collectCandidateCounts();
            } catch (RuntimeException | LinkageError failure) {
                FastSuiteConfig.indexedCrafting = previousIndexedCrafting;
                configRestored = true;
                throw failure;
            }
        }

        private boolean tick() {
            long tickStart = System.nanoTime();
            while (caseIndex < cases.length && System.nanoTime() - tickStart < TICK_BUDGET_NANOS) {
                BenchCase benchCase = cases[caseIndex];
                Workload workload = Workload.values()[workloadIndex];
                Backend backend = Backend.values()[backendIndex];

                if (warmupDone < mode.warmupIterations) {
                    consume(runLookup(benchCase.grid, workload, backend));
                    warmupDone++;
                    continue;
                }

                if (measuredDone < mode.iterations) {
                    long start = System.nanoTime();
                    int result = runLookup(benchCase.grid, workload, backend);
                    long elapsed = System.nanoTime() - start;
                    consume(result);
                    measuredNanos += elapsed;
                    measuredDone++;
                    continue;
                }

                micros[caseIndex][workloadIndex][backendIndex] =
                    measuredNanos / 1000.0D / (double) mode.iterations;
                advancePhase();
            }
            return caseIndex >= cases.length;
        }

        private void advancePhase() {
            warmupDone = 0;
            measuredDone = 0;
            measuredNanos = 0L;

            backendIndex++;
            if (backendIndex < Backend.values().length) return;
            backendIndex = 0;

            workloadIndex++;
            if (workloadIndex < Workload.values().length) return;
            workloadIndex = 0;

            sender.sendMessage(new TextComponentString(TextFormatting.DARK_GRAY +
                "[FastSuite bench] Finished case " + cases[caseIndex].name +
                " (" + (caseIndex + 1) + "/" + cases.length + ")."));
            caseIndex++;
        }

        private int runLookup(InventoryCrafting grid, Workload workload, Backend backend) {
            Iterable<IRecipe> recipes = backend == Backend.INDEXED
                ? RecipeIndex.INSTANCE.candidatesFor(grid)
                : CraftingManager.REGISTRY;

            if (workload == Workload.ALL_MATCHES) {
                int matches = 0;
                for (IRecipe recipe : recipes) {
                    if (recipe.matches(grid, world)) matches++;
                }
                return matches;
            }

            for (IRecipe recipe : recipes) {
                if (recipe.matches(grid, world)) return 1;
            }
            return 0;
        }

        private void ensureRealNoMatchCase() {
            int failedIndex = cases.length - 1;
            if (isNoMatch(cases[failedIndex].grid)) return;

            // A huge modpack can add surprising catch-all/custom recipes. Keep the public benchmark
            // meaning honest by finding a deterministic 3x3 input that truly has no vanilla match.
            List<Item> pool = new ArrayList<Item>();
            for (Item item : Item.REGISTRY) {
                if (item != null && item != Items.AIR) pool.add(item);
            }
            if (pool.isEmpty()) {
                throw new IllegalStateException("Cannot synthesize failed-match benchmark input: item registry is empty");
            }

            Random rng = new Random(0xF4575009L);
            for (int attempt = 0; attempt < 512; attempt++) {
                InventoryCrafting candidate = FastSuiteSelfTest.createGrid(3, 3);
                for (int slot = 0; slot < 9; slot++) {
                    Item item = pool.get(rng.nextInt(pool.size()));
                    candidate.setInventorySlotContents(slot, new ItemStack(item));
                }
                if (isNoMatch(candidate)) {
                    cases[failedIndex] = new BenchCase("failed match", candidate);
                    FastSuite112.LOGGER.info("[benchmark] Canonical failed-match mixture matched a modded recipe; using deterministic fallback attempt {}.", attempt + 1);
                    return;
                }
            }
            throw new IllegalStateException("Could not synthesize a true failed-match 3x3 benchmark input after 512 attempts");
        }

        private boolean isNoMatch(InventoryCrafting grid) {
            ReferenceCraftingScanner.FirstMatchOutcome outcome =
                ReferenceCraftingScanner.findFirstOutcome(CraftingManager.REGISTRY, grid, world);
            if (outcome.kind == ReferenceCraftingScanner.OutcomeKind.THREW) {
                throw new IllegalStateException("A recipe threw while probing failed-match benchmark input: " +
                    (outcome.throwingRecipe == null ? "unknown" : String.valueOf(CraftingManager.REGISTRY.getNameForObject(outcome.throwingRecipe))));
            }
            return outcome.kind == ReferenceCraftingScanner.OutcomeKind.NO_MATCH;
        }

        private void verifyCanonicalCases() {
            for (BenchCase benchCase : cases) {
                ReferenceCraftingScanner.AllMatchesOutcome vanillaAll =
                    ReferenceCraftingScanner.findAllOutcome(CraftingManager.REGISTRY, benchCase.grid, world);
                ReferenceCraftingScanner.AllMatchesOutcome indexedAll =
                    ReferenceCraftingScanner.findAllOutcome(RecipeIndex.INSTANCE.candidatesFor(benchCase.grid), benchCase.grid, world);

                if (vanillaAll.threw() || indexedAll.threw()) {
                    throw new IllegalStateException("Canonical benchmark case throws during matching: " + benchCase.name);
                }
                if (!sameIdentityOrder(vanillaAll.matches, indexedAll.matches)) {
                    throw new IllegalStateException("Indexed/vanilla all-match mismatch for benchmark case: " + benchCase.name);
                }

                ReferenceCraftingScanner.FirstMatchOutcome vanillaFirst =
                    ReferenceCraftingScanner.findFirstOutcome(CraftingManager.REGISTRY, benchCase.grid, world);
                ReferenceCraftingScanner.FirstMatchOutcome indexedFirst =
                    ReferenceCraftingScanner.findFirstOutcome(RecipeIndex.INSTANCE.candidatesFor(benchCase.grid), benchCase.grid, world);

                if (vanillaFirst.kind != indexedFirst.kind || vanillaFirst.recipe != indexedFirst.recipe ||
                    vanillaFirst.throwingRecipe != indexedFirst.throwingRecipe ||
                    vanillaFirst.exceptionClass != indexedFirst.exceptionClass) {
                    throw new IllegalStateException("Indexed/vanilla first-match mismatch for benchmark case: " + benchCase.name);
                }
            }
        }

        private void collectCandidateCounts() {
            for (int i = 0; i < cases.length; i++) {
                int indexed = 0;
                for (IRecipe ignored : RecipeIndex.INSTANCE.candidatesFor(cases[i].grid)) indexed++;
                candidateCounts[i][0] = indexed;
                candidateCounts[i][1] = RecipeIndex.INSTANCE.getRecipeCount();
                candidateCounts[i][2] = RecipeIndex.INSTANCE.getFallbackDiagnostics().total;
            }
        }

        private void printResults() {
            long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L;
            send(TextFormatting.GOLD + "========== FastSuite112 benchmark ==========");
            send(String.format(Locale.ROOT,
                "Registry: %,d recipes | measured: %,d lookups/cell | warmup: %,d | elapsed: %,d ms",
                RecipeIndex.INSTANCE.getRecipeCount(), mode.iterations, mode.warmupIterations, elapsedMs));
            send("Workloads mirror modern FastSuite: match-all and findFirst; result caches bypassed.");
            send("");
            send(TextFormatting.AQUA + "Matching every recipe (Polymorph-style)");
            for (int i = 0; i < cases.length; i++) printRow(i, Workload.ALL_MATCHES);
            send("");
            send(TextFormatting.AQUA + "Finding the first match (vanilla-style)");
            for (int i = 0; i < cases.length; i++) printRow(i, Workload.FIRST_MATCH);
            RecipeIndex.FallbackDiagnostics fallbackDiagnostics = RecipeIndex.INSTANCE.getFallbackDiagnostics();
            send("");
            send(String.format(Locale.ROOT, "Fallback floor: %,d recipes (%.2f%% of registry) | reasons=%s",
                fallbackDiagnostics.total,
                RecipeIndex.INSTANCE.getRecipeCount() == 0 ? 0.0D : fallbackDiagnostics.total * 100.0D / RecipeIndex.INSTANCE.getRecipeCount(),
                fallbackDiagnostics.byReason));
            send("Use /fastsuite-dev fallback 20 for the largest fallback families.");
            send(TextFormatting.GOLD + "============================================");
        }

        private void printRow(int index, Workload workload) {
            double indexed = micros[index][workload.ordinal()][Backend.INDEXED.ordinal()];
            double vanilla = micros[index][workload.ordinal()][Backend.VANILLA.ordinal()];
            double speedup = indexed <= 0.0D ? 0.0D : vanilla / indexed;
            int candidateTotal = candidateCounts[index][0];
            int fallback = candidateCounts[index][2];
            int indexedCandidates = Math.max(0, candidateTotal - fallback);
            String row = String.format(Locale.ROOT,
                "%s: Indexed %.2f us | Vanilla %.2f us | %.2fx | candidates %,d = indexed %,d + fallback %,d / %,d",
                cases[index].name, indexed, vanilla, speedup,
                candidateTotal, indexedCandidates, fallback, candidateCounts[index][1]);
            send(row);
        }

        private void send(String text) {
            sender.sendMessage(new TextComponentString(text));
            FastSuite112.LOGGER.info("[benchmark] {}", TextFormatting.getTextWithoutFormattingCodes(text));
        }

        private void restoreConfig() {
            if (configRestored) return;
            FastSuiteConfig.indexedCrafting = previousIndexedCrafting;
            configRestored = true;
        }
    }

    private enum Workload {
        ALL_MATCHES,
        FIRST_MATCH
    }

    private enum Backend {
        INDEXED,
        VANILLA
    }

    private static final class BenchCase {
        private final String name;
        private final InventoryCrafting grid;

        private BenchCase(String name, InventoryCrafting grid) {
            this.name = name;
            this.grid = grid;
        }
    }

    private static BenchCase[] createCases() {
        List<BenchCase> result = new ArrayList<BenchCase>(5);

        // Modern FastSuite: one acacia log -> acacia planks.
        InventoryCrafting acaciaPlanks = FastSuiteSelfTest.createGrid(2, 2);
        acaciaPlanks.setInventorySlotContents(0, new ItemStack(Blocks.LOG2, 1, 0));
        result.add(new BenchCase("acacia planks", acaciaPlanks));

        // Modern FastSuite: two birch planks vertically -> sticks.
        InventoryCrafting sticks = FastSuiteSelfTest.createGrid(2, 2);
        int birchMeta = BlockPlanks.EnumType.BIRCH.getMetadata();
        sticks.setInventorySlotContents(0, new ItemStack(Blocks.PLANKS, 1, birchMeta));
        sticks.setInventorySlotContents(2, new ItemStack(Blocks.PLANKS, 1, birchMeta));
        result.add(new BenchCase("sticks", sticks));

        // Modern FastSuite: four oak planks -> crafting table.
        InventoryCrafting craftingTable = FastSuiteSelfTest.createGrid(2, 2);
        int oakMeta = BlockPlanks.EnumType.OAK.getMetadata();
        for (int i = 0; i < 4; i++) {
            craftingTable.setInventorySlotContents(i, new ItemStack(Blocks.PLANKS, 1, oakMeta));
        }
        result.add(new BenchCase("crafting table", craftingTable));

        // Modern FastSuite: shulker box + black dye -> black shulker box.
        InventoryCrafting blackShulker = FastSuiteSelfTest.createGrid(2, 2);
        blackShulker.setInventorySlotContents(0, new ItemStack(Blocks.PURPLE_SHULKER_BOX));
        blackShulker.setInventorySlotContents(3,
            new ItemStack(Items.DYE, 1, EnumDyeColor.BLACK.getDyeDamage()));
        result.add(new BenchCase("black shulker box", blackShulker));

        // Same intent as upstream's nine-slot impossible mixture, adapted to items available in 1.12.2.
        InventoryCrafting failed = FastSuiteSelfTest.createGrid(3, 3);
        failed.setInventorySlotContents(0, new ItemStack(Items.STICK));
        failed.setInventorySlotContents(1, new ItemStack(Blocks.STICKY_PISTON));
        failed.setInventorySlotContents(2, new ItemStack(Blocks.ACACIA_FENCE));
        failed.setInventorySlotContents(3, new ItemStack(Blocks.LEAVES2, 1, 0));
        failed.setInventorySlotContents(4, new ItemStack(Items.APPLE));
        failed.setInventorySlotContents(5, new ItemStack(Blocks.OBSIDIAN));
        failed.setInventorySlotContents(6, new ItemStack(Items.BLAZE_ROD));
        failed.setInventorySlotContents(7, new ItemStack(Items.DYE, 1, EnumDyeColor.BLACK.getDyeDamage()));
        failed.setInventorySlotContents(8, new ItemStack(Blocks.PURPLE_SHULKER_BOX));
        result.add(new BenchCase("failed match", failed));

        return result.toArray(new BenchCase[result.size()]);
    }

    private static boolean sameIdentityOrder(List<IRecipe> first, List<IRecipe> second) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            if (first.get(i) != second.get(i)) return false;
        }
        return true;
    }

    private static void consume(int value) {
        blackhole ^= value + 0x9E3779B9;
    }
}
