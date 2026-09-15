package gg.w2;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class W2PriceService
{
    private static final String API =
            "https://prices.runescape.wiki/api/v1/osrs/";

    private static final String USER_AGENT =
            "W2 RuneLite Plugin - https://w2.gg";

    private static final int GE_TAX_CAP =
            5_000_000;

    /*
     * Keep the most recent 5-minute market snapshot around briefly so Flips,
     * Lookup and Watch are less likely to show different volume buckets when
     * the Wiki API rolls into a new 5-minute interval between clicks.
     */
    private static final long FIVE_MINUTE_CACHE_MILLIS =
            30_000L;

    private final OkHttpClient httpClient;
    private final Gson gson;

    /*
     * Cached OSRS Wiki item mapping.
     *
     * The mapping contains item IDs, names and GE limits. It changes
     * infrequently, so W2 downloads it once per RuneLite session and reuses
     * it for both Flips and Watch.
     */
    private final Object mappingLock = new Object();
    private Map<Integer, ItemMeta> mappingCache;
    private boolean mappingLoading;

    private final List<Consumer<Map<Integer, ItemMeta>>> mappingSuccessWaiters =
            new ArrayList<>();

    private final List<Consumer<Exception>> mappingErrorWaiters =
            new ArrayList<>();

    private final Object fiveMinuteLock = new Object();
    private FiveMinuteSnapshot fiveMinuteCache;
    private long fiveMinuteCacheLoadedAt;
    private boolean fiveMinuteLoading;

    private final List<Consumer<FiveMinuteSnapshot>> fiveMinuteSuccessWaiters =
            new ArrayList<>();

    private final List<Consumer<Exception>> fiveMinuteErrorWaiters =
            new ArrayList<>();

    @Inject
    public W2PriceService(
            OkHttpClient httpClient,
            Gson gson)
    {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public void getItemName(
            int itemId,
            Consumer<String> onSuccess,
            Consumer<Exception> onError)
    {
        getMapping(
                mapping ->
                {
                    ItemMeta meta =
                            mapping.get(itemId);

                    if (meta == null)
                    {
                        onError.accept(
                                new IOException(
                                        "Unknown item ID: "
                                                + itemId
                                )
                        );
                        return;
                    }

                    onSuccess.accept(meta.name);
                },
                onError
        );
    }

    private void getMapping(
            Consumer<Map<Integer, ItemMeta>> onSuccess,
            Consumer<Exception> onError)
    {
        Map<Integer, ItemMeta> cached = null;
        boolean startRequest = false;

        synchronized (mappingLock)
        {
            if (mappingCache != null)
            {
                cached = mappingCache;
            }
            else
            {
                mappingSuccessWaiters.add(onSuccess);
                mappingErrorWaiters.add(onError);

                if (!mappingLoading)
                {
                    mappingLoading = true;
                    startRequest = true;
                }
            }
        }

        if (cached != null)
        {
            onSuccess.accept(cached);
            return;
        }

        if (!startRequest)
        {
            return;
        }

        getElement(
                API + "mapping",
                mappingElement ->
                {
                    final Map<Integer, ItemMeta> parsed;

                    try
                    {
                        parsed =
                                parseMapping(
                                        mappingElement.getAsJsonArray()
                                );
                    }
                    catch (Exception exception)
                    {
                        failMappingLoad(exception);
                        return;
                    }

                    final List<Consumer<Map<Integer, ItemMeta>>> successWaiters;

                    synchronized (mappingLock)
                    {
                        mappingCache = parsed;
                        mappingLoading = false;

                        successWaiters =
                                new ArrayList<>(
                                        mappingSuccessWaiters
                                );

                        mappingSuccessWaiters.clear();
                        mappingErrorWaiters.clear();
                    }

                    for (Consumer<Map<Integer, ItemMeta>> waiter
                            : successWaiters)
                    {
                        waiter.accept(parsed);
                    }
                },
                this::failMappingLoad
        );
    }

    private void failMappingLoad(
            Exception exception)
    {
        final List<Consumer<Exception>> errorWaiters;

        synchronized (mappingLock)
        {
            mappingLoading = false;

            errorWaiters =
                    new ArrayList<>(
                            mappingErrorWaiters
                    );

            mappingSuccessWaiters.clear();
            mappingErrorWaiters.clear();
        }

        for (Consumer<Exception> waiter
                : errorWaiters)
        {
            waiter.accept(exception);
        }
    }

    private void getFiveMinuteSnapshot(
            Consumer<FiveMinuteSnapshot> onSuccess,
            Consumer<Exception> onError)
    {
        FiveMinuteSnapshot cached = null;
        boolean startRequest = false;
        long now = System.currentTimeMillis();

        synchronized (fiveMinuteLock)
        {
            if (fiveMinuteCache != null
                    && now - fiveMinuteCacheLoadedAt
                    <= FIVE_MINUTE_CACHE_MILLIS)
            {
                cached = fiveMinuteCache;
            }
            else
            {
                fiveMinuteSuccessWaiters.add(onSuccess);
                fiveMinuteErrorWaiters.add(onError);

                if (!fiveMinuteLoading)
                {
                    fiveMinuteLoading = true;
                    startRequest = true;
                }
            }
        }

        if (cached != null)
        {
            onSuccess.accept(cached);
            return;
        }

        if (!startRequest)
        {
            return;
        }

        getElement(
                API + "5m",
                element ->
                {
                    final FiveMinuteSnapshot snapshot;

                    try
                    {
                        JsonObject root =
                                element.getAsJsonObject();

                        JsonObject data =
                                root.getAsJsonObject("data");

                        if (data == null)
                        {
                            throw new IOException(
                                    "No 5-minute market data"
                            );
                        }

                        snapshot =
                                new FiveMinuteSnapshot(
                                        data,
                                        getNullableLong(
                                                root,
                                                "timestamp"
                                        )
                                );
                    }
                    catch (Exception exception)
                    {
                        failFiveMinuteLoad(exception);
                        return;
                    }

                    final List<Consumer<FiveMinuteSnapshot>> successWaiters;

                    synchronized (fiveMinuteLock)
                    {
                        fiveMinuteCache = snapshot;
                        fiveMinuteCacheLoadedAt =
                                System.currentTimeMillis();
                        fiveMinuteLoading = false;

                        successWaiters =
                                new ArrayList<>(
                                        fiveMinuteSuccessWaiters
                                );

                        fiveMinuteSuccessWaiters.clear();
                        fiveMinuteErrorWaiters.clear();
                    }

                    for (Consumer<FiveMinuteSnapshot> waiter
                            : successWaiters)
                    {
                        waiter.accept(snapshot);
                    }
                },
                this::failFiveMinuteLoad
        );
    }

    private void failFiveMinuteLoad(
            Exception exception)
    {
        final List<Consumer<Exception>> errorWaiters;

        synchronized (fiveMinuteLock)
        {
            fiveMinuteLoading = false;

            errorWaiters =
                    new ArrayList<>(
                            fiveMinuteErrorWaiters
                    );

            fiveMinuteSuccessWaiters.clear();
            fiveMinuteErrorWaiters.clear();
        }

        for (Consumer<Exception> waiter
                : errorWaiters)
        {
            waiter.accept(exception);
        }
    }

    public void getItemData(
            int itemId,
            Consumer<W2ItemData> onSuccess,
            Consumer<Exception> onError)
    {
        getElement(
                API + "latest?id=" + itemId,
                latestElement ->
                {
                    try
                    {
                        JsonObject root =
                                latestElement.getAsJsonObject();

                        JsonObject data =
                                root.getAsJsonObject("data");

                        if (data == null)
                        {
                            throw new IOException(
                                    "No price data"
                            );
                        }

                        JsonObject item =
                                data.getAsJsonObject(
                                        String.valueOf(itemId)
                                );

                        if (item == null)
                        {
                            throw new IOException(
                                    "No item price data"
                            );
                        }

                        Integer high =
                                getNullableInt(
                                        item,
                                        "high"
                                );

                        Integer low =
                                getNullableInt(
                                        item,
                                        "low"
                                );

                        Long highTime =
                                getNullableLong(
                                        item,
                                        "highTime"
                                );

                        Long lowTime =
                                getNullableLong(
                                        item,
                                        "lowTime"
                                );

                        if (high == null
                                || low == null)
                        {
                            throw new IOException(
                                    "Incomplete price data"
                            );
                        }

                        getItemFiveMinuteData(
                                itemId,
                                high,
                                low,
                                highTime,
                                lowTime,
                                onSuccess
                        );
                    }
                    catch (Exception exception)
                    {
                        onError.accept(exception);
                    }
                },
                onError
        );
    }

    private void getItemFiveMinuteData(
            int itemId,
            int high,
            int low,
            Long highTime,
            Long lowTime,
            Consumer<W2ItemData> onSuccess)
    {
        getFiveMinuteSnapshot(
                snapshot ->
                {
                    Integer highVolume = null;
                    Integer lowVolume = null;

                    try
                    {
                        JsonObject item =
                                snapshot.data.getAsJsonObject(
                                        String.valueOf(itemId)
                                );

                        if (item != null)
                        {
                            highVolume =
                                    getNullableInt(
                                            item,
                                            "highPriceVolume"
                                    );

                            lowVolume =
                                    getNullableInt(
                                            item,
                                            "lowPriceVolume"
                                    );
                        }
                    }
                    catch (Exception ignored)
                    {
                    }

                    onSuccess.accept(
                            new W2ItemData(
                                    high,
                                    low,
                                    highTime,
                                    lowTime,
                                    highVolume,
                                    lowVolume,
                                    snapshot.timestamp
                            )
                    );
                },
                exception ->
                        onSuccess.accept(
                                new W2ItemData(
                                        high,
                                        low,
                                        highTime,
                                        lowTime,
                                        null,
                                        null,
                                        null
                                )
                        )
        );
    }

    public void getFlipCandidates(
            Consumer<List<W2FlipCandidate>> onSuccess,
            Consumer<Exception> onError)
    {
        getMapping(
                mapping ->
                        getElement(
                                API + "latest",
                                latestElement ->
                                {
                                    try
                                    {
                                        JsonObject latest =
                                                latestElement
                                                        .getAsJsonObject()
                                                        .getAsJsonObject(
                                                                "data"
                                                        );

                                        getFiveMinuteSnapshot(
                                                snapshot ->
                                                {
                                                    try
                                                    {
                                                        List<W2FlipCandidate> candidates =
                                                                buildCandidates(
                                                                        mapping,
                                                                        latest,
                                                                        snapshot.data,
                                                                        snapshot.timestamp
                                                                );

                                                        onSuccess.accept(
                                                                candidates
                                                        );
                                                    }
                                                    catch (Exception exception)
                                                    {
                                                        onError.accept(
                                                                exception
                                                        );
                                                    }
                                                },
                                                onError
                                        );
                                    }
                                    catch (Exception exception)
                                    {
                                        onError.accept(
                                                exception
                                        );
                                    }
                                },
                                onError
                        ),
                onError
        );
    }

    private Map<Integer, ItemMeta> parseMapping(
            JsonArray array)
    {
        Map<Integer, ItemMeta> result =
                new HashMap<>();

        for (JsonElement element : array)
        {
            JsonObject object =
                    element.getAsJsonObject();

            Integer id =
                    getNullableInt(
                            object,
                            "id"
                    );

            JsonElement nameElement =
                    object.get("name");

            if (id == null
                    || nameElement == null
                    || nameElement.isJsonNull())
            {
                continue;
            }

            Integer limit =
                    getNullableInt(
                            object,
                            "limit"
                    );

            result.put(
                    id,
                    new ItemMeta(
                            id,
                            nameElement.getAsString(),
                            limit == null
                                    ? 0
                                    : limit
                    )
            );
        }

        return result;
    }

    private List<W2FlipCandidate> buildCandidates(
            Map<Integer, ItemMeta> mapping,
            JsonObject latest,
            JsonObject fiveMinute,
            Long fiveMinuteTimestamp)
    {
        List<W2FlipCandidate> result =
                new ArrayList<>();

        if (latest == null)
        {
            return result;
        }

        long now =
                Instant.now()
                        .getEpochSecond();

        for (ItemMeta meta : mapping.values())
        {
            JsonObject price =
                    latest.getAsJsonObject(
                            String.valueOf(meta.id)
                    );

            if (price == null)
            {
                continue;
            }

            Integer high =
                    getNullableInt(
                            price,
                            "high"
                    );

            Integer low =
                    getNullableInt(
                            price,
                            "low"
                    );

            Long highTime =
                    getNullableLong(
                            price,
                            "highTime"
                    );

            Long lowTime =
                    getNullableLong(
                            price,
                            "lowTime"
                    );

            if (high == null
                    || low == null
                    || high <= 0
                    || low <= 0
                    || high <= low)
            {
                continue;
            }

            int tax =
                    calculateTax(high);

            int profit =
                    high
                            - low
                            - tax;

            if (profit <= 0)
            {
                continue;
            }

            double roi =
                    (profit / (double) low)
                            * 100.0;

            if (roi > 25.0)
            {
                continue;
            }

            if (highTime == null
                    || lowTime == null)
            {
                continue;
            }

            long highAge =
                    Math.max(
                            0,
                            now - highTime
                    );

            long lowAge =
                    Math.max(
                            0,
                            now - lowTime
                    );

            long newestAge =
                    Math.min(
                            highAge,
                            lowAge
                    );

            long oldestAge =
                    Math.max(
                            highAge,
                            lowAge
                    );

            /*
             * Keep obviously dead markets out of the list, but do not hide
             * merely slow ones. Slow/thin markets are surfaced with a warning
             * instead of being silently treated as equally trustworthy.
             */
            if (newestAge > 1800
                    || oldestAge > 3600)
            {
                continue;
            }

            Integer highVolume = null;
            Integer lowVolume = null;

            if (fiveMinute != null)
            {
                JsonObject activity =
                        fiveMinute.getAsJsonObject(
                                String.valueOf(meta.id)
                        );

                if (activity != null)
                {
                    highVolume =
                            getNullableInt(
                                    activity,
                                    "highPriceVolume"
                            );

                    lowVolume =
                            getNullableInt(
                                    activity,
                                    "lowPriceVolume"
                            );
                }
            }

            int highVolumeValue =
                    valueOrZero(highVolume);

            int lowVolumeValue =
                    valueOrZero(lowVolume);

            int volume =
                    combinedFiveMinuteVolume(
                            highVolume,
                            lowVolume
                    );

            if (volume < 5)
            {
                continue;
            }

            /*
             * W2 no longer asks for a cash stack. Suggested size is based on
             * the item's GE limit and recent two-sided market activity only.
             * This keeps the shortlist honest instead of forcing different
             * items for arbitrary bank presets.
             */
            long baseQuantity = meta.limit > 0
                    ? meta.limit
                    : Long.MAX_VALUE;

            int smallerSide =
                    weakerSideVolume(
                            highVolume,
                            lowVolume
                    );

            boolean oneSided =
                    isOneSided(
                            highVolume,
                            lowVolume
                    );

            /*
             * A flip needs activity on both sides of the spread.
             *
             * Combined 5-minute volume can look healthy while nearly all of
             * those trades happened on only one side. W2 therefore bases its
             * suggested size on the weaker side and uses only half of that
             * observed activity.
             *
             * This remains a planning aid, not a promise that orders will fill.
             */
            long activityCapacity =
                    Math.max(
                            1L,
                            smallerSide / 2L
                    );

            long quantity =
                    Math.min(
                            baseQuantity,
                            activityCapacity
                    );

            long potential =
                    (long) profit
                            * quantity;

            String marketSignal =
                    marketSignal(
                            highVolume,
                            lowVolume
                    );

            String freshnessSignal =
                    freshnessSignal(
                            highTime,
                            lowTime
                    );

            String liquidityWarning = null;

            if (oneSided)
            {
                if (highVolumeValue < lowVolumeValue)
                {
                    liquidityWarning =
                            "Weak exit-side activity";
                }
                else
                {
                    liquidityWarning =
                            "Weak entry-side activity";
                }
            }
            else if (smallerSide > 0
                    && baseQuantity > (long) smallerSide * 2L)
            {
                liquidityWarning =
                        "Large size vs recent activity";
            }
            else if (smallerSide > 0
                    && baseQuantity > smallerSide)
            {
                liquidityWarning =
                        "This size may take time to fill";
            }
            else if (oldestAge > 900)
            {
                liquidityWarning =
                        "One side of the market is getting stale";
            }

            result.add(
                    new W2FlipCandidate(
                            meta.id,
                            meta.name,
                            meta.limit,
                            low,
                            high,
                            profit,
                            roi,
                            volume,
                            highVolume,
                            lowVolume,
                            highTime,
                            lowTime,
                            fiveMinuteTimestamp,
                            quantity,
                            potential,
                            marketSignal,
                            freshnessSignal,
                            liquidityWarning
                    )
            );
        }

        /*
         * Bank-independent ranking. Prefer real post-tax opportunity backed
         * by two-sided activity, while penalising risky/stale-looking fills.
         */
        result.sort(
                Comparator
                        .<W2FlipCandidate>comparingDouble(
                                W2PriceService::marketOpportunityScore
                        )
                        .reversed()
                        .thenComparing(
                                Comparator.comparingLong(
                                        W2FlipCandidate::getPotentialProfit
                                ).reversed()
                        )
                        .thenComparing(
                                Comparator.comparingInt(
                                        W2FlipCandidate::getWeakerSideVolume
                                ).reversed()
                        )
        );

        if (result.size() > 30)
        {
            return new ArrayList<>(
                    result.subList(
                            0,
                            30
                    )
            );
        }

        return result;
    }

    private static double marketOpportunityScore(
            W2FlipCandidate candidate)
    {
        double profitQuality = Math.log10(
                Math.max(1.0, candidate.getPotentialProfit()) + 1.0
        );

        double activityQuality = Math.log10(
                Math.max(1.0, candidate.getWeakerSideVolume()) + 1.0
        );

        double roiQuality = Math.sqrt(
                Math.min(25.0, Math.max(0.0, candidate.getRoi()))
        );

        double warningPenalty = candidate.hasLiquidityWarning()
                ? 2.0
                : 0.0;

        return (3.0 * profitQuality)
                + (2.0 * activityQuality)
                + roiQuality
                - warningPenalty;
    }

    private static int valueOrZero(
            Integer value)
    {
        return value == null
                ? 0
                : value;
    }

    private static int combinedFiveMinuteVolume(
            Integer highVolume,
            Integer lowVolume)
    {
        return valueOrZero(highVolume)
                + valueOrZero(lowVolume);
    }

    private static int weakerSideVolume(
            Integer highVolume,
            Integer lowVolume)
    {
        return Math.min(
                valueOrZero(highVolume),
                valueOrZero(lowVolume)
        );
    }

    private static boolean isOneSided(
            Integer highVolume,
            Integer lowVolume)
    {
        int smallerSide =
                weakerSideVolume(
                        highVolume,
                        lowVolume
                );

        int largerSide =
                Math.max(
                        valueOrZero(highVolume),
                        valueOrZero(lowVolume)
                );

        return largerSide > 0
                && smallerSide
                < Math.max(
                1,
                largerSide / 5
        );
    }

    private static String marketSignal(
            Integer highVolume,
            Integer lowVolume)
    {
        int high =
                valueOrZero(highVolume);

        int low =
                valueOrZero(lowVolume);

        int total =
                high + low;

        if (total < 10
                || high == 0
                || low == 0)
        {
            return "Low activity";
        }

        if (isOneSided(
                highVolume,
                lowVolume))
        {
            return "One-sided";
        }

        if (total < 50)
        {
            return "Moderate";
        }

        return "Active";
    }

    private static String freshnessSignal(
            Long highTime,
            Long lowTime)
    {
        if (highTime == null
                || lowTime == null)
        {
            return null;
        }

        long now =
                Instant.now()
                        .getEpochSecond();

        long oldestAge =
                Math.max(
                        Math.max(
                                0,
                                now - highTime
                        ),
                        Math.max(
                                0,
                                now - lowTime
                        )
                );

        if (oldestAge <= 300)
        {
            return "Fresh";
        }

        if (oldestAge <= 900)
        {
            return "Recent";
        }

        return "Aging";
    }

    private int calculateTax(
            int sellPrice)
    {
        long tax =
                sellPrice / 50L;

        return (int) Math.min(
                tax,
                GE_TAX_CAP
        );
    }

    private void getElement(
            String url,
            Consumer<JsonElement> onSuccess,
            Consumer<Exception> onError)
    {
        Request request =
                new Request.Builder()
                        .url(url)
                        .header(
                                "User-Agent",
                                USER_AGENT
                        )
                        .build();

        httpClient
                .newCall(request)
                .enqueue(
                        new Callback()
                        {
                            @Override
                            public void onFailure(
                                    Call call,
                                    IOException exception)
                            {
                                onError.accept(
                                        exception
                                );
                            }

                            @Override
                            public void onResponse(
                                    Call call,
                                    Response response)
                                    throws IOException
                            {
                                try (ResponseBody body =
                                             response.body())
                                {
                                    if (!response.isSuccessful()
                                            || body == null)
                                    {
                                        onError.accept(
                                                new IOException(
                                                        "Request failed: "
                                                                + response.code()
                                                )
                                        );

                                        return;
                                    }

                                    JsonElement result =
                                            gson.fromJson(
                                                    body.string(),
                                                    JsonElement.class
                                            );

                                    onSuccess.accept(
                                            result
                                    );
                                }
                                catch (Exception exception)
                                {
                                    onError.accept(
                                            exception
                                    );
                                }
                            }
                        }
                );
    }

    private Integer getNullableInt(
            JsonObject object,
            String name)
    {
        JsonElement value =
                object.get(name);

        if (value == null
                || value.isJsonNull())
        {
            return null;
        }

        return value.getAsInt();
    }

    private Long getNullableLong(
            JsonObject object,
            String name)
    {
        JsonElement value =
                object.get(name);

        if (value == null
                || value.isJsonNull())
        {
            return null;
        }

        return value.getAsLong();
    }

    private static class FiveMinuteSnapshot
    {
        private final JsonObject data;
        private final Long timestamp;

        private FiveMinuteSnapshot(
                JsonObject data,
                Long timestamp)
        {
            this.data = data;
            this.timestamp = timestamp;
        }
    }

    private static class ItemMeta
    {
        private final int id;
        private final String name;
        private final int limit;

        private ItemMeta(
                int id,
                String name,
                int limit)
        {
            this.id = id;
            this.name = name;
            this.limit = limit;
        }
    }

    public static class W2ItemData
    {
        private final int high;
        private final int low;

        private final Long highTime;
        private final Long lowTime;

        private final Integer highVolume;
        private final Integer lowVolume;
        private final Long fiveMinuteTimestamp;

        public W2ItemData(
                int high,
                int low,
                Long highTime,
                Long lowTime,
                Integer highVolume,
                Integer lowVolume,
                Long fiveMinuteTimestamp)
        {
            this.high = high;
            this.low = low;
            this.highTime = highTime;
            this.lowTime = lowTime;
            this.highVolume = highVolume;
            this.lowVolume = lowVolume;
            this.fiveMinuteTimestamp =
                    fiveMinuteTimestamp;
        }

        public int getHigh()
        {
            return high;
        }

        public int getLow()
        {
            return low;
        }

        public Long getHighTime()
        {
            return highTime;
        }

        public Long getLowTime()
        {
            return lowTime;
        }

        public Integer getHighVolume()
        {
            return highVolume;
        }

        public Integer getLowVolume()
        {
            return lowVolume;
        }

        public Integer getFiveMinuteVolume()
        {
            if (highVolume == null
                    && lowVolume == null)
            {
                return null;
            }

            return combinedFiveMinuteVolume(
                    highVolume,
                    lowVolume
            );
        }

        public Integer getWeakerSideVolume()
        {
            if (highVolume == null
                    && lowVolume == null)
            {
                return null;
            }

            return weakerSideVolume(
                    highVolume,
                    lowVolume
            );
        }

        public String getMarketSignal()
        {
            if (highVolume == null
                    && lowVolume == null)
            {
                return null;
            }

            return marketSignal(
                    highVolume,
                    lowVolume
            );
        }

        public String getFreshnessSignal()
        {
            return freshnessSignal(
                    highTime,
                    lowTime
            );
        }

        public Long getFiveMinuteTimestamp()
        {
            return fiveMinuteTimestamp;
        }

    }

    public static class W2FlipCandidate
    {
        private final int id;
        private final String name;
        private final int limit;

        private final int buy;
        private final int sell;

        private final int profit;
        private final double roi;

        private final int volume;

        private final Integer highVolume;
        private final Integer lowVolume;

        private final Long highTime;
        private final Long lowTime;
        private final Long fiveMinuteTimestamp;

        private final long suggestedQuantity;
        private final long potentialProfit;

        private final String marketSignal;
        private final String freshnessSignal;
        private final String liquidityWarning;

        public W2FlipCandidate(
                int id,
                String name,
                int limit,
                int buy,
                int sell,
                int profit,
                double roi,
                int volume,
                Integer highVolume,
                Integer lowVolume,
                Long highTime,
                Long lowTime,
                Long fiveMinuteTimestamp,
                long suggestedQuantity,
                long potentialProfit,
                String marketSignal,
                String freshnessSignal,
                String liquidityWarning)
        {
            this.id = id;
            this.name = name;
            this.limit = limit;
            this.buy = buy;
            this.sell = sell;
            this.profit = profit;
            this.roi = roi;
            this.volume = volume;
            this.highVolume = highVolume;
            this.lowVolume = lowVolume;
            this.highTime = highTime;
            this.lowTime = lowTime;
            this.fiveMinuteTimestamp =
                    fiveMinuteTimestamp;
            this.suggestedQuantity =
                    suggestedQuantity;
            this.potentialProfit =
                    potentialProfit;
            this.marketSignal =
                    marketSignal;
            this.freshnessSignal =
                    freshnessSignal;
            this.liquidityWarning =
                    liquidityWarning;
        }

        public int getId()
        {
            return id;
        }

        public String getName()
        {
            return name;
        }

        public int getLimit()
        {
            return limit;
        }

        public int getBuy()
        {
            return buy;
        }

        public int getSell()
        {
            return sell;
        }

        public int getProfit()
        {
            return profit;
        }

        public double getRoi()
        {
            return roi;
        }

        public int getVolume()
        {
            return volume;
        }

        public int getWeakerSideVolume()
        {
            return weakerSideVolume(
                    highVolume,
                    lowVolume
            );
        }

        public Integer getHighVolume()
        {
            return highVolume;
        }

        public Integer getLowVolume()
        {
            return lowVolume;
        }

        public Long getHighTime()
        {
            return highTime;
        }

        public Long getLowTime()
        {
            return lowTime;
        }

        public Long getFiveMinuteTimestamp()
        {
            return fiveMinuteTimestamp;
        }

        public long getSuggestedQuantity()
        {
            return suggestedQuantity;
        }

        public long getPotentialProfit()
        {
            return potentialProfit;
        }

        public String getMarketSignal()
        {
            return marketSignal;
        }

        public String getFreshnessSignal()
        {
            return freshnessSignal;
        }

        public String getLiquidityWarning()
        {
            return liquidityWarning;
        }

        public boolean hasLiquidityWarning()
        {
            return liquidityWarning != null
                    && !liquidityWarning.isEmpty();
        }
    }
}

