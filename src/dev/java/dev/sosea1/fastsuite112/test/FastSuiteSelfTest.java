package dev.sosea1.fastsuite112.test;

import dev.sosea1.fastsuite112.FastSuite112;
import dev.sosea1.fastsuite112.recipe.RecipeIndex;
import dev.sosea1.fastsuite112.recipe.RecipeSafetyClassifier;
import dev.sosea1.fastsuite112.test.ReferenceCraftingScanner.AllMatchesOutcome;
import dev.sosea1.fastsuite112.test.ReferenceCraftingScanner.FirstMatchOutcome;
import dev.sosea1.fastsuite112.test.ReferenceCraftingScanner.OutcomeKind;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-game self-testing and differential fuzzing harness.
 *
 * <p>Stage 3 deliberately executes arbitrary recipe code only from logical-server ticks. Work is
 * sliced into bounded batches so a large validation run does not move IRecipe.matches() onto a
 * foreign daemon thread merely to avoid blocking.</p>
 */
public final class FastSuiteSelfTest {
    public static final FastSuiteSelfTest INSTANCE = new FastSuiteSelfTest();

    private static final int DEFAULT_FUZZ_COUNT = 100000;
    private static final int QUICK_FUZZ_COUNT = 2000;
    public static final int MAX_FUZZ_COUNT = 250000;

    private static final int MAX_ALTERNATIVES_PER_INGREDIENT = 3;
    private static final int QUICK_SAMPLES_PER_CLASS = 5;
    private static final int POOL_RECIPES_PER_TICK = 96;
    private static final int QUICK_RECIPES_PER_TICK = 96;
    private static final int FULL_RECIPES_PER_TICK = 1;
    private static final int FUZZ_CASES_PER_TICK = 160;
    private static final int MAX_ITEM_POOL_SAMPLES = 4096;
    private static final int MAX_SAMPLES_PER_ITEM = 10;
    private static final int MAX_ORDERED_ORACLE_CASES = 64;
    private static final int ORDERED_FUZZ_SAMPLE_INTERVAL = 512;
    private static final long TICK_BUDGET_NANOS = 4_000_000L;

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private volatile TestSession session;

    private FastSuiteSelfTest() {}

    public static final class DummyContainer extends Container {
        @Override
        public boolean canInteractWith(EntityPlayer playerIn) {
            return true;
        }
    }

    public static InventoryCrafting createGrid(int width, int height) {
        return new InventoryCrafting(new DummyContainer(), width, height);
    }

    public enum TestMode {
        QUICK,
        FULL,
        RECIPES,
        FUZZ,
        REPLAY
    }

    public static final class TestResult {
        public int totalRecipes;
        public int testableRecipes;
        public int positiveCasesPassed;
        public int negativeCasesPassed;
        public int alternativeCasesPassed;
        public int fuzzCasesPassed;
        public int orderedCasesPassed;
        public int orderedCasesChecked;
        public int exceptionCasesMatched;
        public int skippedUntestable;
        public int mismatches;

        public final List<String> mismatchReports = new ArrayList<String>();
        public final Map<String, Integer> untestableClasses = new HashMap<String, Integer>();
        public long elapsedMs;
        public long masterSeed;

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n================ FastSuite Recipe Validation ================\n");
            sb.append(String.format("Total Registry Recipes:   %,d\n", totalRecipes));
            sb.append(String.format("Generic Testable:         %,d\n", testableRecipes));
            sb.append(String.format("Positive Cases Passed:    %,d\n", positiveCasesPassed));
            sb.append(String.format("Negative Cases Passed:    %,d\n", negativeCasesPassed));
            sb.append(String.format("Alternative Stacks:       %,d\n", alternativeCasesPassed));
            sb.append(String.format("Fuzz Cases Passed:        %,d\n", fuzzCasesPassed));
            sb.append(String.format("Ordered Oracle Cases:     %,d / %,d\n", orderedCasesPassed, orderedCasesChecked));
            sb.append(String.format("Matched Exception Cases:  %,d\n", exceptionCasesMatched));
            sb.append(String.format("Skipped / Untestable:     %,d\n", skippedUntestable));
            sb.append(String.format("TOTAL MISMATCHES:         %d\n", mismatches));
            if (masterSeed != 0L) {
                sb.append(String.format("Master Fuzz Seed:         %d\n", masterSeed));
            }
            sb.append(String.format("Elapsed Time:             %,d ms\n", elapsedMs));

