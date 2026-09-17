package dev.sosea1.fastsuite112.recipe;

import dev.sosea1.fastsuite112.FastSuite112;
import dev.sosea1.fastsuite112.api.RecipeConstraintProvider;
import dev.sosea1.fastsuite112.config.FastSuiteConfig;
import dev.sosea1.fastsuite112.recipe.RecipeSafetyClassifier.Safety;
import dev.sosea1.fastsuite112.util.IdentityCollections;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Comparator;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class RecipeIndex {
    public static final RecipeIndex INSTANCE = new RecipeIndex();

    private final Object rebuildLock = new Object();
    private final RecipeSafetyClassifier classifier = RecipeSafetyClassifier.INSTANCE;
    private final RecipeConstraintProviderRegistry constraintProviders = new RecipeConstraintProviderRegistry();
    private final RecipeAnalyzer analyzer = new RecipeAnalyzer(classifier, constraintProviders);

    private volatile State state = State.EMPTY;
    private volatile boolean dirty = true;
    private final AtomicLong epoch = new AtomicLong();
    private final AtomicLong rebuildsTotal = new AtomicLong();
    private volatile long lastRebuildNanos;


    private RecipeIndex() {}

    public void registerSafeRecipeClass(Class<? extends IRecipe> recipeClass) {
        if (recipeClass == null) return;
        synchronized (rebuildLock) {
            classifier.registerSafeRecipeClass(recipeClass);
            markDirty("safe recipe class registered: " + recipeClass.getName());
        }
    }

    public void registerSafeIngredientClass(Class<? extends Ingredient> ingredientClass) {
        if (ingredientClass == null) return;
        synchronized (rebuildLock) {
            classifier.registerSafeIngredientClass(ingredientClass);
            markDirty("safe ingredient class registered: " + ingredientClass.getName());
        }
    }

    public <T extends IRecipe> void registerRecipeConstraintProvider(
        Class<T> recipeClass, RecipeConstraintProvider<? super T> provider) {
        if (recipeClass == null) throw new IllegalArgumentException("recipeClass");
        if (provider == null) throw new IllegalArgumentException("provider");
        synchronized (rebuildLock) {
            constraintProviders.register(recipeClass, provider);
            markDirty("recipe constraint provider registered: " + recipeClass.getName());
        }
    }

    public void invalidate(String reason) {
        markDirty(reason == null ? "unspecified mutation" : reason);
    }

    /**
     * Invalidate both the index and cached safety decisions after a runtime config change that can
     * affect classification semantics. The old immutable state may still be read by an in-flight
     * lookup; the next lookup observes the new epoch and rebuilds lazily.
     */
    public void invalidateClassification(String reason) {
        classifier.clearCaches();
        markDirty(reason == null ? "classification semantics changed" : reason);
    }

    public void clear() {
        synchronized (rebuildLock) {
            state = State.EMPTY;
            classifier.clearCaches();
            markDirty("index cleared");
        }
    }

    public long getEpoch() {
        return epoch.get();
    }

    public boolean isDirty() {
        return dirty;
    }


    public long getRebuildsTotal() {
        return rebuildsTotal.get();
    }


    public long getLastRebuildNanos() {
        return lastRebuildNanos;
    }

    private void markDirty(String reason) {
        boolean wasDirty = dirty;
        long newEpoch = epoch.incrementAndGet();
        dirty = true;
        if (!wasDirty && FastSuiteConfig.debugLogging) {
            FastSuite112.LOGGER.debug("Recipe index invalidated (epoch {}): {}", newEpoch, reason);
        }
    }

    /**
     * Return an iterable that is semantically equivalent to the full recipe registry for this grid,
     * but normally contains only plausible indexed recipes plus mandatory fallback recipes.
     *
     * <p>The production path intentionally performs no telemetry/profiling work. Development
     * instrumentation lives outside the release source set.</p>
     */
    public Iterable<IRecipe> candidatesFor(InventoryCrafting inventory) {
        if (!FastSuiteConfig.indexedCrafting || inventory == null) {
            return net.minecraft.item.crafting.CraftingManager.REGISTRY;
        }

        State snapshot = ensureState();
        if (snapshot.recipeCount == 0) {
            return net.minecraft.item.crafting.CraftingManager.REGISTRY;
        }

        RecipeBucket firstBucket = null;
        int[][] unionBuckets = null;
        int unionBucketCount = 0;
        int occupiedSlots = 0;
        long queryItemMaskA = 0L;
        long queryItemMaskB = 0L;
        long queryVariantMaskA = 0L;
        long queryVariantMaskB = 0L;
        long queryCountSketchA = 0L;
        long queryCountSketchB = 0L;

        final int inventorySize = inventory.getSizeInventory();
        final int gridWidth = inventory.getWidth();
        final int gridHeight = inventory.getHeight();
        final boolean advancedPruning = FastSuiteConfig.advancedCandidatePruning
            && snapshot.constraints.hasAny();
        final boolean itemFingerprintsNeeded = advancedPruning
            && snapshot.constraints.needsItemFingerprints();
        final boolean variantFingerprintsNeeded = advancedPruning
            && snapshot.constraints.needsVariantFingerprints();
        final boolean countSketchNeeded = advancedPruning
            && snapshot.constraints.needsCountSketch();
        final boolean shapeOccupancyNeeded = advancedPruning
            && snapshot.constraints.needsShapeOccupancy();
        final boolean occupancyMaskValid = gridWidth > 0
            && gridHeight > 0
            && (long) gridWidth * (long) gridHeight == inventorySize
            && inventorySize <= 63;
        final boolean positionalTokensValid = advancedPruning
            && snapshot.constraints.hasPositionalProbes()
            && occupancyMaskValid
            && ((gridWidth == 2 && gridHeight == 2) || (gridWidth == 3 && gridHeight == 3));
        long queryOccupancyMask = 0L;
        long querySlotTokens0 = 0L;
        long querySlotTokens1 = 0L;
        int querySlotToken8 = 0;

        // One grid walk feeds primary routing and only the enabled second-stage filters.
        // No HashSet/List is allocated for the query itself.
        for (int slot = 0; slot < inventorySize; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            occupiedSlots++;
            if (shapeOccupancyNeeded && occupancyMaskValid) queryOccupancyMask |= 1L << slot;

            Item item = stack.getItem();
            int metadata = stack.getMetadata();
            long itemBitA = 0L;
            long itemBitB = 0L;
            if (itemFingerprintsNeeded) {
                itemBitA = MandatoryStackConstraint.presenceBitA(item);
                itemBitB = MandatoryStackConstraint.presenceBitB(item);
                queryItemMaskA |= itemBitA;
                queryItemMaskB |= itemBitB;
            }
            long variantBitA = 0L;
            long variantBitB = 0L;
            if (variantFingerprintsNeeded) {
                variantBitA = MandatoryStackConstraint.exactVariantBitA(item, metadata);
                variantBitB = MandatoryStackConstraint.exactVariantBitB(item, metadata);
                queryVariantMaskA |= variantBitA;
                queryVariantMaskB |= variantBitB;
            }
            if (positionalTokensValid) {
                int compactToken = ConstraintCompiler.compactQueryToken(itemBitA, itemBitB, variantBitA, variantBitB);
                if (slot < 4) {
                    querySlotTokens0 |= ((long) compactToken & 0xffffL) << (slot << 4);
                } else if (slot < 8) {
                    querySlotTokens1 |= ((long) compactToken & 0xffffL) << ((slot - 4) << 4);
                } else if (slot == 8) {
                    querySlotToken8 = compactToken;
                }
            }
            if (countSketchNeeded) {
                queryCountSketchA = SaturatingCountSketch.increment(
                    queryCountSketchA, ConstraintCompiler.countSketchIndexA(item));
                queryCountSketchB = SaturatingCountSketch.increment(
                    queryCountSketchB, ConstraintCompiler.countSketchIndexB(item));
            }

            ItemPivotBucket pivot = snapshot.pivotBuckets.get(item);
            if (pivot == null) continue;

            // A recipe accepting any metadata is routed through wildcardIds; recipes accepting only
            // specific metadata live in exact buckets and never enter the stream for the wrong meta.
            RecipeBucket wildcardBucket = pivot.wildcardBucket;
            RecipeBucket exactBucket = pivot.exactBucket(metadata);
            for (int source = 0; source < 2; source++) {
                RecipeBucket bucket = source == 0 ? wildcardBucket : exactBucket;
                if (bucket == null || bucket.ids.length == 0) continue;

                if (firstBucket == null) {
                    firstBucket = bucket;
                    continue;
                }

                // Repeated slots (or duplicate routes) often resolve to the same immutable source.
                if (bucket == firstBucket && unionBuckets == null) continue;

                if (unionBuckets == null) {
                    unionBuckets = new int[Math.max(4, inventorySize * 2)][];
                    unionBuckets[unionBucketCount++] = firstBucket.ids;
                }

                boolean alreadyPresent = false;
                for (int i = 0; i < unionBucketCount; i++) {
                    if (unionBuckets[i] == bucket.ids) {
                        alreadyPresent = true;
                        break;
                    }
                }
                if (!alreadyPresent) {
                    if (unionBucketCount == unionBuckets.length) {
                        unionBuckets = Arrays.copyOf(unionBuckets, unionBuckets.length + Math.max(4, unionBuckets.length / 2));
                    }
                    unionBuckets[unionBucketCount++] = bucket.ids;
                }
            }
        }

        if (firstBucket == null) {
            return snapshot.alwaysCheck;
        }

        final long queryMatchMaskA = queryItemMaskA | queryVariantMaskA;
        final long queryMatchMaskB = queryItemMaskB | queryVariantMaskB;

        if (!advancedPruning) {
            if (unionBuckets == null) {
                return snapshot.fallbackRecipeIds.length == 0
                    ? firstBucket
                    : new OrderedIdMergeIterable(snapshot.recipesById, firstBucket.ids, snapshot.fallbackRecipeIds);
            }
            return new OrderedIdUnionIterable(
                snapshot.recipesById,
                unionBuckets,
                unionBucketCount,
                snapshot.fallbackRecipeIds
            );
        }

        final int queryGridWidth = shapeOccupancyNeeded && occupancyMaskValid ? gridWidth : 0;
        final int queryGridHeight = shapeOccupancyNeeded && occupancyMaskValid ? gridHeight : 0;
        if (unionBuckets == null) {
            return new FilteredSingleBucketIterable(
                snapshot.recipesById,
                firstBucket.ids,
                snapshot.fallbackRecipeIds,
                snapshot.constraints,
                occupiedSlots,
                queryMatchMaskA,
                queryMatchMaskB,
                queryItemMaskA,
                queryItemMaskB,
                queryVariantMaskA,
                queryVariantMaskB,
                queryCountSketchA,
                queryCountSketchB,
                queryGridWidth,
                queryGridHeight,
                queryOccupancyMask,
                querySlotTokens0,
                querySlotTokens1,
                querySlotToken8
            );
        }

        return new FilteredOrderedIdUnionIterable(
            snapshot.recipesById,
            unionBuckets,
            unionBucketCount,
            snapshot.fallbackRecipeIds,
            snapshot.constraints,
            occupiedSlots,
            queryMatchMaskA,
            queryMatchMaskB,
            queryItemMaskA,
            queryItemMaskB,
            queryVariantMaskA,
            queryVariantMaskB,
            queryCountSketchA,
            queryCountSketchB,
            queryGridWidth,
            queryGridHeight,
            queryOccupancyMask,
            querySlotTokens0,
            querySlotTokens1,
            querySlotToken8
        );
    }

    /** Ensure the lazy index snapshot has been rebuilt for the current registry generation. */
    public void ensureBuilt() {
        ensureState();
    }

    /** Immutable diagnostic summary; never exposes mutable backing arrays or bucket storage. */
    public StatusSnapshot getStatusSnapshot() {
        State snapshot = ensureState();
        return new StatusSnapshot(
            snapshot.recipeCount,
            snapshot.fallbackRecipeIds.length,
            snapshot.pivotBuckets.size(),
            snapshot.exactMetadataBucketCount,
            snapshot.epoch,
            snapshot.registrySignature
        );
    }

    public int getRecipeCount() {
        return ensureState().recipeCount;
    }

    /** Immutable rebuild-time diagnostics for recipes that must stay on the ordered fallback path. */
    public FallbackDiagnostics getFallbackDiagnostics() {
        return ensureState().fallbackDiagnostics;
    }

    /** Development diagnostics helper; normal lookup paths never call this linear identity check. */
    public boolean isFallbackRecipe(IRecipe recipe) {
        if (recipe == null) return false;
        State snapshot = ensureState();
        for (int id : snapshot.fallbackRecipeIds) {
            if (snapshot.recipesById[id] == recipe) return true;
        }
        return false;
    }

    private State ensureState() {
        State current = state;
        long currentEpoch = epoch.get();
        if (!dirty && current.epoch == currentEpoch) {
            return current;
        }

        synchronized (rebuildLock) {
            current = state;
            currentEpoch = epoch.get();
            if (!dirty && current.epoch == currentEpoch) {
                return current;
            }

            long started = System.nanoTime();
            while (true) {
                long buildEpoch = epoch.get();
                State rebuilt = buildState(buildEpoch);

                // A recipe mutation that races the rebuild must not make this lookup consume a stale
                // freshly-built snapshot. Rebuild once more against the newest generation instead.
                if (epoch.get() != buildEpoch) {
                    dirty = true;
                    continue;
                }

                state = rebuilt;
                dirty = false;
                lastRebuildNanos = System.nanoTime() - started;
                rebuildsTotal.incrementAndGet();
                return rebuilt;
            }
        }
    }

    private State buildState(long currentEpoch) {
        IdentityHashMap<Item, MutableItemPivotBucket> buckets = new IdentityHashMap<Item, MutableItemPivotBucket>();
        List<IRecipe> alwaysCheck = new ArrayList<IRecipe>();
        IntArrayBuilder fallbackIds = new IntArrayBuilder();
        List<IRecipe> recipesInOrder = new ArrayList<IRecipe>();
        List<RecipeAnalysis> analyses = new ArrayList<RecipeAnalysis>();
        IdentityHashMap<Item, Integer> potentialRecipeFrequency = new IdentityHashMap<Item, Integer>();
        BuildReport report = FastSuiteConfig.debugLogging ? new BuildReport() : null;
        FallbackDiagnostics.Builder fallbackDiagnostics = new FallbackDiagnostics.Builder();

        int order = 0;
        RegistrySignature.Builder signature = RegistrySignature.builder();
        for (IRecipe recipe : net.minecraft.item.crafting.CraftingManager.REGISTRY) {
            order++;
            recipesInOrder.add(recipe);
            signature.add(net.minecraft.item.crafting.CraftingManager.REGISTRY.getNameForObject(recipe), recipe);

            RecipeAnalysis analysis;
            try {
                analysis = analyzer.analyze(recipe);
            } catch (RuntimeException | LinkageError failure) {
                // A hostile/buggy modded recipe must not make the entire index unavailable. Keep the
                // recipe reachable through vanilla matching order and record the failure for diagnosis.
                analysis = RecipeAnalysis.failure(failure.getClass());
            }
            analyses.add(analysis);

            if (analysis.isIndexable()) {
                // Count each Item at most once per recipe. This is an upper-bound estimate of how
                // many recipes an Item can attract if chosen as a pivot anywhere in the registry.
                Set<Item> recipePotentialItems = IdentityCollections.newIdentitySet();
                for (CandidateRouting routing : analysis.candidateRoutings) {
                    Collections.addAll(recipePotentialItems, routing.items);
                }
                for (Item item : recipePotentialItems) {
                    Integer current = potentialRecipeFrequency.get(item);
                    potentialRecipeFrequency.put(item, current == null ? 1 : current + 1);
                }
            }
        }

        // Phase two chooses pivots only after the complete registry has been analyzed. The initial
        // assignment uses the registry-wide support estimate; an optional rebuild-only coordinate
        // descent then rebalances those choices against the exact wildcard/meta buckets they create.
        PivotPlanner.Result pivotPlan = PivotPlanner.plan(
            analyses, potentialRecipeFrequency, FastSuiteConfig.loadBalancedPivotSelection);
        int[] pivotAssignments = pivotPlan.assignments;
        if (report != null) report.recordPivotPlanning(pivotPlan.diagnostics);
        RecipeConstraint[] constraintsById = new RecipeConstraint[analyses.size()];
        for (int recipeId = 0; recipeId < analyses.size(); recipeId++) {
            IRecipe recipe = recipesInOrder.get(recipeId);
            RecipeAnalysis analysis = analyses.get(recipeId);

            if (analysis.indexingFailure) {
                alwaysCheck.add(recipe);
                fallbackIds.add(recipeId);
                fallbackDiagnostics.record(recipe, recipeId, analysis);
                if (report != null) report.recordIndexingFailure();
                continue;
            }

            if (!analysis.isIndexable()) {
                alwaysCheck.add(recipe);
                fallbackIds.add(recipeId);
                if (!analysis.mandatoryStackConstraint.isUnconstrained()) {
                    constraintsById[recipeId] = RecipeConstraint.fromMandatory(
                        analysis.mandatoryStackConstraint);
                }
                fallbackDiagnostics.record(recipe, recipeId, analysis);
                if (report != null) report.recordFallback(analysis.fallbackReason);
                continue;
            }

            int selectedPivotIndex = pivotAssignments[recipeId];
            if (selectedPivotIndex < 0 || selectedPivotIndex >= analysis.candidateRoutings.size()) {
                throw new IllegalStateException("Missing pivot assignment for indexed recipe " + recipeId);
            }
            RecipeConstraint constraint = ConstraintCompiler.compile(
                analysis, selectedPivotIndex, potentialRecipeFrequency);
            constraintsById[recipeId] = constraint;
            if (report != null) report.recordConstraint(constraint);
            CandidateRouting primaryRouting = analysis.candidateRoutings.get(selectedPivotIndex);
            for (ItemRoute route : primaryRouting.routes) {
                MutableItemPivotBucket bucket = buckets.get(route.item);
                if (bucket == null) {
                    bucket = new MutableItemPivotBucket();
                    buckets.put(route.item, bucket);
                }
                bucket.add(route, recipeId);
            }
            if (report != null) report.recordIndexed(analysis.recipeSafety);
        }

        // Recipes and ids are appended while walking the Forge registry, so every frozen bucket is
        // already sorted by dense registry id. No priority map or comparator survives into State.
        // Each bucket stores only its primitive ids plus one shared recipesById table reference; it
        // never owns a duplicate array/list of recipe-object references.
        IRecipe[] recipesById = recipesInOrder.toArray(new IRecipe[recipesInOrder.size()]);
        IdentityHashMap<Item, ItemPivotBucket> denseBuckets = new IdentityHashMap<Item, ItemPivotBucket>();
        int exactMetadataBucketCount = 0;
        for (Map.Entry<Item, MutableItemPivotBucket> entry : buckets.entrySet()) {
            ItemPivotBucket frozen = entry.getValue().freeze(recipesById);
            denseBuckets.put(entry.getKey(), frozen);
            exactMetadataBucketCount += frozen.exactMetas.length;
        }

        State result = new State(
            Collections.unmodifiableMap(denseBuckets),
            Collections.unmodifiableList(alwaysCheck),
            recipesById,
            fallbackIds.toArray(),
            ConstraintTable.freeze(constraintsById),
            fallbackDiagnostics.freeze(),
            exactMetadataBucketCount,
            order,
            currentEpoch,
            signature.build()
        );

        if (report != null) {
            report.log(order, alwaysCheck.size(), buckets.size(), exactMetadataBucketCount);
        }
        return result;
    }

    /**
     * Explicit O(N) development verifier. This is intentionally never called from candidatesFor()
     * or ensureState(). It detects registry key/order changes and same-key recipe replacement by
     * object identity. It cannot detect in-place mutation of fields inside an existing IRecipe; mods
     * doing that must call FastSuiteAPI.invalidateRecipeIndex().
     */
    public RegistryVerification verifyRegistrySnapshot() {
        State snapshot = ensureState();
        RegistrySignature current = computeRegistrySignature();
        return new RegistryVerification(snapshot.registrySignature.equals(current), snapshot.registrySignature, current);
    }

    private static RegistrySignature computeRegistrySignature() {
        RegistrySignature.Builder signature = RegistrySignature.builder();
        for (IRecipe recipe : net.minecraft.item.crafting.CraftingManager.REGISTRY) {
            signature.add(net.minecraft.item.crafting.CraftingManager.REGISTRY.getNameForObject(recipe), recipe);
        }
        return signature.build();
    }

    public enum FallbackReason {
        NULL_RECIPE,
        DYNAMIC_RECIPE,
        UNKNOWN_RECIPE_CLASS,
        NO_INGREDIENTS,
        NO_SAFE_PIVOT,
        NO_ENUMERABLE_PIVOT,
        PROVIDER_FILTER_ONLY,
        PROVIDER_FAILURE,
        INDEXING_ERROR
    }

    /** Debug-only rebuild summary. Never allocated when debugLogging=false. */
    private static final class BuildReport {
        private final Map<Safety, Integer> indexedBySafety = new LinkedHashMap<Safety, Integer>();
        private final Map<FallbackReason, Integer> fallbackByReason = new LinkedHashMap<FallbackReason, Integer>();
        private long pivotChoices;
        private long frequencyAwareChoiceChanges;
        private long naiveWorstSupportTotal;
        private long frequencyAwareWorstSupportTotal;
        private int selectedPivotMaxAlternatives;
        private int indexingFailures;
        private int constrainedRecipes;
        private int occupiedCountConstraints;
        private int presenceConstraint1;
        private int presenceConstraint2;
        private int compiledItemSignatureConstraints;
        private int compiledVariantSignatureConstraints;
        private int repeatedCountConstraint1;
        private int repeatedCountConstraint2;
        private int shapedOccupancyConstraints;
        private int positionalProbeConstraints;
        private int loadBalancingPasses;
        private int loadBalancingChanges;
        private int maxBalancedQueryLoad;

        private void recordIndexed(@Nullable Safety safety) {
            if (safety != null) increment(indexedBySafety, safety);
        }

        private void recordFallback(@Nullable FallbackReason reason) {
            if (reason != null) increment(fallbackByReason, reason);
        }

        private void recordIndexingFailure() {
            increment(fallbackByReason, FallbackReason.INDEXING_ERROR);
            indexingFailures++;
        }

        private void recordPivotPlanning(PivotPlanner.Diagnostics diagnostics) {
            pivotChoices = diagnostics.pivotChoices;
            frequencyAwareChoiceChanges = diagnostics.frequencyAwareChoiceChanges;
            naiveWorstSupportTotal = diagnostics.naiveWorstSupportTotal;
            frequencyAwareWorstSupportTotal = diagnostics.frequencyAwareWorstSupportTotal;
            selectedPivotMaxAlternatives = diagnostics.selectedPivotMaxAlternatives;
            loadBalancingPasses = diagnostics.passes;
            loadBalancingChanges = diagnostics.changes;
            maxBalancedQueryLoad = diagnostics.maximumQueryLoad;
        }

        private void recordConstraint(@Nullable RecipeConstraint constraint) {
            if (constraint == null) return;
            constrainedRecipes++;
            if (constraint.occupiedSlots > 0) occupiedCountConstraints++;
            if (constraint.presenceMaskA1 != 0L) presenceConstraint1++;
            if (constraint.presenceMaskA2 != 0L) presenceConstraint2++;
            if (constraint.requiredItemMaskA != 0L) compiledItemSignatureConstraints++;
            if (constraint.requiredVariantMaskA != 0L) compiledVariantSignatureConstraints++;
            if (constraint.repeatedCount1 > 1) repeatedCountConstraint1++;
            if (constraint.repeatedCount2 > 1) repeatedCountConstraint2++;
            if (constraint.shapeWidth > 0) shapedOccupancyConstraints++;
            if (constraint.positionalProbeCount > 0) positionalProbeConstraints++;
        }

        private void log(int total, int fallback, int bucketCount, int exactMetadataBucketCount) {
            int indexed = total - fallback;
            double indexedPercent = total == 0 ? 0.0D : indexed * 100.0D / total;
            FastSuite112.LOGGER.info(
                "Recipe index rebuilt: {} total, {} indexed ({}%), {} ordered fallback, {} item buckets, {} exact-meta buckets",
                total, indexed, String.format(java.util.Locale.ROOT, "%.2f", indexedPercent), fallback, bucketCount, exactMetadataBucketCount
            );
            FastSuite112.LOGGER.info("Indexed safety: {}; fallback reasons: {}", indexedBySafety, fallbackByReason);
            FastSuite112.LOGGER.info(
                "Advanced constraints (enabled={}): {} recipes; occupied={}, shaped={}, positional={}, presence1={}, presence2={}, itemSig={}, variantSig={}, repeated1={}, repeated2={}",
                FastSuiteConfig.advancedCandidatePruning,
                constrainedRecipes,
                occupiedCountConstraints,
                shapedOccupancyConstraints,
                positionalProbeConstraints,
                presenceConstraint1,
                presenceConstraint2,
                compiledItemSignatureConstraints,
                compiledVariantSignatureConstraints,
                repeatedCountConstraint1,
                repeatedCountConstraint2
            );
            if (pivotChoices > 0L) {
                FastSuite112.LOGGER.info(
                    "Frequency-aware pivots (enabled={}): {} choices, {} differ from fewest-alternatives; avg worst support {} -> {}, max alternatives {}",
                    FastSuiteConfig.frequencyAwarePivotSelection,
                    pivotChoices,
                    frequencyAwareChoiceChanges,
                    String.format(java.util.Locale.ROOT, "%.2f", (double) naiveWorstSupportTotal / (double) pivotChoices),
                    String.format(java.util.Locale.ROOT, "%.2f", (double) frequencyAwareWorstSupportTotal / (double) pivotChoices),
                    selectedPivotMaxAlternatives
                );
            }
            FastSuite112.LOGGER.info(
                "Load-balanced pivots (enabled={}): {} pass(es), {} reassignment(s), max final wildcard+exact route load {}",
                FastSuiteConfig.loadBalancedPivotSelection,
                loadBalancingPasses,
                loadBalancingChanges,
                maxBalancedQueryLoad
            );
            if (indexingFailures > 0) {
                FastSuite112.LOGGER.warn("{} recipe(s) threw while the index was rebuilt and were kept on the ordered fallback path", indexingFailures);
            }
        }

        private static <K> void increment(Map<K, Integer> map, K key) {
            Integer count = map.get(key);
            map.put(key, count == null ? 1 : count + 1);
        }
    }

    private static final class State {
        public static final State EMPTY = new State(
            Collections.<Item, ItemPivotBucket>emptyMap(),
            Collections.<IRecipe>emptyList(),
            new IRecipe[0],
            new int[0],
            ConstraintTable.EMPTY,
            FallbackDiagnostics.EMPTY,
            0,
            0,
            0L,
            RegistrySignature.EMPTY
        );

        private final Map<Item, ItemPivotBucket> pivotBuckets;
        public final List<IRecipe> alwaysCheck;
        /** Dense Forge-registry-order recipe table used by primitive candidate iterators. */
        public final IRecipe[] recipesById;
        /** Registry-order dense IDs for mandatory fallback recipes. */
        public final int[] fallbackRecipeIds;
        /** Compact immutable necessary-condition table keyed by dense recipe id. */
        private final ConstraintTable constraints;
        public final FallbackDiagnostics fallbackDiagnostics;
        public final int exactMetadataBucketCount;
        public final int recipeCount;
        public final long epoch;
        public final RegistrySignature registrySignature;

        private State(Map<Item, ItemPivotBucket> pivotBuckets,
                      List<IRecipe> alwaysCheck,
                      IRecipe[] recipesById,
                      int[] fallbackRecipeIds,
                      ConstraintTable constraints,
                      FallbackDiagnostics fallbackDiagnostics,
                      int exactMetadataBucketCount,
                      int recipeCount,
                      long epoch,
                      RegistrySignature registrySignature) {
            this.pivotBuckets = pivotBuckets;
            this.alwaysCheck = alwaysCheck;
            this.recipesById = recipesById;
            this.fallbackRecipeIds = fallbackRecipeIds;
            this.constraints = constraints;
            this.fallbackDiagnostics = fallbackDiagnostics;
            this.exactMetadataBucketCount = exactMetadataBucketCount;
            this.recipeCount = recipeCount;
            this.epoch = epoch;
            this.registrySignature = registrySignature;
        }

        public int getBucketCount() {
            return pivotBuckets.size();
        }
    }


    /** Public read-only index status used by commands and diagnostics. */
    public static final class StatusSnapshot {
        public final int recipeCount;
        public final int fallbackRecipeCount;
        public final int bucketCount;
        public final int exactMetadataBucketCount;
        public final long epoch;
        public final RegistrySignature registrySignature;

        private StatusSnapshot(int recipeCount,
                               int fallbackRecipeCount,
                               int bucketCount,
                               int exactMetadataBucketCount,
                               long epoch,
                               RegistrySignature registrySignature) {
            this.recipeCount = recipeCount;
            this.fallbackRecipeCount = fallbackRecipeCount;
            this.bucketCount = bucketCount;
            this.exactMetadataBucketCount = exactMetadataBucketCount;
            this.epoch = epoch;
            this.registrySignature = registrySignature;
        }
    }


    /** Rebuild-time snapshot describing why recipes could not enter the primary index. */
    public static final class FallbackDiagnostics {
        public static final FallbackDiagnostics EMPTY = new FallbackDiagnostics(
            0, 0, 0, 0, Collections.<FallbackReason, Integer>emptyMap(),
            Collections.<FallbackGroup>emptyList());

        public final int total;
        public final int constrainedTotal;
        public final int opaqueTotal;
        public final int providerFailures;
        public final Map<FallbackReason, Integer> byReason;
        public final List<FallbackGroup> groups;

        private FallbackDiagnostics(int total,
                                    int constrainedTotal,
                                    int opaqueTotal,
                                    int providerFailures,
                                    Map<FallbackReason, Integer> byReason,
                                    List<FallbackGroup> groups) {
            this.total = total;
            this.constrainedTotal = constrainedTotal;
            this.opaqueTotal = opaqueTotal;
            this.providerFailures = providerFailures;
            this.byReason = byReason;
            this.groups = groups;
        }

        public List<FallbackGroup> topGroups(int limit) {
            if (limit <= 0 || groups.isEmpty()) return Collections.emptyList();
            int end = Math.min(limit, groups.size());
            return groups.subList(0, end);
        }

        private static final class Builder {
            private final EnumMap<FallbackReason, Integer> reasons = new EnumMap<FallbackReason, Integer>(FallbackReason.class);
            private final Map<String, MutableFallbackGroup> groups = new HashMap<String, MutableFallbackGroup>();
            private int total;
            private int constrainedTotal;
            private int opaqueTotal;
            private int providerFailures;

            private void record(@Nullable IRecipe recipe, int recipeId, RecipeAnalysis analysis) {
                FallbackReason reason = analysis.fallbackReason == null ? FallbackReason.INDEXING_ERROR : analysis.fallbackReason;
                Integer oldReason = reasons.get(reason);
                reasons.put(reason, oldReason == null ? 1 : oldReason + 1);
                total++;
                if (analysis.mandatoryStackConstraint.isUnconstrained()) opaqueTotal++;
                else constrainedTotal++;
                if (reason == FallbackReason.PROVIDER_FAILURE) providerFailures++;

                String recipeClass = recipe == null ? "<null>" : recipe.getClass().getName();
                String detailClass = analysis.diagnosticClass == null ? null : analysis.diagnosticClass.getName();
                String failureClass = analysis.failureClass == null ? null : analysis.failureClass.getName();
                ResourceLocation registryName = recipe == null ? null : net.minecraft.item.crafting.CraftingManager.REGISTRY.getNameForObject(recipe);
                String example = registryName == null ? "#" + recipeId : registryName.toString();
                String key = reason.name() + '\n' + recipeClass + '\n' + String.valueOf(detailClass) + '\n' + String.valueOf(failureClass);

                MutableFallbackGroup group = groups.get(key);
                if (group == null) {
                    group = new MutableFallbackGroup(reason, recipeClass, detailClass, failureClass, example);
                    groups.put(key, group);
                }
                group.count++;
            }

            private FallbackDiagnostics freeze() {
                if (total == 0) return EMPTY;

                EnumMap<FallbackReason, Integer> frozenReasons = new EnumMap<FallbackReason, Integer>(FallbackReason.class);
                frozenReasons.putAll(reasons);
                List<FallbackGroup> frozenGroups = new ArrayList<FallbackGroup>(groups.size());
                for (MutableFallbackGroup group : groups.values()) frozenGroups.add(group.freeze());
                Collections.sort(frozenGroups, FALLBACK_GROUP_ORDER);
                return new FallbackDiagnostics(
                    total,
                    constrainedTotal,
                    opaqueTotal,
                    providerFailures,
                    Collections.unmodifiableMap(frozenReasons),
                    Collections.unmodifiableList(frozenGroups));
            }
        }
    }

    public static final class FallbackGroup {
        public final FallbackReason reason;
        public final String recipeClass;
        @Nullable public final String detailClass;
        @Nullable public final String failureClass;
        public final String exampleRecipe;
        public final int count;

        private FallbackGroup(FallbackReason reason,
                              String recipeClass,
                              @Nullable String detailClass,
                              @Nullable String failureClass,
                              String exampleRecipe,
                              int count) {
            this.reason = reason;
            this.recipeClass = recipeClass;
            this.detailClass = detailClass;
            this.failureClass = failureClass;
            this.exampleRecipe = exampleRecipe;
            this.count = count;
        }
    }

    private static final class MutableFallbackGroup {
        private final FallbackReason reason;
        private final String recipeClass;
        @Nullable private final String detailClass;
        @Nullable private final String failureClass;
        private final String exampleRecipe;
        private int count;

        private MutableFallbackGroup(FallbackReason reason,
                                     String recipeClass,
                                     @Nullable String detailClass,
                                     @Nullable String failureClass,
                                     String exampleRecipe) {
            this.reason = reason;
            this.recipeClass = recipeClass;
            this.detailClass = detailClass;
            this.failureClass = failureClass;
            this.exampleRecipe = exampleRecipe;
        }

        private FallbackGroup freeze() {
            return new FallbackGroup(reason, recipeClass, detailClass, failureClass, exampleRecipe, count);
        }
    }

    private static final Comparator<FallbackGroup> FALLBACK_GROUP_ORDER = new Comparator<FallbackGroup>() {
        @Override
        public int compare(FallbackGroup left, FallbackGroup right) {
            int countOrder = Integer.compare(right.count, left.count);
            if (countOrder != 0) return countOrder;
            int reasonOrder = left.reason.compareTo(right.reason);
            if (reasonOrder != 0) return reasonOrder;
            return left.recipeClass.compareTo(right.recipeClass);
        }
    };


    public static final class RegistryVerification {
        public final boolean matches;
        public final RegistrySignature snapshot;
        public final RegistrySignature current;

        private RegistryVerification(boolean matches, RegistrySignature snapshot, RegistrySignature current) {
            this.matches = matches;
            this.snapshot = snapshot;
            this.current = current;
        }
    }

    public static final class RegistrySignature {
        public static final RegistrySignature EMPTY = new RegistrySignature(0, 0xcbf29ce484222325L);

        public final int recipeCount;
        public final long orderedIdentityHash;

        private RegistrySignature(int recipeCount, long orderedIdentityHash) {
            this.recipeCount = recipeCount;
            this.orderedIdentityHash = orderedIdentityHash;
        }

        private static Builder builder() {
            return new Builder();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RegistrySignature)) return false;
            RegistrySignature that = (RegistrySignature) other;
            return recipeCount == that.recipeCount && orderedIdentityHash == that.orderedIdentityHash;
        }

        @Override
        public int hashCode() {
            return 31 * recipeCount + (int) (orderedIdentityHash ^ (orderedIdentityHash >>> 32));
        }

        @Override
        public String toString() {
            return "RegistrySignature{count=" + recipeCount + ", hash=0x" + Long.toHexString(orderedIdentityHash) + '}';
        }

        private static final class Builder {
            private int count;
            private long hash = 0xcbf29ce484222325L;

            private void add(@Nullable ResourceLocation key, @Nullable IRecipe recipe) {
                int keyHash = key == null ? 0 : key.hashCode();
                int identityHash = recipe == null ? 0 : System.identityHashCode(recipe);
                mix(keyHash);
                mix(identityHash);
                count++;
            }

            private void mix(int value) {
                hash ^= value & 0xffffffffL;
                hash *= 0x100000001b3L;
            }

            private RegistrySignature build() {
                return new RegistrySignature(count, hash);
            }
        }
    }

    private static final class IntArrayBuilder {
        private int[] values = new int[8];
        private int size;

        private void add(int value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length + Math.max(8, values.length / 2));
            }
            values[size++] = value;
        }

        private int[] toArray() {
            return size == 0 ? new int[0] : Arrays.copyOf(values, size);
        }
    }

    private static final class MutableItemPivotBucket {
        @Nullable
        private MutableRecipeBucket wildcard;
        private final Map<Integer, MutableRecipeBucket> exact = new HashMap<Integer, MutableRecipeBucket>();

        private void add(ItemRoute route, int recipeId) {
            if (route.itemWide) {
                if (wildcard == null) wildcard = new MutableRecipeBucket();
                wildcard.add(recipeId);
                return;
            }
            for (int metadata : route.exactMetas) {
                Integer key = metadata;
                MutableRecipeBucket bucket = exact.get(key);
                if (bucket == null) {
                    bucket = new MutableRecipeBucket();
                    exact.put(key, bucket);
                }
                bucket.add(recipeId);
            }
        }

        private ItemPivotBucket freeze(IRecipe[] recipesById) {
            RecipeBucket wildcardBucket = wildcard == null ? null : wildcard.freeze(recipesById);
            int[] metas = new int[exact.size()];
            int pos = 0;
            for (Integer metadata : exact.keySet()) metas[pos++] = metadata;
            Arrays.sort(metas);
            RecipeBucket[] exactBuckets = new RecipeBucket[metas.length];
            for (int i = 0; i < metas.length; i++) {
                exactBuckets[i] = exact.get(metas[i]).freeze(recipesById);
            }
            return new ItemPivotBucket(wildcardBucket, metas, exactBuckets);
        }
    }

    private static final class ItemPivotBucket {
        @Nullable
        private final RecipeBucket wildcardBucket;
        private final int[] exactMetas;
        private final RecipeBucket[] exactBuckets;

        private ItemPivotBucket(@Nullable RecipeBucket wildcardBucket, int[] exactMetas, RecipeBucket[] exactBuckets) {
            this.wildcardBucket = wildcardBucket;
            this.exactMetas = exactMetas;
            this.exactBuckets = exactBuckets;
        }

        @Nullable
        private RecipeBucket exactBucket(int metadata) {
            if (exactMetas.length <= 8) {
                for (int i = 0; i < exactMetas.length; i++) {
                    if (exactMetas[i] == metadata) return exactBuckets[i];
                }
                return null;
            }
            int index = Arrays.binarySearch(exactMetas, metadata);
            return index < 0 ? null : exactBuckets[index];
        }
    }

    private static final class MutableRecipeBucket {
        private final IntArrayBuilder ids = new IntArrayBuilder();

        private void add(int recipeId) {
            ids.add(recipeId);
        }

        private RecipeBucket freeze(IRecipe[] recipesById) {
            return new RecipeBucket(recipesById, ids.toArray());
        }
    }

    /**
     * Compact immutable pivot bucket. It owns only primitive ids; every bucket references the same
     * dense recipesById table so the fallback=0 single-bucket path remains allocation-free.
     */
    private static final class RecipeBucket implements Iterable<IRecipe> {
        private final IRecipe[] recipesById;
        private final int[] ids;

        private RecipeBucket(IRecipe[] recipesById, int[] ids) {
            this.recipesById = recipesById;
            this.ids = ids;
        }

        @Override
        public Iterator<IRecipe> iterator() {
            return new Iterator<IRecipe>() {
                private int position;

                @Override
                public boolean hasNext() {
                    return position < ids.length;
                }

                @Override
                public IRecipe next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    return recipeAt(recipesById, ids[position++]);
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("immutable recipe candidate iterator");
                }
            };
        }
    }


    private static final class OrderedIdMergeIterable implements Iterable<IRecipe> {
        private final IRecipe[] recipesById;
        private final int[] indexedIds;
        private final int[] fallbackIds;

        private OrderedIdMergeIterable(IRecipe[] recipesById, int[] indexedIds, int[] fallbackIds) {
            this.recipesById = recipesById;
            this.indexedIds = indexedIds;
            this.fallbackIds = fallbackIds;
        }

        @Override
        public Iterator<IRecipe> iterator() {
            return new Iterator<IRecipe>() {
                private int indexedPos;
                private int fallbackPos;

                @Override
                public boolean hasNext() {
                    return indexedPos < indexedIds.length || fallbackPos < fallbackIds.length;
                }

                @Override
                public IRecipe next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    int id;
                    if (indexedPos >= indexedIds.length) {
                        id = fallbackIds[fallbackPos++];
                    } else if (fallbackPos >= fallbackIds.length) {
                        id = indexedIds[indexedPos++];
                    } else {
                        int indexedId = indexedIds[indexedPos];
                        int fallbackId = fallbackIds[fallbackPos];
                        if (indexedId <= fallbackId) {
                            id = indexedId;
                            indexedPos++;
                            if (indexedId == fallbackId) fallbackPos++;
                        } else {
                            id = fallbackId;
                            fallbackPos++;
                        }
                    }
                    return recipeAt(recipesById, id);
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("immutable recipe candidate iterator");
                }
            };
        }
    }

    private static final class OrderedIdUnionIterable implements Iterable<IRecipe> {
        private final IRecipe[] recipesById;
        private final int[][] indexedBuckets;
        private final int indexedBucketCount;
        private final int[] fallbackIds;

        private OrderedIdUnionIterable(IRecipe[] recipesById,
                                       int[][] indexedBuckets,
                                       int indexedBucketCount,
                                       int[] fallbackIds) {
            this.recipesById = recipesById;
            this.indexedBuckets = indexedBuckets;
            this.indexedBucketCount = indexedBucketCount;
            this.fallbackIds = fallbackIds;
        }

        @Override
        public Iterator<IRecipe> iterator() {
            final PrimitiveIterator.OfInt ids = SortedIntUnion.iterator(indexedBuckets, indexedBucketCount, fallbackIds);
            return new Iterator<IRecipe>() {
                @Override
                public boolean hasNext() {
                    return ids.hasNext();
                }

                @Override
                public IRecipe next() {
                    return recipeAt(recipesById, ids.nextInt());
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("immutable recipe candidate iterator");
                }
            };
        }
    }

    /**
     * Single primary bucket plus ordered fallback with second-stage necessary-condition pruning.
     * This keeps the common one-bucket path out of SortedIntUnion while still preserving exact
     * Forge registry order.
     */
    private static final class FilteredSingleBucketIterable implements Iterable<IRecipe> {
        private final IRecipe[] recipesById;
        private final int[] indexedIds;
        private final int[] fallbackIds;
        private final ConstraintTable constraints;
        private final int occupiedSlots;
        private final long queryMatchMaskA;
        private final long queryMatchMaskB;
        private final long queryItemMaskA;
        private final long queryItemMaskB;
        private final long queryVariantMaskA;
        private final long queryVariantMaskB;
        private final long queryCountSketchA;
        private final long queryCountSketchB;
        private final int queryGridWidth;
        private final int queryGridHeight;
        private final long queryOccupancyMask;
        private final long querySlotTokens0;
        private final long querySlotTokens1;
        private final int querySlotToken8;

        private FilteredSingleBucketIterable(IRecipe[] recipesById,
                                             int[] indexedIds,
                                             int[] fallbackIds,
                                             ConstraintTable constraints,
                                             int occupiedSlots,
                                             long queryMatchMaskA,
                                             long queryMatchMaskB,
                                             long queryItemMaskA,
                                             long queryItemMaskB,
                                             long queryVariantMaskA,
                                             long queryVariantMaskB,
                                             long queryCountSketchA,
                                             long queryCountSketchB,
                                             int queryGridWidth,
                                             int queryGridHeight,
                                             long queryOccupancyMask,
                                             long querySlotTokens0,
                                             long querySlotTokens1,
                                             int querySlotToken8) {
            this.recipesById = recipesById;
            this.indexedIds = indexedIds;
            this.fallbackIds = fallbackIds;
            this.constraints = constraints;
            this.occupiedSlots = occupiedSlots;
            this.queryMatchMaskA = queryMatchMaskA;
            this.queryMatchMaskB = queryMatchMaskB;
            this.queryItemMaskA = queryItemMaskA;
            this.queryItemMaskB = queryItemMaskB;
            this.queryVariantMaskA = queryVariantMaskA;
            this.queryVariantMaskB = queryVariantMaskB;
            this.queryCountSketchA = queryCountSketchA;
            this.queryCountSketchB = queryCountSketchB;
            this.queryGridWidth = queryGridWidth;
            this.queryGridHeight = queryGridHeight;
            this.queryOccupancyMask = queryOccupancyMask;
            this.querySlotTokens0 = querySlotTokens0;
            this.querySlotTokens1 = querySlotTokens1;
            this.querySlotToken8 = querySlotToken8;
        }

        @Override
        public Iterator<IRecipe> iterator() {
            return new Iterator<IRecipe>() {
                private int indexedPos;
                private int fallbackPos;
                private int nextId;
                private boolean prepared;
                private boolean exhausted;

                @Override
                public boolean hasNext() {
                    prepare();
                    return !exhausted;
                }

                @Override
                public IRecipe next() {
                    prepare();
                    if (exhausted) throw new NoSuchElementException();
                    int id = nextId;
                    prepared = false;
                    return recipeAt(recipesById, id);
                }

                private void prepare() {
                    if (prepared || exhausted) return;
                    while (indexedPos < indexedIds.length || fallbackPos < fallbackIds.length) {
                        int id;
                        if (indexedPos >= indexedIds.length) {
                            id = fallbackIds[fallbackPos++];
                        } else if (fallbackPos >= fallbackIds.length) {
                            id = indexedIds[indexedPos++];
                        } else {
                            int indexedId = indexedIds[indexedPos];
                            int fallbackId = fallbackIds[fallbackPos];
                            if (indexedId <= fallbackId) {
                                id = indexedId;
                                indexedPos++;
                                if (indexedId == fallbackId) fallbackPos++;
                            } else {
                                id = fallbackId;
                                fallbackPos++;
                            }
                        }
                        if (acceptsConstraint(
                            constraints, id, occupiedSlots, queryMatchMaskA, queryMatchMaskB,
                            queryItemMaskA, queryItemMaskB, queryVariantMaskA, queryVariantMaskB,
                            queryCountSketchA, queryCountSketchB,
                            queryGridWidth, queryGridHeight, queryOccupancyMask,
                            querySlotTokens0, querySlotTokens1, querySlotToken8)) {
                            nextId = id;
                            prepared = true;
                            return;
                        }
                    }
                    exhausted = true;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("immutable recipe candidate iterator");
                }
            };
        }
    }

    /** Ordered k-way union plus fallback, filtered by immutable per-recipe necessary conditions. */
    private static final class FilteredOrderedIdUnionIterable implements Iterable<IRecipe> {
        private final IRecipe[] recipesById;
        private final int[][] indexedBuckets;
        private final int indexedBucketCount;
        private final int[] fallbackIds;
        private final ConstraintTable constraints;
        private final int occupiedSlots;
        private final long queryMatchMaskA;
        private final long queryMatchMaskB;
        private final long queryItemMaskA;
        private final long queryItemMaskB;
        private final long queryVariantMaskA;
        private final long queryVariantMaskB;
        private final long queryCountSketchA;
        private final long queryCountSketchB;
        private final int queryGridWidth;
        private final int queryGridHeight;
        private final long queryOccupancyMask;
        private final long querySlotTokens0;
        private final long querySlotTokens1;
        private final int querySlotToken8;

        private FilteredOrderedIdUnionIterable(IRecipe[] recipesById,
                                               int[][] indexedBuckets,
                                               int indexedBucketCount,
                                               int[] fallbackIds,
                                               ConstraintTable constraints,
                                               int occupiedSlots,
                                               long queryMatchMaskA,
                                               long queryMatchMaskB,
                                               long queryItemMaskA,
                                               long queryItemMaskB,
                                               long queryVariantMaskA,
                                               long queryVariantMaskB,
                                               long queryCountSketchA,
                                               long queryCountSketchB,
                                               int queryGridWidth,
                                               int queryGridHeight,
                                               long queryOccupancyMask,
                                               long querySlotTokens0,
                                               long querySlotTokens1,
                                               int querySlotToken8) {
            this.recipesById = recipesById;
            this.indexedBuckets = indexedBuckets;
            this.indexedBucketCount = indexedBucketCount;
            this.fallbackIds = fallbackIds;
            this.constraints = constraints;
            this.occupiedSlots = occupiedSlots;
            this.queryMatchMaskA = queryMatchMaskA;
            this.queryMatchMaskB = queryMatchMaskB;
            this.queryItemMaskA = queryItemMaskA;
            this.queryItemMaskB = queryItemMaskB;
            this.queryVariantMaskA = queryVariantMaskA;
            this.queryVariantMaskB = queryVariantMaskB;
            this.queryCountSketchA = queryCountSketchA;
            this.queryCountSketchB = queryCountSketchB;
            this.queryGridWidth = queryGridWidth;
            this.queryGridHeight = queryGridHeight;
            this.queryOccupancyMask = queryOccupancyMask;
            this.querySlotTokens0 = querySlotTokens0;
            this.querySlotTokens1 = querySlotTokens1;
            this.querySlotToken8 = querySlotToken8;
        }

        @Override
        public Iterator<IRecipe> iterator() {
            final PrimitiveIterator.OfInt ids = SortedIntUnion.iterator(indexedBuckets, indexedBucketCount, fallbackIds);
            return new Iterator<IRecipe>() {
                private int nextId;
                private boolean prepared;
                private boolean exhausted;

                @Override
                public boolean hasNext() {
                    prepare();
                    return !exhausted;
                }

                @Override
                public IRecipe next() {
                    prepare();
                    if (exhausted) throw new NoSuchElementException();
                    int id = nextId;
                    prepared = false;
                    return recipeAt(recipesById, id);
                }

                private void prepare() {
                    if (prepared || exhausted) return;
                    while (ids.hasNext()) {
                        int id = ids.nextInt();
                        if (acceptsConstraint(
                            constraints, id, occupiedSlots, queryMatchMaskA, queryMatchMaskB,
                            queryItemMaskA, queryItemMaskB, queryVariantMaskA, queryVariantMaskB,
                            queryCountSketchA, queryCountSketchB,
                            queryGridWidth, queryGridHeight, queryOccupancyMask,
                            querySlotTokens0, querySlotTokens1, querySlotToken8)) {
                            nextId = id;
                            prepared = true;
                            return;
                        }
                    }
                    exhausted = true;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("immutable recipe candidate iterator");
                }
            };
        }
    }

    private static boolean acceptsConstraint(ConstraintTable constraints,
                                             int id,
                                             int occupiedSlots,
                                             long queryMatchMaskA,
                                             long queryMatchMaskB,
                                             long queryItemMaskA,
                                             long queryItemMaskB,
                                             long queryVariantMaskA,
                                             long queryVariantMaskB,
                                             long queryCountSketchA,
                                             long queryCountSketchB,
                                             int queryGridWidth,
                                             int queryGridHeight,
                                             long queryOccupancyMask,
                                             long querySlotTokens0,
                                             long querySlotTokens1,
                                             int querySlotToken8) {
        return constraints.accepts(
            id, occupiedSlots, queryMatchMaskA, queryMatchMaskB,
            queryItemMaskA, queryItemMaskB, queryVariantMaskA, queryVariantMaskB,
            queryCountSketchA, queryCountSketchB,
            queryGridWidth, queryGridHeight, queryOccupancyMask,
            querySlotTokens0, querySlotTokens1, querySlotToken8);
    }

    private static IRecipe recipeAt(IRecipe[] recipesById, int id) {
        if (id < 0 || id >= recipesById.length) {
            throw new IllegalStateException("Recipe id out of range: " + id + " / " + recipesById.length);
        }
        return recipesById[id];
    }

}
