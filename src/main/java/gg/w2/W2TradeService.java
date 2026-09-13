package gg.w2;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.client.config.ConfigManager;

public class W2TradeService
{
    private static final String CONFIG_GROUP = "w2";
    private static final String HISTORY_KEY = "tradeHistoryV2";
    private static final String LEGACY_HISTORY_KEY = "tradeHistoryV1";

    private static final int MAX_HISTORY = 500;
    private static final int MAX_HISTORY_DAYS = 365;

    private final ConfigManager configManager;
    private final Gson gson;

    private final Map<Integer, OfferSnapshot> snapshots =
            new HashMap<>();

    @Inject
    public W2TradeService(
            ConfigManager configManager,
            Gson gson)
    {
        this.configManager = configManager;
        this.gson = gson;
    }

    public synchronized void bootstrap(
            GrandExchangeOffer[] offers)
    {
        snapshots.clear();

        if (offers == null)
        {
            return;
        }

        for (int slot = 0; slot < offers.length; slot++)
        {
            GrandExchangeOffer offer = offers[slot];

            if (offer == null
                    || offer.getState() == GrandExchangeOfferState.EMPTY)
            {
                continue;
            }

            snapshots.put(
                    slot,
                    OfferSnapshot.from(offer)
            );
        }
    }

    public synchronized void onOfferChanged(
            int slot,
            GrandExchangeOffer offer)
    {
        OfferSnapshot previous =
                snapshots.get(slot);

        if (offer.getState() == GrandExchangeOfferState.EMPTY)
        {
            snapshots.remove(slot);
            return;
        }

        OfferSnapshot current =
                OfferSnapshot.from(offer);

        if (previous == null)
        {
            snapshots.put(slot, current);
            return;
        }

        boolean sameOffer =
                previous.itemId == current.itemId
                        && previous.price == current.price
                        && previous.totalQuantity
                        == current.totalQuantity;

        if (!sameOffer)
        {
            snapshots.put(slot, current);
            return;
        }

        int deltaQuantity =
                current.quantitySold
                        - previous.quantitySold;

        int deltaSpent =
                current.spent
                        - previous.spent;

        if (deltaQuantity > 0
                && deltaSpent > 0)
        {
            recordFill(
                    current.itemId,
                    isBuyState(current.state),
                    deltaQuantity,
                    deltaSpent
            );
        }

        snapshots.put(slot, current);
    }

    public synchronized List<W2Trade> getTrades()
    {
        List<W2Trade> trades =
                loadTrades();

        trades.sort(
                Comparator.comparing(
                        W2Trade::getTime
                ).reversed()
        );

        return Collections.unmodifiableList(
                trades
        );
    }

    public synchronized TradeAnalysis getAnalysis()
    {
        List<W2Trade> trades =
                loadTrades();

        trades.sort(
                Comparator.comparing(
                        W2Trade::getTime
                )
        );

        Map<Integer, Position> positions =
                new HashMap<>();

        List<TradeView> views =
                new ArrayList<>();

        long realised = 0L;
        long taxPaid = 0L;
        long buyValue = 0L;
        long sellValue = 0L;

        for (W2Trade trade : trades)
        {
            Position position =
                    positions.computeIfAbsent(
                            trade.getItemId(),
                            ignored -> new Position()
                    );

            if (trade.isBuy())
            {
                position.quantity +=
                        trade.getQuantity();

                position.cost +=
                        trade.getGrossValue();

                buyValue +=
                        trade.getGrossValue();

                views.add(
                        new TradeView(
                                trade,
                                0,
                                0L,
                                null
                        )
                );

                continue;
            }

            sellValue +=
                    trade.getGrossValue();

            int matched =
                    Math.min(
                            position.quantity,
                            trade.getQuantity()
                    );

            Long tradeProfit = null;
            long tradeTax = 0L;

            if (matched > 0
                    && position.quantity > 0)
            {
                long matchedCost =
                        position.cost
                                * matched
                                / position.quantity;

                long matchedRevenue =
                        trade.getGrossValue()
                                * matched
                                / trade.getQuantity();

                tradeTax =
                        calculateTax(
                                matchedRevenue,
                                matched
                        );

                long profit =
                        matchedRevenue
                                - tradeTax
                                - matchedCost;

                realised +=
                        profit;

                taxPaid +=
                        tradeTax;

                tradeProfit =
                        profit;

                position.cost -=
                        matchedCost;

                position.quantity -=
                        matched;
            }

            views.add(
                    new TradeView(
                            trade,
                            matched,
                            tradeTax,
                            tradeProfit
                    )
            );
        }

        List<OpenPosition> openPositions =
                new ArrayList<>();

        long openCost = 0L;

        for (Map.Entry<Integer, Position> entry
                : positions.entrySet())
        {
            Position position =
                    entry.getValue();

            if (position.quantity <= 0)
            {
                continue;
            }

            openCost +=
                    position.cost;

            openPositions.add(
                    new OpenPosition(
                            entry.getKey(),
                            position.quantity,
                            position.cost
                    )
            );
        }

        openPositions.sort(
                Comparator.comparingLong(
                        OpenPosition::getCost
                ).reversed()
        );

        Collections.reverse(views);

        return new TradeAnalysis(
                realised,
                taxPaid,
                buyValue,
                sellValue,
                openCost,
                Collections.unmodifiableList(
                        openPositions
                ),
                Collections.unmodifiableList(
                        views
                )
        );
    }

