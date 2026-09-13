package gg.w2;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

public class W2ItemLookupPanel extends JPanel
{
    private static final Color GOLD = new Color(205, 173, 92);
    private static final Color GREEN = new Color(94, 186, 125);
    private static final Color RED = new Color(220, 90, 90);
    private static final Color WARNING = new Color(224, 183, 92);
    private static final Color MUTED = new Color(165, 165, 165);
    private static final Color DIVIDER = new Color(72, 72, 72);
    private static final Color PANEL_BG = ColorScheme.DARK_GRAY_COLOR;

    private static final Font BODY = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font SMALL = new Font("SansSerif", Font.PLAIN, 11);
    private static final Font BOLD = new Font("SansSerif", Font.BOLD, 12);
    private static final Font ITEM_NAME = new Font("SansSerif", Font.BOLD, 14);
    private static final Font SECTION = new Font("SansSerif", Font.BOLD, 10);

    private static final int GE_TAX_CAP = 5_000_000;

    /*
     * These explicit heights are deliberate.
     *
     * The previous Lookup layout used vertically-expandable child panels.
     * Because W2Panel places Lookup inside a scroll viewport, BoxLayout was
     * stretching those child panels to fill the viewport. That produced the
     * huge blank spaces between MARKET, RECENT ACTIVITY and Watchlist.
     *
     * This version gives every vertical block a real upper bound, so there is
     * nothing available for BoxLayout to stretch.
     */
    private static final int EMPTY_HEIGHT = 72;
    private static final int HEADER_HEIGHT = 52;
    private static final int MARKET_HEIGHT = 126;
    private static final int ACTIVITY_HEIGHT = 80;
    private static final int WATCH_HEIGHT = 24;
    private static final int SELECTED_PANEL_HEIGHT = 350;

    private final ItemManager itemManager;
    private final W2PriceService priceService;
    private final W2WatchlistService watchlistService;

    private final JPanel emptyState = new JPanel();
    private final JPanel selectedState = new JPanel();

    private final JLabel iconLabel = new JLabel();
    private final JLabel nameLabel = new JLabel("Select an item");
    private final JLabel subtitleLabel = new JLabel();

    private final JLabel buyValue = new JLabel("—");
    private final JLabel sellValue = new JLabel("—");
    private final JLabel profitValue = new JLabel("—");
    private final JLabel roiValue = new JLabel("—");
    private final JLabel tradesValue = new JLabel("—");
    private final JLabel latestTradeValue = new JLabel("—");

    private final JLabel taxLabel = new JLabel(" ");
    private final JLabel activityLabel = new JLabel(" ");
    private final JLabel watchAction = new JLabel("☆ Watch");

    private int currentItemId = -1;
    private String currentItemName;
    private Runnable watchlistChangedListener;

    public W2ItemLookupPanel(
            ItemManager itemManager,
            W2PriceService priceService,
            W2WatchlistService watchlistService)
    {
        this.itemManager = itemManager;
        this.priceService = priceService;
        this.watchlistService = watchlistService;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(PANEL_BG);
        setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        setAlignmentX(LEFT_ALIGNMENT);

        buildEmptyState();
        buildSelectedState();

        add(emptyState);
        add(selectedState);

        clearDisplay();
    }

    public void setWatchlistChangedListener(Runnable listener)
    {
        this.watchlistChangedListener = listener;
    }

    public void setItem(int itemId, String itemName)
    {
        currentItemId = itemId;
        currentItemName =
                itemName == null || itemName.trim().isEmpty()
                        ? "Item " + itemId
                        : itemName;

        emptyState.setVisible(false);
        selectedState.setVisible(true);

        setPreferredSize(new Dimension(0, SELECTED_PANEL_HEIGHT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, SELECTED_PANEL_HEIGHT));

        nameLabel.setText(shortenName(currentItemName, 28));
        nameLabel.setToolTipText(currentItemName);

        subtitleLabel.setText("Loading live market data...");
        subtitleLabel.setForeground(MUTED);

        iconLabel.setIcon(null);
        itemManager.getImage(itemId).addTo(iconLabel);

        resetMetrics();

        taxLabel.setText(" ");
        activityLabel.setText("Checking recent activity...");
        activityLabel.setForeground(MUTED);

        updateWatchAction();

        revalidate();
        repaint();

        priceService.getItemData(
                itemId,
                data ->
                        SwingUtilities.invokeLater(
                                () ->
                                {
                                    if (currentItemId != itemId)
                                    {
                                        return;
                                    }

                                    showData(data);
                                }
                        ),
                exception ->
                        SwingUtilities.invokeLater(
                                () ->
                                {
                                    if (currentItemId != itemId)
                                    {
                                        return;
                                    }

                                    showError();
                                }
                        )
        );
    }

