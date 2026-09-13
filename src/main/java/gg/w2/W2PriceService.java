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
        getElement(
                API + "5m?id=" + itemId,
                element ->
                {
                    Integer highVolume = null;
                    Integer lowVolume = null;

                    try
                    {
                        JsonObject root =
                                element.getAsJsonObject();

                        JsonObject data =
                                root.getAsJsonObject("data");

                        if (data != null)
                        {
                            JsonObject item =
                                    data.getAsJsonObject(
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
                                    lowVolume
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
                                        null
                                )
                        )
        );
    }

    public void getFlipCandidates(
            long bank,
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

                                        getElement(
                                                API + "5m",
                                                fiveMinuteElement ->
                                                {
                                                    try
                                                    {
                                                        JsonObject fiveMinute =
                                                                fiveMinuteElement
                                                                        .getAsJsonObject()
                                                                        .getAsJsonObject(
                                                                                "data"
                                                                        );

                                                        List<W2FlipCandidate> candidates =
                                                                buildCandidates(
                                                                        bank,
                                                                        mapping,
                                                                        latest,
                                                                        fiveMinute
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
            long bank,
            Map<Integer, ItemMeta> mapping,
            JsonObject latest,
            JsonObject fiveMinute)
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

            if (bank < low)
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
                    highVolume == null
                            ? 0
                            : highVolume;

            int lowVolumeValue =
                    lowVolume == null
                            ? 0
                            : lowVolume;

            int volume =
                    highVolumeValue
                            + lowVolumeValue;

            if (volume < 5)
            {
                continue;
            }

            long affordable =
                    bank / low;

            if (affordable < 1)
            {
                continue;
            }

            long baseQuantity =
                    affordable;

            if (meta.limit > 0)
            {
                baseQuantity =
                        Math.min(
                                baseQuantity,
                                meta.limit
                        );
            }

            int smallerSide =
                    Math.min(
                            highVolumeValue,
                            lowVolumeValue
                    );

            int largerSide =
                    Math.max(
                            highVolumeValue,
                            lowVolumeValue
                    );

            boolean oneSided =
                    largerSide > 0
                            && smallerSide
                            < Math.max(
                            1,
                            largerSide / 5
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

            String marketSignal;

            if (volume < 10
                    || highVolumeValue == 0
                    || lowVolumeValue == 0)
            {
                marketSignal = "Thin";
            }
            else if (oneSided)
            {
                marketSignal = "One-sided";
            }
            else if (volume < 50)
            {
                marketSignal = "Low";
            }
            else
            {
                marketSignal = "Active";
            }

            String freshnessSignal;

            if (oldestAge <= 300)
            {
                freshnessSignal = "Fresh";
            }
            else if (oldestAge <= 900)
            {
                freshnessSignal = "Recent";
            }
            else
            {
                freshnessSignal = "Aging";
            }

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
                            quantity,
                            potential,
                            marketSignal,
                            freshnessSignal,
                            liquidityWarning
                    )
            );
        }

        result.sort(
                Comparator
                        .comparingLong(
                                W2FlipCandidate::getPotentialProfit
                        )
                        .reversed()
                        .thenComparing(
                                Comparator
                                        .comparingInt(
                                                W2FlipCandidate::getVolume
                                        )
                                        .reversed()
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

        public W2ItemData(
                int high,
                int low,
                Long highTime,
                Long lowTime,
                Integer highVolume,
                Integer lowVolume)
        {
            this.high = high;
            this.low = low;
            this.highTime = highTime;
            this.lowTime = lowTime;
            this.highVolume = highVolume;
            this.lowVolume = lowVolume;
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

            return (highVolume == null
                    ? 0
                    : highVolume)
                    +
                    (lowVolume == null
                            ? 0
                            : lowVolume);
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