    public synchronized long getRealisedProfitAfterTax()
    {
        return getAnalysis()
                .getRealisedProfitAfterTax();
    }

    public synchronized void clearHistory()
    {
        try
        {
            configManager.unsetRSProfileConfiguration(
                    CONFIG_GROUP,
                    HISTORY_KEY
            );
        }
        catch (Exception ignored)
        {
            // A RuneScape profile is not always available at the login screen.
        }

        configManager.unsetConfiguration(
                CONFIG_GROUP,
                HISTORY_KEY
        );

        configManager.unsetConfiguration(
                CONFIG_GROUP,
                LEGACY_HISTORY_KEY
        );
    }

    private boolean isBuyState(
            GrandExchangeOfferState state)
    {
        return state
                == GrandExchangeOfferState.BUYING
                || state
                == GrandExchangeOfferState.BOUGHT
                || state
                == GrandExchangeOfferState.CANCELLED_BUY;
    }

    private void recordFill(
            int itemId,
            boolean buy,
            int quantity,
            int grossValue)
    {
        if (itemId <= 0
                || quantity <= 0
                || grossValue <= 0)
        {
            return;
        }

        List<W2Trade> trades =
                loadTrades();

        trades.add(
                new W2Trade(
                        itemId,
                        buy,
                        quantity,
                        grossValue,
                        Instant.now()
                )
        );

        pruneHistory(trades);
        saveTrades(trades);
    }

    private List<W2Trade> loadTrades()
    {
        String json = null;

        try
        {
            json =
                    configManager.getRSProfileConfiguration(
                            CONFIG_GROUP,
                            HISTORY_KEY
                    );
        }
        catch (Exception ignored)
        {
            // Fall back to ordinary local config when no RS profile is active.
        }

        if (json == null
                || json.trim().isEmpty())
        {
            json =
                    configManager.getConfiguration(
                            CONFIG_GROUP,
                            HISTORY_KEY
                    );
        }

        /*
         * Smooth migration from the first W2 Trades build.
         * The old history remains readable until the next fill saves it as V2.
         */
        if (json == null
                || json.trim().isEmpty())
        {
            json =
                    configManager.getConfiguration(
                            CONFIG_GROUP,
                            LEGACY_HISTORY_KEY
                    );
        }

        if (json == null
                || json.trim().isEmpty())
        {
            return new ArrayList<>();
        }

        try
        {
            Type type =
                    new TypeToken<List<W2Trade>>() {}
                            .getType();

            List<W2Trade> trades =
                    gson.fromJson(
                            json,
                            type
                    );

            if (trades == null)
            {
                return new ArrayList<>();
            }

            List<W2Trade> cleaned =
                    new ArrayList<>(trades);

            pruneHistory(cleaned);

            return cleaned;
        }
        catch (Exception ignored)
        {
            /*
             * Corrupt history must never stop RuneLite or W2 from starting.
             * Start with an empty in-memory history instead.
             */
            return new ArrayList<>();
        }
    }

    private void saveTrades(
            List<W2Trade> trades)
    {
        pruneHistory(trades);

        Type type =
                new TypeToken<List<W2Trade>>() {}
                        .getType();

        String json =
                gson.toJson(
                        trades,
                        type
                );

        try
        {
            configManager.setRSProfileConfiguration(
                    CONFIG_GROUP,
                    HISTORY_KEY,
                    json
            );

            return;
        }
        catch (Exception ignored)
        {
            // Fall back when a RuneScape profile is not currently available.
        }

        configManager.setConfiguration(
                CONFIG_GROUP,
                HISTORY_KEY,
                json
        );
    }

    private void pruneHistory(
            List<W2Trade> trades)
    {
        Instant cutoff =
                Instant.now()
                        .minus(
                                MAX_HISTORY_DAYS,
                                ChronoUnit.DAYS
                        );

        trades.removeIf(
                trade ->
                        trade == null
                                || trade.getTime()
                                .isBefore(cutoff)
        );

        trades.sort(
                Comparator.comparing(
                        W2Trade::getTime
                )
        );

        while (trades.size() > MAX_HISTORY)
        {
            trades.remove(0);
        }
    }