    private void buildEmptyState()
    {
        emptyState.setLayout(new BoxLayout(emptyState, BoxLayout.Y_AXIS));
        emptyState.setBackground(PANEL_BG);
        emptyState.setAlignmentX(LEFT_ALIGNMENT);
        emptyState.setBorder(BorderFactory.createEmptyBorder(12, 4, 8, 4));
        emptyState.setPreferredSize(new Dimension(0, EMPTY_HEIGHT));
        emptyState.setMaximumSize(new Dimension(Integer.MAX_VALUE, EMPTY_HEIGHT));

        JLabel title = new JLabel("Select an item");
        title.setForeground(Color.WHITE);
        title.setFont(ITEM_NAME);
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel description = new JLabel(
                "<html>Search above to view live prices, "
                        + "post-tax profit and recent activity.</html>"
        );
        description.setForeground(MUTED);
        description.setFont(SMALL);
        description.setAlignmentX(LEFT_ALIGNMENT);

        emptyState.add(title);
        emptyState.add(Box.createVerticalStrut(5));
        emptyState.add(description);
    }

    private void buildSelectedState()
    {
        selectedState.setLayout(new BoxLayout(selectedState, BoxLayout.Y_AXIS));
        selectedState.setBackground(PANEL_BG);
        selectedState.setAlignmentX(LEFT_ALIGNMENT);
        selectedState.setPreferredSize(new Dimension(0, SELECTED_PANEL_HEIGHT));
        selectedState.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        SELECTED_PANEL_HEIGHT
                )
        );

        selectedState.add(buildHeader());
        selectedState.add(Box.createVerticalStrut(7));
        selectedState.add(createDivider());
        selectedState.add(Box.createVerticalStrut(8));
        selectedState.add(buildMarketSection());
        selectedState.add(Box.createVerticalStrut(8));
        selectedState.add(createDivider());
        selectedState.add(Box.createVerticalStrut(8));
        selectedState.add(buildActivitySection());
        selectedState.add(Box.createVerticalStrut(8));
        selectedState.add(createDivider());
        selectedState.add(Box.createVerticalStrut(7));
        selectedState.add(buildWatchRow());
    }

    private JPanel buildHeader()
    {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(PANEL_BG);
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setPreferredSize(new Dimension(0, HEADER_HEIGHT));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, HEADER_HEIGHT));

        iconLabel.setPreferredSize(new Dimension(40, 40));
        iconLabel.setMinimumSize(new Dimension(40, 40));
        iconLabel.setMaximumSize(new Dimension(40, 40));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setBackground(PANEL_BG);

        nameLabel.setFont(ITEM_NAME);
        nameLabel.setForeground(Color.WHITE);

        subtitleLabel.setFont(SMALL);
        subtitleLabel.setForeground(MUTED);

        text.add(nameLabel);
        text.add(Box.createVerticalStrut(2));
        text.add(subtitleLabel);

        header.add(iconLabel, BorderLayout.WEST);
        header.add(text, BorderLayout.CENTER);

        return header;
    }

    private JPanel buildMarketSection()
    {
        JPanel section = fixedVerticalPanel(MARKET_HEIGHT);

        section.add(createHeading("MARKET"));
        section.add(Box.createVerticalStrut(5));

        section.add(createMetricRow("Buy", buyValue));
        section.add(createMetricRow("Sell", sellValue));
        section.add(createMetricRow("Profit after tax", profitValue));
        section.add(createMetricRow("ROI", roiValue));

        taxLabel.setFont(SMALL);
        taxLabel.setForeground(MUTED);
        taxLabel.setAlignmentX(LEFT_ALIGNMENT);

        section.add(Box.createVerticalStrut(4));
        section.add(taxLabel);

        return section;
    }

    private JPanel buildActivitySection()
    {
        JPanel section = fixedVerticalPanel(ACTIVITY_HEIGHT);

        section.add(createHeading("RECENT ACTIVITY"));
        section.add(Box.createVerticalStrut(5));

        section.add(createMetricRow("Trades / 5 min", tradesValue));
        section.add(createMetricRow("Latest trade", latestTradeValue));

        activityLabel.setFont(SMALL);
        activityLabel.setAlignmentX(LEFT_ALIGNMENT);

        section.add(Box.createVerticalStrut(4));
        section.add(activityLabel);

        return section;
    }

    private JPanel buildWatchRow()
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(PANEL_BG);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setPreferredSize(new Dimension(0, WATCH_HEIGHT));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, WATCH_HEIGHT));

        JLabel label = new JLabel("Watchlist");
        label.setForeground(MUTED);
        label.setFont(SMALL);

        watchAction.setFont(BOLD);
        watchAction.setForeground(GOLD);
        watchAction.setHorizontalAlignment(SwingConstants.RIGHT);
        watchAction.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );

        watchAction.addMouseListener(
                new MouseAdapter()
                {
                    @Override
                    public void mouseClicked(MouseEvent event)
                    {
                        toggleWatch();
                    }

                    @Override
                    public void mouseEntered(MouseEvent event)
                    {
                        if (currentItemId >= 0)
                        {
                            watchAction.setForeground(Color.WHITE);
                        }
                    }

                    @Override
                    public void mouseExited(MouseEvent event)
                    {
                        updateWatchActionColour();
                    }
                }
        );

        row.add(label, BorderLayout.WEST);
        row.add(watchAction, BorderLayout.EAST);

        return row;
    }

    private JPanel fixedVerticalPanel(int height)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(PANEL_BG);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setPreferredSize(new Dimension(0, height));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return panel;
    }

    private JPanel createMetricRow(String labelText, JLabel valueLabel)
    {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(PANEL_BG);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setPreferredSize(new Dimension(0, 22));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel label = new JLabel(labelText);
        label.setFont(BODY);
        label.setForeground(MUTED);

        valueLabel.setFont(BOLD);
        valueLabel.setForeground(Color.WHITE);
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        row.add(label, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);

        return row;
    }

    private JLabel createHeading(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(SECTION);
        label.setForeground(GOLD);
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setPreferredSize(new Dimension(0, 14));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        return label;
    }

    private JPanel createDivider()
    {
        JPanel divider = new JPanel();
        divider.setBackground(DIVIDER);
        divider.setAlignmentX(LEFT_ALIGNMENT);
        divider.setPreferredSize(new Dimension(1, 1));
        divider.setMinimumSize(new Dimension(1, 1));
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return divider;
    }

    private void toggleWatch()
    {
        if (currentItemId < 0)
        {
            return;
        }

        if (watchlistService.contains(currentItemId))
        {
            watchlistService.remove(currentItemId);
        }
        else
        {
            watchlistService.add(currentItemId);
        }

        updateWatchAction();

        if (watchlistChangedListener != null)
        {
            watchlistChangedListener.run();
        }
    }

    private void updateWatchAction()
    {
        if (currentItemId < 0)
        {
            watchAction.setText("☆ Watch");
            watchAction.setToolTipText(null);
            watchAction.setForeground(MUTED);
            return;
        }

        if (watchlistService.contains(currentItemId))
        {
            watchAction.setText("★ Watching");
            watchAction.setToolTipText("Remove from watchlist");
        }
        else
        {
            watchAction.setText("☆ Watch");
            watchAction.setToolTipText("Add to watchlist");
        }

        updateWatchActionColour();
    }

    private void updateWatchActionColour()
    {
        if (currentItemId < 0)
        {
            watchAction.setForeground(MUTED);
            return;
        }

        watchAction.setForeground(
                watchlistService.contains(currentItemId)
                        ? GREEN
                        : GOLD
        );
    }

    private void showData(W2PriceService.W2ItemData data)
    {
        int buy = data.getLow();
        int sell = data.getHigh();

        int tax = calculateTax(sell);
        int profit = sell - buy - tax;

        double roi =
                buy > 0
                        ? (profit * 100.0) / buy
                        : 0.0;

        buyValue.setText(formatGp(buy));
        sellValue.setText(formatGp(sell));

        profitValue.setText(
                (profit >= 0 ? "+" : "")
                        + formatGp(profit)
        );
        profitValue.setForeground(
                profit >= 0
                        ? GREEN
                        : RED
        );

        roiValue.setText(
                String.format(
                        Locale.ROOT,
                        "%.2f%%",
                        roi
                )
        );
        roiValue.setForeground(
                profit >= 0
                        ? GREEN
                        : RED
        );

        taxLabel.setText(
                tax > 0
                        ? "GE tax: " + formatGp(tax)
                        : "No GE tax at this price"
        );

        Integer volume = data.getFiveMinuteVolume();

        if (volume == null)
        {
            tradesValue.setText("Unavailable");
            tradesValue.setForeground(MUTED);
        }
        else
        {
            tradesValue.setText(
                    String.format(
                            Locale.ROOT,
                            "%,d",
                            volume
                    )
            );
            tradesValue.setForeground(Color.WHITE);
        }

        Long newestTimestamp = newestTimestamp(
                data.getHighTime(),
                data.getLowTime()
        );

        if (newestTimestamp == null)
        {
            latestTradeValue.setText("Unavailable");
            latestTradeValue.setForeground(MUTED);
        }
        else
        {
            long age = Math.max(
                    0,
                    Instant.now().getEpochSecond()
                            - newestTimestamp
            );

            latestTradeValue.setText(formatAge(age));
            latestTradeValue.setForeground(Color.WHITE);
        }

        updateActivity(
                volume,
                data.getHighTime(),
                data.getLowTime()
        );

        subtitleLabel.setText("Live OSRS Wiki prices");
        subtitleLabel.setForeground(MUTED);

        revalidate();
        repaint();
    }

    private void updateActivity(
            Integer volume,
            Long highTime,
            Long lowTime)
    {
        if (volume == null)
        {
            activityLabel.setText("No recent volume data");
            activityLabel.setForeground(MUTED);
            return;
        }

        long now = Instant.now().getEpochSecond();
        long oldestAge = 0;

        if (highTime != null)
        {
            oldestAge = Math.max(
                    oldestAge,
                    Math.max(0, now - highTime)
            );
        }

        if (lowTime != null)
        {
            oldestAge = Math.max(
                    oldestAge,
                    Math.max(0, now - lowTime)
            );
        }

        if (highTime != null
                && lowTime != null
                && oldestAge > 900)
        {
            activityLabel.setText("Prices may be stale");
            activityLabel.setForeground(WARNING);
            return;
        }

        if (volume < 10)
        {
            activityLabel.setText("Low recent activity");
            activityLabel.setForeground(WARNING);
        }
        else if (volume < 50)
        {
            activityLabel.setText("Moderate recent activity");
            activityLabel.setForeground(MUTED);
        }
        else
        {
            activityLabel.setText("Active in the last 5 minutes");
            activityLabel.setForeground(GREEN);
        }
    }

    private void showError()
    {
        resetMetrics();

        taxLabel.setText(" ");
        activityLabel.setText("Live market data unavailable");
        activityLabel.setForeground(RED);

        subtitleLabel.setText("Could not load current price data");
        subtitleLabel.setForeground(RED);

        revalidate();
        repaint();
    }

    private void clearDisplay()
    {
        currentItemId = -1;
        currentItemName = null;

        iconLabel.setIcon(null);
        nameLabel.setText("Select an item");
        nameLabel.setToolTipText(null);

        subtitleLabel.setText(" ");
        subtitleLabel.setForeground(MUTED);

        resetMetrics();

        taxLabel.setText(" ");
        activityLabel.setText(" ");

        updateWatchAction();

        selectedState.setVisible(false);
        emptyState.setVisible(true);

        setPreferredSize(new Dimension(0, EMPTY_HEIGHT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, EMPTY_HEIGHT));

        revalidate();
        repaint();
    }

    private void resetMetrics()
    {
        buyValue.setText("—");
        sellValue.setText("—");
        profitValue.setText("—");
        roiValue.setText("—");
        tradesValue.setText("—");
        latestTradeValue.setText("—");

        buyValue.setForeground(Color.WHITE);
        sellValue.setForeground(Color.WHITE);
        profitValue.setForeground(Color.WHITE);
        roiValue.setForeground(Color.WHITE);
        tradesValue.setForeground(Color.WHITE);
        latestTradeValue.setForeground(Color.WHITE);
    }

    private int calculateTax(int sellPrice)
    {
        long tax = sellPrice / 50L;
        return (int) Math.min(tax, GE_TAX_CAP);
    }

    private Long newestTimestamp(Long highTime, Long lowTime)
    {
        if (highTime == null)
        {
            return lowTime;
        }

        if (lowTime == null)
        {
            return highTime;
        }

        return Math.max(highTime, lowTime);
    }

    private String formatAge(long seconds)
    {
        if (seconds < 60)
        {
            return seconds + " sec ago";
        }

        if (seconds < 3600)
        {
            long minutes = seconds / 60;

            return minutes
                    + (minutes == 1
                    ? " min ago"
                    : " mins ago");
        }

        long hours = seconds / 3600;

        return hours
                + (hours == 1
                ? " hr ago"
                : " hrs ago");
    }

    private String formatGp(long amount)
    {
        return String.format(
                Locale.ROOT,
                "%,d gp",
                amount
        );
    }

    private String shortenName(String name, int maxLength)
    {
        if (name == null || name.length() <= maxLength)
        {
            return name;
        }

        return name.substring(
                0,
                Math.max(1, maxLength - 1)
        ) + "…";
    }
}