            if (!untestableClasses.isEmpty()) {
                sb.append("\nTop Untestable Recipe Classes:\n");
                List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(untestableClasses.entrySet());
                Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
                    @Override
                    public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                        return Integer.compare(b.getValue(), a.getValue());
                    }
                });
                int max = Math.min(10, entries.size());
                for (int i = 0; i < max; i++) {
                    sb.append(String.format("  %,5d x %s\n", entries.get(i).getValue(), entries.get(i).getKey()));
                }
            }

            if (mismatches > 0) {
                sb.append("\nFirst Mismatches:\n");
                int max = Math.min(5, mismatchReports.size());
                for (int i = 0; i < max; i++) {
                    sb.append(mismatchReports.get(i)).append("\n");
                }
            }
            sb.append("=============================================================");
            return sb.toString();
        }
    }

    /** Start a tick-sliced logical-server test session. */
    public void start(TestMode mode, ICommandSender sender, World world, long seed, int customFuzzCount) {
        if (world == null || world.isRemote) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[FastSuite] Tests must run on the logical server."));
            return;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[FastSuite] A validation session is already running."));
            return;
        }

        int boundedFuzzCount = customFuzzCount;
        if (boundedFuzzCount > MAX_FUZZ_COUNT) boundedFuzzCount = MAX_FUZZ_COUNT;

        try {
            TestSession created = new TestSession(mode, sender, world, seed, boundedFuzzCount);
            session = created;
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW +
                "[FastSuite] Starting " + mode + " validation on logical-server ticks (bounded batches)."));
            if (created.result.masterSeed != 0L && mode != TestMode.REPLAY) {
                sender.sendMessage(new TextComponentString(TextFormatting.GRAY +
                    "[FastSuite] Master fuzz seed: " + created.result.masterSeed));
            }
        } catch (RuntimeException | LinkageError failure) {
            RUNNING.set(false);
            session = null;
            throw failure;
        }
    }

    /** Called from ServerTickEvent.END. */
    public void tick() {
        TestSession current = session;
        if (current == null) return;

        try {
            boolean finished = current.tick();
            if (finished) {
                finishSession(current);
            }
        } catch (Throwable throwable) {
            ReferenceCraftingScanner.rethrowFatal(throwable);
            FastSuite112.LOGGER.error("Error during FastSuite test execution", throwable);
            current.sender.sendMessage(new TextComponentString(TextFormatting.RED +
                "[FastSuite] Test aborted with error: " + throwable.getClass().getName() +
                (throwable.getMessage() == null ? "" : ": " + throwable.getMessage())));
            clearSession(current);
        }
    }

    public void cancel(String reason) {
        TestSession current = session;
        if (current == null) return;
        current.sender.sendMessage(new TextComponentString(TextFormatting.YELLOW +
            "[FastSuite] Validation cancelled: " + reason));
        clearSession(current);
    }

    private void finishSession(TestSession completed) {
        completed.result.elapsedMs = System.currentTimeMillis() - completed.startedMs;
        FastSuite112.LOGGER.info(completed.result.summary());

        if (completed.result.mismatches == 0) {
            completed.sender.sendMessage(new TextComponentString(TextFormatting.GREEN + String.format(
                "[FastSuite] PASSED! %d testable recipes (%d positive, %d negative, %d fuzz, %d ordered-oracle), 0 mismatches in %,d ms.",
                completed.result.testableRecipes,
                completed.result.positiveCasesPassed,
                completed.result.negativeCasesPassed,
                completed.result.fuzzCasesPassed,
                completed.result.orderedCasesChecked,
                completed.result.elapsedMs
            )));
        } else {
            completed.sender.sendMessage(new TextComponentString(TextFormatting.RED + String.format(
                "[FastSuite] FAILED! %d mismatches found in %,d ms. Check log for details.",
                completed.result.mismatches,
                completed.result.elapsedMs
            )));
            if (!completed.result.mismatchReports.isEmpty()) {
                completed.sender.sendMessage(new TextComponentString(TextFormatting.RED + completed.result.mismatchReports.get(0)));
            }
        }
        clearSession(completed);
    }

    private void clearSession(TestSession expected) {
        if (session == expected) session = null;
        RUNNING.set(false);
    }

    private enum Phase {
        PREPARE_POOL,
        RECIPES,
        FUZZ,
        CACHE_AB,
        REPLAY,
        DONE
    }

    private final class TestSession {
        private final TestMode mode;
        private final ICommandSender sender;
        private final World world;
        private final List<IRecipe> registry;
        private final TestResult result = new TestResult();
        private final long startedMs = System.currentTimeMillis();
        private final int fuzzTarget;
        private final long replaySeed;
        private final FuzzSeedSequence fuzzSeeds;
        private final Map<Class<?>, Integer> quickClassSampleCounts = new HashMap<Class<?>, Integer>();
        private final IdentityHashMap<IRecipe, SynthesisPlan> planCache = new IdentityHashMap<IRecipe, SynthesisPlan>();

        private final List<ItemStack> itemPool = new ArrayList<ItemStack>();
        private final Set<StackSampleKey> itemPoolKeys = new HashSet<StackSampleKey>();
        private final IdentityHashMap<Item, Integer> itemSampleCounts = new IdentityHashMap<Item, Integer>();

        private Phase phase;
        private int poolCursor;
        private int recipeCursor;
        private int fuzzCursor;
        private int lastProgressBucket = -1;

        // RECIPES mode is deliberately case-granular: one expensive vanilla-vs-indexed oracle case
        // is a work unit, rather than an entire recipe with several full-registry scans.
        private IRecipe activeRecipe;
        private SynthesisPlan activePlan;
        private int activeRecipeCase;

        private TestSession(TestMode mode, ICommandSender sender, World world, long seed, int customFuzzCount) {
            this.mode = mode;
            this.sender = sender;
            this.world = world;
            this.registry = new ArrayList<IRecipe>();
            for (IRecipe recipe : CraftingManager.REGISTRY) {
                this.registry.add(recipe);
            }
            this.result.totalRecipes = registry.size();

            this.replaySeed = mode == TestMode.REPLAY ? seed : 0L;
            long masterSeed = mode == TestMode.REPLAY ? 0L : (seed != 0L ? seed : new Random().nextLong());
            this.result.masterSeed = masterSeed;
            this.fuzzSeeds = mode == TestMode.REPLAY ? null : new FuzzSeedSequence(masterSeed);

            if (mode == TestMode.QUICK) {
                this.fuzzTarget = QUICK_FUZZ_COUNT;
            } else if (mode == TestMode.FULL || mode == TestMode.FUZZ) {
                this.fuzzTarget = customFuzzCount > 0 ? customFuzzCount : DEFAULT_FUZZ_COUNT;
            } else {
                this.fuzzTarget = 0;
            }

            if (needsPool(mode)) {
                this.phase = Phase.PREPARE_POOL;
            } else if (mode == TestMode.RECIPES) {
                this.phase = Phase.RECIPES;
            } else {
                this.phase = Phase.DONE;
            }
        }

        private boolean tick() {
            switch (phase) {
                case PREPARE_POOL:
                    preparePoolBatch();
                    break;
                case RECIPES:
                    testRecipeBatch();
                    break;
                case FUZZ:
                    runFuzzBatch();
                    break;
                case CACHE_AB:
                    testCacheAB(registry, planCache, world, result);
                    phase = Phase.DONE;
                    break;
                case REPLAY:
                    runReplayCase();
                    phase = Phase.DONE;
                    break;
                case DONE:
                    return true;
                default:
                    throw new IllegalStateException("Unknown FastSuite test phase: " + phase);
            }
            return phase == Phase.DONE;
        }

        private void preparePoolBatch() {
            int end = Math.min(registry.size(), poolCursor + POOL_RECIPES_PER_TICK);
            long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
            int processed = 0;
            while (poolCursor < end && itemPool.size() < MAX_ITEM_POOL_SAMPLES &&
                (processed == 0 || System.nanoTime() < deadline)) {
                IRecipe recipe = registry.get(poolCursor++);
                SynthesisPlan plan = resolvePlan(recipe);
                if (plan != null) collectPlanSamples(plan, itemPool, itemPoolKeys, itemSampleCounts);
                processed++;
            }
            sendProgress("building fuzz pool", poolCursor, registry.size());

            if (poolCursor >= registry.size() || itemPool.size() >= MAX_ITEM_POOL_SAMPLES) {
                ensureFallbackPool(itemPool);
                if (mode == TestMode.REPLAY) {
                    phase = Phase.REPLAY;
                } else if (mode == TestMode.FUZZ) {
                    phase = Phase.FUZZ;
                } else {
                    phase = Phase.RECIPES;
                }
                resetProgress();
            }
        }

        private void testRecipeBatch() {
            if (mode == TestMode.RECIPES) {
                testRecipesCaseBatch();
                return;
            }
            int budget = mode == TestMode.QUICK ? QUICK_RECIPES_PER_TICK : FULL_RECIPES_PER_TICK;
            int end = Math.min(registry.size(), recipeCursor + budget);
            long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
            int processed = 0;
            while (recipeCursor < end && (processed == 0 || System.nanoTime() < deadline)) {
                IRecipe recipe = registry.get(recipeCursor++);
                SynthesisPlan plan = resolvePlan(recipe);
                if (mode == TestMode.QUICK) {
                    testQuickRecipe(recipe, plan, world, result, quickClassSampleCounts);
                } else {
                    testOneRecipe(recipe, plan, world, mode, result);
                }
                processed++;
            }
            sendProgress("validating recipes", recipeCursor, registry.size());

            if (recipeCursor >= registry.size()) {
                resetProgress();
                if (mode == TestMode.QUICK || mode == TestMode.FULL) {
                    phase = Phase.FUZZ;
                } else {
                    phase = Phase.DONE;
                }
            }
        }

        private void testRecipesCaseBatch() {
            long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
            int processedCases = 0;
            while (recipeCursor < registry.size() && (processedCases == 0 || System.nanoTime() < deadline)) {
                if (activeRecipe == null) {
                    activeRecipe = registry.get(recipeCursor);
                    activePlan = resolvePlan(activeRecipe);
                    activeRecipeCase = 0;
                    if (activePlan == null) {
                        recordUntestable(activeRecipe, result);
                        finishActiveRecipe();
                        processedCases++;
                        continue;
                    }
                    result.testableRecipes++;
                }

                runOneRecipesModeCase();
                processedCases++;
            }
            sendProgress("validating recipes", recipeCursor, registry.size());
            if (recipeCursor >= registry.size() && activeRecipe == null) {
                resetProgress();
                phase = Phase.DONE;
            }
        }

        private void runOneRecipesModeCase() {
            switch (activeRecipeCase++) {
                case 0:
                    if (verifyLookup(activePlan.createGrid(), world, result, "ORIGINAL", 0L, false)) {
                        result.positiveCasesPassed++;
                    }
                    return;
                case 1: {
                    InventoryCrafting missing = activePlan.createMissingGrid();
                    if (missing != null && verifyLookup(missing, world, result, "NEGATIVE_MISSING", 0L, false)) {
                        result.negativeCasesPassed++;
                    }
                    return;
                }
                case 2: {
                    InventoryCrafting wrong = activePlan.createWrongItemGrid();
                    if (wrong != null && verifyLookup(wrong, world, result, "NEGATIVE_WRONG_ITEM", 0L, false)) {
                        result.negativeCasesPassed++;
                    }
                    return;
                }
                case 3:
                    if (!activePlan.isShaped) {
                        InventoryCrafting extra = activePlan.createExtraItemGrid();
                        if (extra != null && verifyLookup(extra, world, result, "EXTRA_ITEM", 0L, false)) {
                            result.negativeCasesPassed++;
                        }
                    }
                    finishActiveRecipe();
                    return;
                default:
                    finishActiveRecipe();
            }
        }

        private void finishActiveRecipe() {
            activeRecipe = null;
            activePlan = null;
            activeRecipeCase = 0;
            recipeCursor++;
        }

        private void runFuzzBatch() {
            int end = Math.min(fuzzTarget, fuzzCursor + FUZZ_CASES_PER_TICK);
            long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
            int processed = 0;
            while (fuzzCursor < end && (processed == 0 || System.nanoTime() < deadline)) {
                long caseSeed = fuzzSeeds.nextCaseSeed();
                boolean orderedSample = (fuzzCursor % ORDERED_FUZZ_SAMPLE_INTERVAL) == 0 && canRunOrderedOracle(result);
                InventoryCrafting grid = buildFuzzGrid(registry, planCache, itemPool, caseSeed);
                if (verifyLookup(grid, world, result, "FUZZ", caseSeed, orderedSample)) {
                    result.fuzzCasesPassed++;
                }
                fuzzCursor++;
                processed++;
            }
            sendProgress("fuzzing", fuzzCursor, fuzzTarget);

            if (fuzzCursor >= fuzzTarget) {
                resetProgress();
                phase = mode == TestMode.FULL ? Phase.CACHE_AB : Phase.DONE;
            }
        }

        private void runReplayCase() {
            // Critical alpha.10 regression: the recorded case seed is consumed directly. It is not
            // fed into a master RNG and transformed by nextLong() a second time.
            long exactCaseSeed = FuzzSeedSequence.replayCaseSeed(replaySeed);
            InventoryCrafting grid = buildFuzzGrid(registry, planCache, itemPool, exactCaseSeed);
            verifyLookup(grid, world, result, "REPLAY", exactCaseSeed, true);
        }

        private SynthesisPlan resolvePlan(IRecipe recipe) {
            if (planCache.containsKey(recipe)) return planCache.get(recipe);
            SynthesisPlan plan = synthesizePlan(recipe);
            planCache.put(recipe, plan);
            return plan;
        }

        private void sendProgress(String label, int done, int total) {
            if (total <= 0) return;
            int bucket = Math.min(10, (done * 10) / total);
            if (bucket == lastProgressBucket) return;
            lastProgressBucket = bucket;
            if (bucket == 0 || bucket == 10) return;
            FastSuite112.LOGGER.info("[FastSuite] {}: {} / {} ({}%)", label, done, total, bucket * 10);
        }

        private void resetProgress() {
            lastProgressBucket = -1;
        }
    }

    private static boolean needsPool(TestMode mode) {
        return mode == TestMode.QUICK || mode == TestMode.FULL || mode == TestMode.FUZZ || mode == TestMode.REPLAY;
    }

    private static void collectPlanSamples(SynthesisPlan plan,
                                           List<ItemStack> pool,
                                           Set<StackSampleKey> seen,
                                           IdentityHashMap<Item, Integer> perItemCounts) {
        if (pool.size() >= MAX_ITEM_POOL_SAMPLES) return;
        for (List<ItemStack> alternatives : plan.ingredientAlternatives) {
            if (alternatives == null) continue;
            for (ItemStack stack : alternatives) {
                if (stack == null || stack.isEmpty()) continue;
                Item item = stack.getItem();
                Integer count = perItemCounts.get(item);
                if (count != null && count >= MAX_SAMPLES_PER_ITEM) continue;

                StackSampleKey key = StackSampleKey.of(stack);
                if (!seen.add(key)) continue;

                ItemStack sample = stack.copy();
                sample.setCount(1);
                pool.add(sample);
                perItemCounts.put(item, count == null ? 1 : count + 1);
                if (pool.size() >= MAX_ITEM_POOL_SAMPLES) return;
            }
        }
    }

    private static List<ItemStack> expandWildcardSample(ItemStack stack) {
        if (stack.getMetadata() != OreDictionary.WILDCARD_VALUE) {
            return Collections.singletonList(stack);
        }
        List<ItemStack> samples = new ArrayList<ItemStack>(2);
        for (int meta = 0; meta <= 1; meta++) {
            ItemStack concrete = new ItemStack(stack.getItem(), 1, meta);
            if (stack.hasTagCompound()) concrete.setTagCompound(stack.getTagCompound().copy());
            samples.add(concrete);
        }
        return samples;
    }

    private static void ensureFallbackPool(List<ItemStack> pool) {
        if (!pool.isEmpty()) return;
        pool.add(new ItemStack(Items.STICK));
        pool.add(new ItemStack(Items.IRON_INGOT));
        pool.add(new ItemStack(Items.GOLD_INGOT));
        pool.add(new ItemStack(Items.DIAMOND));
    }

    private static final class StackSampleKey {
        private final Item item;
        private final int meta;
        private final boolean hasNbt;
        private final int nbtShapeHash;
        private final int nbtValueHash;

        private StackSampleKey(Item item, int meta, boolean hasNbt, int nbtShapeHash, int nbtValueHash) {
            this.item = item;
            this.meta = meta;
            this.hasNbt = hasNbt;
            this.nbtShapeHash = nbtShapeHash;
            this.nbtValueHash = nbtValueHash;
        }

        private static StackSampleKey of(ItemStack stack) {
            NBTTagCompound tag = stack.getTagCompound();
            int shapeHash = tag == null ? 0 : tag.getKeySet().hashCode();
            int valueHash = tag == null ? 0 : tag.hashCode();
            return new StackSampleKey(stack.getItem(), stack.getMetadata(), tag != null, shapeHash, valueHash);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof StackSampleKey)) return false;
            StackSampleKey that = (StackSampleKey) other;
            return item == that.item && meta == that.meta && hasNbt == that.hasNbt &&
                nbtShapeHash == that.nbtShapeHash && nbtValueHash == that.nbtValueHash;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(item);
            result = 31 * result + meta;
            result = 31 * result + (hasNbt ? 1 : 0);
            result = 31 * result + nbtShapeHash;
            result = 31 * result + nbtValueHash;
            return result;
        }
    }

    private void testQuickRecipe(IRecipe recipe,
                                 SynthesisPlan plan,
                                 World world,
                                 TestResult result,
                                 Map<Class<?>, Integer> classSampleCounts) {
        Class<?> cls = recipe.getClass();
        Integer count = classSampleCounts.get(cls);
        if (count != null && count >= QUICK_SAMPLES_PER_CLASS) return;

        if (plan == null) {
            recordUntestable(recipe, result);
            return;
        }

        classSampleCounts.put(cls, count == null ? 1 : count + 1);
        result.testableRecipes++;

        if (verifyLookup(plan.createGrid(), world, result, "ORIGINAL", 0L, false)) {
            result.positiveCasesPassed++;
        }

        InventoryCrafting missing = plan.createMissingGrid();
        if (missing != null && verifyLookup(missing, world, result, "NEGATIVE_MISSING", 0L, false)) {
            result.negativeCasesPassed++;
        }

        InventoryCrafting wrong = plan.createWrongItemGrid();
        if (wrong != null && verifyLookup(wrong, world, result, "NEGATIVE_WRONG_ITEM", 0L, false)) {
            result.negativeCasesPassed++;
        }
    }

    private void testOneRecipe(IRecipe recipe, SynthesisPlan plan, World world, TestMode mode, TestResult result) {
        if (plan == null) {
            recordUntestable(recipe, result);
            return;
        }
        result.testableRecipes++;

        boolean ordered = mode == TestMode.FULL && canRunOrderedOracle(result);
        if (verifyLookup(plan.createGrid(), world, result, "ORIGINAL", 0L, ordered)) {
            result.positiveCasesPassed++;
        }

        if (mode == TestMode.FULL && plan.hasAlternatives()) {
            List<InventoryCrafting> grids = plan.createAlternativeGrids(MAX_ALTERNATIVES_PER_INGREDIENT);
            for (InventoryCrafting grid : grids) {
                if (verifyLookup(grid, world, result, "ALTERNATE_INGREDIENT", 0L, false)) {
                    result.alternativeCasesPassed++;
                }
            }
        }

        InventoryCrafting missing = plan.createMissingGrid();
        if (missing != null && verifyLookup(missing, world, result, "NEGATIVE_MISSING", 0L, false)) {
            result.negativeCasesPassed++;
        }

        InventoryCrafting wrong = plan.createWrongItemGrid();
        if (wrong != null && verifyLookup(wrong, world, result, "NEGATIVE_WRONG_ITEM", 0L, false)) {
            result.negativeCasesPassed++;
        }

        if (!plan.isShaped) {
            InventoryCrafting extra = plan.createExtraItemGrid();
            if (extra != null && verifyLookup(extra, world, result, "EXTRA_ITEM", 0L, false)) {
                result.negativeCasesPassed++;
            }
        }

        if (plan.isShaped && mode == TestMode.FULL) {
            InventoryCrafting shifted = plan.createWrongPosGrid();
            if (shifted != null && verifyLookup(shifted, world, result, "SHAPED_WRONG_POSITION", 0L, false)) {
                result.negativeCasesPassed++;
            }
        }

        if (mode == TestMode.FULL && plan.hasNBT()) {
            InventoryCrafting nbtGrid = plan.createMutatedNBTGrid();
            if (nbtGrid != null && verifyLookup(nbtGrid, world, result, "NBT_MUTATION", 0L, false)) {
                result.alternativeCasesPassed++;
            }
        }

        if (mode == TestMode.FULL && plan.hasMetadataVariation()) {
            InventoryCrafting metaGrid = plan.createMetadataAlternativeGrid();
            if (metaGrid != null && verifyLookup(metaGrid, world, result, "METADATA_ALTERNATIVE", 0L, false)) {
                result.alternativeCasesPassed++;
            }
        }
    }

    private static void recordUntestable(IRecipe recipe, TestResult result) {
        result.skippedUntestable++;
        String cls = recipe.getClass().getName();
        Integer count = result.untestableClasses.get(cls);
        result.untestableClasses.put(cls, count == null ? 1 : count + 1);
    }

    private void testCacheAB(List<IRecipe> registry, IdentityHashMap<IRecipe, SynthesisPlan> planCache, World world, TestResult result) {
        if (registry.size() < 2) return;
        SynthesisPlan planA = null;
        SynthesisPlan planB = null;
        for (IRecipe recipe : registry) {
            SynthesisPlan plan = planCache.containsKey(recipe) ? planCache.get(recipe) : synthesizePlan(recipe);
            if (!planCache.containsKey(recipe)) planCache.put(recipe, plan);
            if (plan != null) {
                if (planA == null) planA = plan;
                else {
                    planB = plan;
                    break;
                }
            }
        }
        if (planA == null || planB == null) return;

        verifyLookup(planA.createGrid(), world, result, "CACHE_A1", 0L, false);
        verifyLookup(planB.createGrid(), world, result, "CACHE_B", 0L, false);
        verifyLookup(planA.createGrid(), world, result, "CACHE_A2", 0L, false);
    }

    private static InventoryCrafting buildFuzzGrid(List<IRecipe> registry, IdentityHashMap<IRecipe, SynthesisPlan> planCache, List<ItemStack> pool, long caseSeed) {
        Random rng = new Random(caseSeed);
        InventoryCrafting grid = createGrid(3, 3);
        boolean fromMutation = rng.nextDouble() < 0.70D && !registry.isEmpty();

        if (fromMutation) {
            IRecipe baseRecipe = registry.get(rng.nextInt(registry.size()));
            SynthesisPlan plan;
            if (planCache.containsKey(baseRecipe)) {
                plan = planCache.get(baseRecipe);
            } else {
                plan = synthesizePlan(baseRecipe);
                planCache.put(baseRecipe, plan);
            }
            if (plan != null) {
                copyContents(plan.createGrid(), grid);
                int mutations = 1 + rng.nextInt(3);
                for (int i = 0; i < mutations; i++) {
                    applyFuzzMutation(grid, plan, pool, rng);
                }
                return grid;
            }
        }

        fillRandomSlots(grid, pool, rng);
        return grid;
    }

    private static void applyFuzzMutation(InventoryCrafting grid, SynthesisPlan plan, List<ItemStack> pool, Random rng) {
        int operation = rng.nextInt(8);
        switch (operation) {
            case 0:
                grid.setInventorySlotContents(rng.nextInt(9), ItemStack.EMPTY);
                return;
            case 1:
                if (!pool.isEmpty()) {
                    grid.setInventorySlotContents(rng.nextInt(9), pool.get(rng.nextInt(pool.size())).copy());
                }
                return;
            case 2:
                if (mutateMetadata(grid, pool, rng)) return;
                break;
            case 3:
                if (mutateNbt(grid, rng)) return;
                break;
            case 4:
                if (mutateCount(grid, rng)) return;
                break;
            case 5:
                if (plan.applyRandomAlternative(grid, rng)) return;
                break;
            case 6:
                if (plan.isShaped) {
                    InventoryCrafting shifted = plan.createWrongPosGrid();
                    if (shifted != null) {
                        copyContents(shifted, grid);
                        return;
                    }
                } else if (duplicateOccupiedSlot(grid, rng)) {
                    return;
                }
                break;
            case 7:
                if (addExtraOccupiedSlot(grid, pool, rng)) return;
                break;
            default:
                break;
        }

        if (!pool.isEmpty()) {
            grid.setInventorySlotContents(rng.nextInt(9), pool.get(rng.nextInt(pool.size())).copy());
        }
    }

    private static boolean mutateMetadata(InventoryCrafting grid, List<ItemStack> pool, Random rng) {
        List<Integer> occupied = occupiedSlots(grid);
        if (occupied.isEmpty()) return false;
        int slot = occupied.get(rng.nextInt(occupied.size()));
        ItemStack original = grid.getStackInSlot(slot);

        List<ItemStack> sameItemDifferentMeta = new ArrayList<ItemStack>();
        for (ItemStack sample : pool) {
            if (sample.getItem() == original.getItem() && sample.getMetadata() != original.getMetadata()) {
                sameItemDifferentMeta.add(sample);
            }
        }
        if (!sameItemDifferentMeta.isEmpty()) {
            ItemStack replacement = sameItemDifferentMeta.get(rng.nextInt(sameItemDifferentMeta.size())).copy();
            replacement.setCount(original.getCount());
            grid.setInventorySlotContents(slot, replacement);
            return true;
        }

        // Do not fabricate arbitrary metadata values for capability/NBT-heavy modded items.
        // Metadata fuzzing is only useful when the recipe corpus exposed a real alternate stack.
        return false;
    }

    private static boolean mutateNbt(InventoryCrafting grid, Random rng) {
        List<Integer> occupied = occupiedSlots(grid);
        if (occupied.isEmpty()) return false;
        int slot = occupied.get(rng.nextInt(occupied.size()));
        ItemStack replacement = grid.getStackInSlot(slot).copy();
        if (!replacement.hasTagCompound()) {
            // Adding synthetic NBT to arbitrary modded stacks can instantiate invalid capability/genetic
            // states. Recipe-derived NBT testing below mutates only stacks that already carry NBT.
            return false;
        }
        NBTTagCompound tag = replacement.getTagCompound().copy();
        tag.setLong("fastsuite_test_probe", rng.nextLong());
        replacement.setTagCompound(tag);
        grid.setInventorySlotContents(slot, replacement);
        return true;
    }

    private static boolean mutateCount(InventoryCrafting grid, Random rng) {
        List<Integer> occupied = occupiedSlots(grid);
        if (occupied.isEmpty()) return false;
        int slot = occupied.get(rng.nextInt(occupied.size()));
        ItemStack replacement = grid.getStackInSlot(slot).copy();
        replacement.setCount(rng.nextBoolean() ? 2 : Math.min(64, Math.max(2, replacement.getCount() + 1)));
        grid.setInventorySlotContents(slot, replacement);
        return true;
    }

    private static boolean duplicateOccupiedSlot(InventoryCrafting grid, Random rng) {
        List<Integer> occupied = occupiedSlots(grid);
        List<Integer> empty = emptySlots(grid);
        if (occupied.isEmpty() || empty.isEmpty()) return false;
        int source = occupied.get(rng.nextInt(occupied.size()));
        int target = empty.get(rng.nextInt(empty.size()));
        grid.setInventorySlotContents(target, grid.getStackInSlot(source).copy());
        return true;
    }

    private static boolean addExtraOccupiedSlot(InventoryCrafting grid, List<ItemStack> pool, Random rng) {
        if (pool.isEmpty()) return false;
        List<Integer> empty = emptySlots(grid);
        if (empty.isEmpty()) return false;
        int target = empty.get(rng.nextInt(empty.size()));
        grid.setInventorySlotContents(target, pool.get(rng.nextInt(pool.size())).copy());
        return true;
    }

    private static List<Integer> occupiedSlots(InventoryCrafting grid) {
        List<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            if (!grid.getStackInSlot(i).isEmpty()) slots.add(i);
        }
        return slots;
    }

    private static List<Integer> emptySlots(InventoryCrafting grid) {
        List<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            if (grid.getStackInSlot(i).isEmpty()) slots.add(i);
        }
        return slots;
    }

    private static void fillRandomSlots(InventoryCrafting grid, List<ItemStack> pool, Random rng) {
        if (pool.isEmpty()) return;
        int occupied = 1 + rng.nextInt(9);
        for (int i = 0; i < occupied; i++) {
            int slot = rng.nextInt(9);
            ItemStack sample = pool.get(rng.nextInt(pool.size())).copy();
            if (rng.nextInt(8) == 0) sample.setCount(2 + rng.nextInt(3));
            grid.setInventorySlotContents(slot, sample);
        }
    }

    private boolean verifyLookup(InventoryCrafting grid,
                                 World world,
                                 TestResult result,
                                 String testName,
                                 long caseSeed,
                                 boolean verifyOrderedMatches) {
        FirstMatchOutcome vanilla = ReferenceCraftingScanner.findFirstOutcome(
            CraftingManager.REGISTRY,
            copyGrid(grid),
            world
        );
        FirstMatchOutcome fast = findFirstFast(copyGrid(grid), world);

        if (!sameFirstOutcome(vanilla, fast)) {
            recordMismatch(result, buildMismatchReport(grid, vanilla, fast, null, null, testName, caseSeed));
            return false;
        }
        if (vanilla.kind == OutcomeKind.THREW) {
            result.exceptionCasesMatched++;
        }

        if (verifyOrderedMatches) {
            result.orderedCasesChecked++;
            AllMatchesOutcome vanillaAll = ReferenceCraftingScanner.findAllOutcome(
                CraftingManager.REGISTRY,
                copyGrid(grid),
                world
            );
            AllMatchesOutcome fastAll = findAllFast(copyGrid(grid), world);
            if (!sameAllOutcome(vanillaAll, fastAll)) {
                recordMismatch(result, buildMismatchReport(grid, vanilla, fast, vanillaAll, fastAll, testName + "_ORDERED", caseSeed));
                return false;
            }
            result.orderedCasesPassed++;
        }

        return true;
    }

    private static FirstMatchOutcome findFirstFast(InventoryCrafting matrix, World world) {
        try {
            Iterable<IRecipe> candidates = RecipeIndex.INSTANCE.candidatesFor(matrix);
            return ReferenceCraftingScanner.findFirstOutcome(candidates, matrix, world);
        } catch (Throwable throwable) {
            ReferenceCraftingScanner.rethrowFatal(throwable);
            // Iterator/backend failures are FastSuite outcomes too. They have no responsible recipe,
            // so comparison against a vanilla recipe exception will correctly fail.
            return FirstMatchOutcome.threw(null, throwable);
        }
    }

    private static AllMatchesOutcome findAllFast(InventoryCrafting matrix, World world) {
        try {
            Iterable<IRecipe> candidates = RecipeIndex.INSTANCE.candidatesFor(matrix);
            return ReferenceCraftingScanner.findAllOutcome(candidates, matrix, world);
        } catch (Throwable throwable) {
            ReferenceCraftingScanner.rethrowFatal(throwable);
            List<IRecipe> none = Collections.emptyList();
            return AllMatchesOutcome.threw(none, null, throwable);
        }
    }

    private static boolean sameFirstOutcome(FirstMatchOutcome expected, FirstMatchOutcome actual) {
        if (expected.kind != actual.kind) return false;
        if (expected.kind == OutcomeKind.MATCH) return expected.recipe == actual.recipe;
        if (expected.kind == OutcomeKind.THREW) {
            return expected.exceptionClass == actual.exceptionClass && expected.throwingRecipe == actual.throwingRecipe;
        }
        return true;
    }

    private static boolean sameAllOutcome(AllMatchesOutcome expected, AllMatchesOutcome actual) {
        if (expected.exceptionClass != actual.exceptionClass) return false;
        if (expected.throwingRecipe != actual.throwingRecipe) return false;
        if (expected.matches.size() != actual.matches.size()) return false;
        for (int i = 0; i < expected.matches.size(); i++) {
            if (expected.matches.get(i) != actual.matches.get(i)) return false;
        }
        return true;
    }

    private static boolean canRunOrderedOracle(TestResult result) {
        return result.orderedCasesChecked < MAX_ORDERED_ORACLE_CASES;
    }

    private static void recordMismatch(TestResult result, String report) {
        result.mismatches++;
        if (result.mismatchReports.size() < 100) result.mismatchReports.add(report);
        FastSuite112.LOGGER.error(report);
    }

    private static String buildMismatchReport(InventoryCrafting grid,
                                              FirstMatchOutcome expected,
                                              FirstMatchOutcome actual,
                                              AllMatchesOutcome expectedAll,
                                              AllMatchesOutcome actualAll,
                                              String testType,
                                              long caseSeed) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n================== FASTSUITE MISMATCH ==================\n");
        sb.append("Test Type: ").append(testType).append("\n");
        if (caseSeed != 0L || testType.startsWith("FUZZ") || testType.startsWith("REPLAY")) {
            sb.append("Replay Seed: ").append(caseSeed).append("\n");
            sb.append("Replay Command: /fastsuite-dev test replay ").append(caseSeed).append("\n");
        }
        sb.append("Grid Size: ").append(grid.getWidth()).append("x").append(grid.getHeight()).append("\n");
        sb.append("Grid Items:\n");
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack stack = grid.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ResourceLocation name = stack.getItem().getRegistryName();
                sb.append(String.format(
                    "  Slot %d: %s meta=%d count=%d nbt=%s (%s)\n",
                    i,
                    name == null ? "<unregistered>" : name.toString(),
                    stack.getMetadata(),
                    stack.getCount(),
                    summarizeNbt(stack),
                    stack.getDisplayName()
                ));
            }
        }
        sb.append("Expected First: ").append(describeFirstOutcome(expected)).append("\n");
        sb.append("Actual First:   ").append(describeFirstOutcome(actual)).append("\n");

        if (expected.kind == OutcomeKind.MATCH && expected.recipe != null) {
            RecipeSafetyClassifier.Safety safety = RecipeSafetyClassifier.INSTANCE.classifyRecipe(expected.recipe.getClass());
            sb.append("Expected Safety: ").append(safety).append("\n");
        }

        if (expectedAll != null || actualAll != null) {
            sb.append("Expected Ordered: ").append(describeAllOutcome(expectedAll)).append("\n");
            sb.append("Actual Ordered:   ").append(describeAllOutcome(actualAll)).append("\n");
        }
        sb.append("========================================================");
        return sb.toString();
    }

    private static String describeFirstOutcome(FirstMatchOutcome outcome) {
        if (outcome == null) return "<not-run>";
        if (outcome.kind == OutcomeKind.NO_MATCH) return "NO_MATCH";
        if (outcome.kind == OutcomeKind.MATCH) return "MATCH(" + describeRecipe(outcome.recipe) + ")";
        return "THREW(" + className(outcome.exceptionClass) + ", recipe=" + describeRecipe(outcome.throwingRecipe) +
            (outcome.exceptionMessage == null ? "" : ", message=" + outcome.exceptionMessage) + ")";
    }

    private static String describeAllOutcome(AllMatchesOutcome outcome) {
        if (outcome == null) return "<not-run>";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        int limit = Math.min(12, outcome.matches.size());
        for (int i = 0; i < limit; i++) {
            if (i != 0) sb.append(", ");
            sb.append(describeRecipe(outcome.matches.get(i)));
        }
        if (outcome.matches.size() > limit) sb.append(", ... +").append(outcome.matches.size() - limit);
        sb.append(']');
        if (outcome.threw()) {
            sb.append(" THEN THREW(").append(className(outcome.exceptionClass)).append(", recipe=")
                .append(describeRecipe(outcome.throwingRecipe)).append(')');
        }
        return sb.toString();
    }

    private static String describeRecipe(IRecipe recipe) {
        if (recipe == null) return "null";
        ResourceLocation name = recipe.getRegistryName();
        return (name == null ? "<unregistered>" : name.toString()) + " [" + recipe.getClass().getName() + "]";
    }

    private static String className(Class<?> cls) {
        return cls == null ? "null" : cls.getName();
    }

    private static String summarizeNbt(ItemStack stack) {
        if (!stack.hasTagCompound()) return "none";
        NBTTagCompound tag = stack.getTagCompound();
        return "keys=" + tag.getKeySet() + ",hash=" + tag.hashCode();
    }

    private static InventoryCrafting copyGrid(InventoryCrafting source) {
        InventoryCrafting copy = createGrid(source.getWidth(), source.getHeight());
        copyContents(source, copy);
        return copy;
    }

    private static void copyContents(InventoryCrafting source, InventoryCrafting target) {
        int size = Math.min(source.getSizeInventory(), target.getSizeInventory());
        for (int i = 0; i < size; i++) {
            ItemStack stack = source.getStackInSlot(i);
            target.setInventorySlotContents(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
    }

    private static final class SynthesisPlan {
        public final boolean isShaped;
        public final int width;
        public final int height;
        public final List<List<ItemStack>> ingredientAlternatives;

        private SynthesisPlan(boolean isShaped, int width, int height, List<List<ItemStack>> ingredientAlternatives) {
            this.isShaped = isShaped;
            this.width = width;
            this.height = height;
            this.ingredientAlternatives = ingredientAlternatives;
        }

        public InventoryCrafting createGrid() {
            InventoryCrafting grid = FastSuiteSelfTest.createGrid(3, 3);
            if (isShaped) {
                for (int row = 0; row < height; row++) {
                    for (int col = 0; col < width; col++) {
                        int index = row * width + col;
                        if (index < ingredientAlternatives.size()) {
                            List<ItemStack> alternatives = ingredientAlternatives.get(index);
                            if (alternatives != null && !alternatives.isEmpty()) {
                                grid.setInventorySlotContents(row * 3 + col, alternatives.get(0).copy());
                            }
                        }
                    }
                }
            } else {
                int slot = 0;
                for (List<ItemStack> alternatives : ingredientAlternatives) {
                    if (alternatives != null && !alternatives.isEmpty() && slot < 9) {
                        grid.setInventorySlotContents(slot++, alternatives.get(0).copy());
                    }
                }
            }
            return grid;
        }

        public boolean hasAlternatives() {
            for (List<ItemStack> alternatives : ingredientAlternatives) {
                if (alternatives != null && alternatives.size() > 1) return true;
            }
            return false;
        }

        public boolean hasMetadataVariation() {
            for (List<ItemStack> alternatives : ingredientAlternatives) {
                if (alternatives == null || alternatives.size() < 2) continue;
                for (int i = 0; i < alternatives.size(); i++) {
                    for (int j = i + 1; j < alternatives.size(); j++) {
                        ItemStack a = alternatives.get(i);
                        ItemStack b = alternatives.get(j);
                        if (a.getItem() == b.getItem() && a.getMetadata() != b.getMetadata()) return true;
                    }
                }
            }
            return false;
        }

        public List<InventoryCrafting> createAlternativeGrids(int maxAlternatives) {
            List<InventoryCrafting> list = new ArrayList<InventoryCrafting>();
            for (int i = 0; i < ingredientAlternatives.size(); i++) {
                List<ItemStack> alternatives = ingredientAlternatives.get(i);
                if (alternatives != null && alternatives.size() > 1) {
                    int limit = Math.min(alternatives.size(), maxAlternatives);
                    for (int alternativeIndex = 1; alternativeIndex < limit; alternativeIndex++) {
                        InventoryCrafting grid = createGrid();
                        int slot = gridSlotForIngredient(i);
                        if (slot >= 0 && slot < 9) {
                            grid.setInventorySlotContents(slot, alternatives.get(alternativeIndex).copy());
                            list.add(grid);
                        }
                    }
                }
            }
            return list;
        }

        public boolean applyRandomAlternative(InventoryCrafting grid, Random rng) {
            List<Integer> candidates = new ArrayList<Integer>();
            for (int i = 0; i < ingredientAlternatives.size(); i++) {
                List<ItemStack> alternatives = ingredientAlternatives.get(i);
                if (alternatives != null && alternatives.size() > 1) candidates.add(i);
            }
            if (candidates.isEmpty()) return false;
            int ingredient = candidates.get(rng.nextInt(candidates.size()));
            List<ItemStack> alternatives = ingredientAlternatives.get(ingredient);
            int slot = gridSlotForIngredient(ingredient);
            if (slot < 0 || slot >= 9) return false;
            grid.setInventorySlotContents(slot, alternatives.get(1 + rng.nextInt(alternatives.size() - 1)).copy());
            return true;
        }

        public InventoryCrafting createMetadataAlternativeGrid() {
            for (int i = 0; i < ingredientAlternatives.size(); i++) {
                List<ItemStack> alternatives = ingredientAlternatives.get(i);
                if (alternatives == null || alternatives.size() < 2) continue;
                ItemStack first = alternatives.get(0);
                for (int j = 1; j < alternatives.size(); j++) {
                    ItemStack candidate = alternatives.get(j);
                    if (candidate.getItem() == first.getItem() && candidate.getMetadata() != first.getMetadata()) {
                        InventoryCrafting grid = createGrid();
                        int slot = gridSlotForIngredient(i);
                        if (slot >= 0 && slot < 9) {
                            grid.setInventorySlotContents(slot, candidate.copy());
                            return grid;
                        }
                    }
                }
            }
            return null;
        }

        private int gridSlotForIngredient(int ingredientIndex) {
            if (isShaped) {
                return (ingredientIndex / width) * 3 + (ingredientIndex % width);
            }
            int slot = 0;
            for (int i = 0; i < ingredientAlternatives.size(); i++) {
                List<ItemStack> alternatives = ingredientAlternatives.get(i);
                if (alternatives == null || alternatives.isEmpty()) continue;
                if (i == ingredientIndex) return slot;
                slot++;
            }
            return -1;
        }

        public InventoryCrafting createMissingGrid() {
            InventoryCrafting grid = createGrid();
            for (int i = 0; i < 9; i++) {
                if (!grid.getStackInSlot(i).isEmpty()) {
                    grid.setInventorySlotContents(i, ItemStack.EMPTY);
                    return grid;
                }
            }
            return null;
        }

        public InventoryCrafting createWrongItemGrid() {
            InventoryCrafting grid = createGrid();
            for (int i = 0; i < 9; i++) {
                if (!grid.getStackInSlot(i).isEmpty()) {
                    grid.setInventorySlotContents(i, new ItemStack(Blocks.BEDROCK));
                    return grid;
                }
            }
            return null;
        }

        public InventoryCrafting createExtraItemGrid() {
            InventoryCrafting grid = createGrid();
            for (int i = 0; i < 9; i++) {
                if (grid.getStackInSlot(i).isEmpty()) {
                    grid.setInventorySlotContents(i, new ItemStack(Blocks.BARRIER));
                    return grid;
                }
            }
            return null;
        }

        public InventoryCrafting createWrongPosGrid() {
            if (!isShaped || (width >= 3 && height >= 3)) return null;
            InventoryCrafting grid = FastSuiteSelfTest.createGrid(3, 3);
            int rowOffset = height < 3 ? 1 : 0;
            int colOffset = width < 3 ? 1 : 0;
            if (rowOffset == 0 && colOffset == 0) return null;

            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int index = row * width + col;
                    if (index < ingredientAlternatives.size()) {
                        List<ItemStack> alternatives = ingredientAlternatives.get(index);
                        if (alternatives != null && !alternatives.isEmpty()) {
                            grid.setInventorySlotContents((row + rowOffset) * 3 + (col + colOffset), alternatives.get(0).copy());
                        }
                    }
                }
            }
            return grid;
        }

        public boolean hasNBT() {
            for (List<ItemStack> alternatives : ingredientAlternatives) {
                if (alternatives == null) continue;
                for (ItemStack stack : alternatives) {
                    if (stack.hasTagCompound()) return true;
                }
            }
            return false;
        }

        public InventoryCrafting createMutatedNBTGrid() {
            InventoryCrafting grid = createGrid();
            for (int i = 0; i < 9; i++) {
                ItemStack stack = grid.getStackInSlot(i);
                if (!stack.isEmpty() && stack.hasTagCompound()) {
                    ItemStack clone = stack.copy();
                    NBTTagCompound tag = clone.getTagCompound().copy();
                    tag.setBoolean("fastsuite_test_probe", true);
                    clone.setTagCompound(tag);
                    grid.setInventorySlotContents(i, clone);
                    return grid;
                }
            }
            return null;
        }
    }

    private static SynthesisPlan synthesizePlan(IRecipe recipe) {
        try {
            NonNullList<Ingredient> ingredients = recipe.getIngredients();
            if (ingredients == null || ingredients.isEmpty()) return null;

            boolean shaped = recipe instanceof IShapedRecipe;
            int width = 3;
            int height = 3;
            if (shaped) {
                IShapedRecipe shapedRecipe = (IShapedRecipe) recipe;
                width = shapedRecipe.getRecipeWidth();
                height = shapedRecipe.getRecipeHeight();
                if (width > 3 || height > 3 || width <= 0 || height <= 0) return null;
            }

            List<List<ItemStack>> alternatives = new ArrayList<List<ItemStack>>();
            boolean sawValid = false;

            for (Ingredient ingredient : ingredients) {
                if (ingredient == null || ingredient == Ingredient.EMPTY) {
                    alternatives.add(Collections.<ItemStack>emptyList());
                    continue;
                }
                ItemStack[] matching = ingredient.getMatchingStacks();
                if (matching == null || matching.length == 0) return null;

                List<ItemStack> list = new ArrayList<ItemStack>();
                Set<StackSampleKey> seen = new HashSet<StackSampleKey>();
                for (ItemStack stack : matching) {
                    if (stack == null || stack.isEmpty()) continue;
                    for (ItemStack concrete : expandWildcardSample(stack)) {
                        if (seen.add(StackSampleKey.of(concrete))) {
                            list.add(concrete.copy());
                        }
                    }
                }
                if (list.isEmpty()) return null;
                sawValid = true;
                alternatives.add(list);
            }

            if (!sawValid) return null;
            return new SynthesisPlan(shaped, width, height, alternatives);
        } catch (Throwable throwable) {
            ReferenceCraftingScanner.rethrowFatal(throwable);
            return null;
        }
    }
}