    private long calculateTax(
            long saleValue,
            int quantity)
    {
        if (saleValue <= 0
                || quantity <= 0)
        {
            return 0L;
        }

        long averagePrice =
                saleValue / quantity;

        long taxPerItem =
                Math.min(
                        5_000_000L,
                        averagePrice / 50L
                );

        return taxPerItem
                * quantity;
    }

    private static class OfferSnapshot
    {
        private final int itemId;
        private final int price;
        private final int totalQuantity;
        private final int quantitySold;
        private final int spent;
        private final GrandExchangeOfferState state;

        private OfferSnapshot(
                int itemId,
                int price,
                int totalQuantity,
                int quantitySold,
                int spent,
                GrandExchangeOfferState state)
        {
            this.itemId = itemId;
            this.price = price;
            this.totalQuantity = totalQuantity;
            this.quantitySold = quantitySold;
            this.spent = spent;
            this.state = state;
        }

        private static OfferSnapshot from(
                GrandExchangeOffer offer)
        {
            return new OfferSnapshot(
                    offer.getItemId(),
                    offer.getPrice(),
                    offer.getTotalQuantity(),
                    offer.getQuantitySold(),
                    offer.getSpent(),
                    offer.getState()
            );
        }
    }

    private static class Position
    {
        private int quantity;
        private long cost;
    }

    public static class TradeAnalysis
    {
        private final long realisedProfitAfterTax;
        private final long taxPaid;
        private final long buyValue;
        private final long sellValue;
        private final long openCost;
        private final List<OpenPosition> openPositions;
        private final List<TradeView> tradeViews;

        private TradeAnalysis(
                long realisedProfitAfterTax,
                long taxPaid,
                long buyValue,
                long sellValue,
                long openCost,
                List<OpenPosition> openPositions,
                List<TradeView> tradeViews)
        {
            this.realisedProfitAfterTax =
                    realisedProfitAfterTax;
            this.taxPaid =
                    taxPaid;
            this.buyValue =
                    buyValue;
            this.sellValue =
                    sellValue;
            this.openCost =
                    openCost;
            this.openPositions =
                    openPositions;
            this.tradeViews =
                    tradeViews;
        }

        public long getRealisedProfitAfterTax()
        {
            return realisedProfitAfterTax;
        }

        public long getTaxPaid()
        {
            return taxPaid;
        }

        public long getBuyValue()
        {
            return buyValue;
        }

        public long getSellValue()
        {
            return sellValue;
        }

        public long getOpenCost()
        {
            return openCost;
        }

        public List<OpenPosition> getOpenPositions()
        {
            return openPositions;
        }

        public List<TradeView> getTradeViews()
        {
            return tradeViews;
        }
    }

    public static class OpenPosition
    {
        private final int itemId;
        private final int quantity;
        private final long cost;

        private OpenPosition(
                int itemId,
                int quantity,
                long cost)
        {
            this.itemId = itemId;
            this.quantity = quantity;
            this.cost = cost;
        }

        public int getItemId()
        {
            return itemId;
        }

        public int getQuantity()
        {
            return quantity;
        }

        public long getCost()
        {
            return cost;
        }

        public long getAverageBuyPrice()
        {
            return quantity <= 0
                    ? 0L
                    : cost / quantity;
        }
    }

    public static class TradeView
    {
        private final W2Trade trade;
        private final int matchedQuantity;
        private final long tax;
        private final Long realisedProfit;

        private TradeView(
                W2Trade trade,
                int matchedQuantity,
                long tax,
                Long realisedProfit)
        {
            this.trade = trade;
            this.matchedQuantity = matchedQuantity;
            this.tax = tax;
            this.realisedProfit = realisedProfit;
        }

        public W2Trade getTrade()
        {
            return trade;
        }

        public int getMatchedQuantity()
        {
            return matchedQuantity;
        }

        public long getTax()
        {
            return tax;
        }

        public Long getRealisedProfit()
        {
            return realisedProfit;
        }

        public boolean hasRealisedProfit()
        {
            return realisedProfit != null;
        }
    }

    public static class W2Trade
    {
        private int itemId;
        private boolean buy;
        private int quantity;
        private long grossValue;
        private Instant time;

        public W2Trade()
        {
        }

        public W2Trade(
                int itemId,
                boolean buy,
                int quantity,
                long grossValue,
                Instant time)
        {
            this.itemId = itemId;
            this.buy = buy;
            this.quantity = quantity;
            this.grossValue = grossValue;
            this.time = time;
        }

        public int getItemId()
        {
            return itemId;
        }

        public boolean isBuy()
        {
            return buy;
        }

        public int getQuantity()
        {
            return quantity;
        }

        public long getGrossValue()
        {
            return grossValue;
        }

        public Instant getTime()
        {
            return time == null
                    ? Instant.EPOCH
                    : time;
        }

        public long getAveragePrice()
        {
            return quantity <= 0
                    ? 0L
                    : grossValue / quantity;
        }
    }
}
