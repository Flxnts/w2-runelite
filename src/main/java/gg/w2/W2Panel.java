package gg.w2;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;
import net.runelite.http.api.item.ItemPrice;

public class W2Panel extends PluginPanel
{
    private static final String TAB_FLIPS = "FLIPS";
    private static final String TAB_LOOKUP = "LOOKUP";
    private static final String TAB_WATCH = "WATCH";
    private static final String TAB_TRADES = "TRADES";

    private static final Color GOLD = new Color(205, 173, 92);
    private static final Color GREEN = new Color(94, 186, 125);
    private static final Color RED = new Color(220, 90, 90);
    private static final Color WARNING = new Color(224, 183, 92);
    private static final Color MUTED = new Color(155, 155, 155);
    private static final Color PANEL_BG = ColorScheme.DARK_GRAY_COLOR;
    private static final Color CARD_BG = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color TAB_BG = new Color(38, 38, 38);
    private static final Color TAB_SELECTED = new Color(54, 49, 38);

    private static final Font BODY = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font SMALL = new Font("SansSerif", Font.PLAIN, 11);
    private static final Font BOLD = new Font("SansSerif", Font.BOLD, 12);
    private static final Font SMALL_BOLD = new Font("SansSerif", Font.BOLD, 11);
    private static final Font W2_TITLE = new Font("SansSerif", Font.BOLD, 21);
    private static final Font PAGE_TITLE = new Font("SansSerif", Font.BOLD, 17);

    private final ItemManager itemManager;
    private final W2PriceService priceService;
    private final W2WatchlistService watchlistService;
    private final W2BankService bankService;
    private final W2TradeService tradeService;
    private final W2ItemLookupPanel lookupPanel;

    private final JPanel pages = new JPanel(new CardLayout());

    private JButton flipsTab;
    private JButton lookupTab;
    private JButton watchTab;
    private JButton tradesTab;

    private JTextField bankField;
    private JLabel bankStatus;
    private JLabel flipStatus;
    private JPanel flipRows;

    private JPanel watchRows;
    private JLabel watchCount;

    private JPanel openPositionRows;
    private JPanel tradeRows;
    private JLabel tradeSummary;
    private JLabel tradeMeta;

    private final JTextField searchField = new JTextField();
    private final DefaultListModel<ItemPrice> suggestionModel = new DefaultListModel<>();
    private final JList<ItemPrice> suggestionList = new JList<>(suggestionModel);
    private final JScrollPane suggestions = new JScrollPane(suggestionList);

    private final Map<Integer, String> itemNameCache = new HashMap<>();
    private boolean suppressSuggestions;

    public W2Panel(
            ItemManager itemManager,
            W2PriceService priceService,
            W2WatchlistService watchlistService,
            W2BankService bankService,
            W2TradeService tradeService)
    {
        this.itemManager = itemManager;
        this.priceService = priceService;
        this.watchlistService = watchlistService;
        this.bankService = bankService;
        this.tradeService = tradeService;

        lookupPanel = new W2ItemLookupPanel(
                itemManager,
                priceService,
                watchlistService
        );

        lookupPanel.setWatchlistChangedListener(this::refreshWatchlist);

        setLayout(new BorderLayout());
        setBackground(PANEL_BG);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(PANEL_BG);

        top.add(createHeader());
        top.add(createDivider());
        top.add(createTabs());

        add(top, BorderLayout.NORTH);

        pages.setBackground(PANEL_BG);
        pages.add(wrapPage(createFlipsPage()), TAB_FLIPS);
        pages.add(wrapPage(createLookupPage()), TAB_LOOKUP);
        pages.add(wrapPage(createWatchPage()), TAB_WATCH);
        pages.add(wrapPage(createTradesPage()), TAB_TRADES);

        add(pages, BorderLayout.CENTER);

        showTab(TAB_FLIPS);

        SwingUtilities.invokeLater(this::refreshFlips);
    }

    private JPanel createHeader()
    {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(PANEL_BG);
        header.setBorder(BorderFactory.createEmptyBorder(9, 11, 7, 11));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        JLabel title = new JLabel("W2");
        title.setForeground(GOLD);
        title.setFont(W2_TITLE);

        JLabel live = new JLabel("● LIVE");
        live.setForeground(GREEN);
        live.setFont(SMALL_BOLD);
        live.setToolTipText("Live OSRS Wiki market data");

        header.add(title, BorderLayout.WEST);
        header.add(live, BorderLayout.EAST);

        return header;
    }

    private JPanel createTabs()
    {
        JPanel tabs = new JPanel(new GridLayout(1, 4, 1, 0));
        tabs.setBackground(PANEL_BG);
        tabs.setBorder(BorderFactory.createEmptyBorder(5, 8, 7, 8));
        tabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        flipsTab = createTabButton("Flips", TAB_FLIPS);
        lookupTab = createTabButton("Lookup", TAB_LOOKUP);
        watchTab = createTabButton("Watch", TAB_WATCH);
        tradesTab = createTabButton("Trades", TAB_TRADES);

        tabs.add(flipsTab);
        tabs.add(lookupTab);
        tabs.add(watchTab);
        tabs.add(tradesTab);

        return tabs;
    }

    private JButton createTabButton(String text, String page)
    {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 10));
        button.setMargin(new Insets(3, 1, 3, 1));
        button.setFocusable(false);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(event -> showTab(page));
        return button;
    }

    private JPanel createFlipsPage()
    {
        JPanel page = createPage();

        addPageTitle(page, "Flips");
        addSectionHeading(page, "TRADING BANK");

        JPanel bankRow = new JPanel(new BorderLayout(6, 0));
        bankRow.setBackground(PANEL_BG);
        bankRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        bankRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 31));

        bankField = new JTextField(formatCompact(bankService.getBank()));
        bankField.setFont(BODY);
        bankField.setToolTipText("Enter an amount such as 25m, 500m or 1b");

        JButton apply = new JButton("Apply");
        apply.setFont(BOLD);
        apply.setMargin(new Insets(3, 9, 3, 9));
        apply.setFocusable(false);
        apply.setFocusPainted(false);
        apply.addActionListener(event -> applyBank());

        bankField.addActionListener(event -> applyBank());

        bankRow.add(bankField, BorderLayout.CENTER);
        bankRow.add(apply, BorderLayout.EAST);

        page.add(bankRow);
        page.add(Box.createVerticalStrut(5));

        JPanel quick = new JPanel(new GridLayout(1, 4, 4, 0));
        quick.setBackground(PANEL_BG);
        quick.setAlignmentX(Component.LEFT_ALIGNMENT);
        quick.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));

        addBankButton(quick, "10m", 10_000_000L);
        addBankButton(quick, "100m", 100_000_000L);
        addBankButton(quick, "500m", 500_000_000L);
        addBankButton(quick, "1b", 1_000_000_000L);

        page.add(quick);
        page.add(Box.createVerticalStrut(5));

        bankStatus = new JLabel();
        bankStatus.setForeground(MUTED);
        bankStatus.setFont(SMALL);
        bankStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        updateBankStatus();
        page.add(bankStatus);

        page.add(Box.createVerticalStrut(13));

        JPanel marketHeader = new JPanel(new BorderLayout(8, 0));
        marketHeader.setBackground(PANEL_BG);
        marketHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        marketHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 19));

        JLabel heading = new JLabel("WORTH A LOOK");
        heading.setForeground(GOLD);
        heading.setFont(SMALL_BOLD);

        JLabel refresh = new JLabel("Refresh");
        refresh.setForeground(GOLD);
        refresh.setFont(BOLD);
        refresh.setToolTipText("Refresh live flip candidates");
        refresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refresh.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent event)
            {
                refreshFlips();
            }

            @Override
            public void mouseEntered(MouseEvent event)
            {
                refresh.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent event)
            {
                refresh.setForeground(GOLD);
            }
        });

        marketHeader.add(heading, BorderLayout.WEST);
        marketHeader.add(refresh, BorderLayout.EAST);

        page.add(marketHeader);
        page.add(Box.createVerticalStrut(4));

        flipStatus = new JLabel("Loading live prices...");
        flipStatus.setForeground(MUTED);
        flipStatus.setFont(SMALL);
        flipStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        flipStatus.setMaximumSize(new Dimension(Integer.MAX_VALUE, 17));
        page.add(flipStatus);

        page.add(Box.createVerticalStrut(7));

        flipRows = new JPanel();
        flipRows.setLayout(new BoxLayout(flipRows, BoxLayout.Y_AXIS));
        flipRows.setBackground(PANEL_BG);
        flipRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(flipRows);

        page.add(Box.createVerticalStrut(10));
        addDataFooter(page);

        return page;
    }

    private JPanel createLookupPage()
    {
        JPanel page = createPage();

        addPageTitle(page, "Lookup");
        addSectionHeading(page, "SEARCH ITEMS");

        configureSearch();

        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 31));
        page.add(searchField);

        page.add(Box.createVerticalStrut(3));

        suggestions.setAlignmentX(Component.LEFT_ALIGNMENT);
        suggestions.setBorder(
                BorderFactory.createLineBorder(
                        ColorScheme.MEDIUM_GRAY_COLOR
                )
        );
        page.add(suggestions);
        hideSuggestions();

        page.add(Box.createVerticalStrut(9));

        lookupPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(lookupPanel);

        page.add(Box.createVerticalStrut(9));
        addDataFooter(page);

        return page;
    }

    private JPanel createWatchPage()
    {
        JPanel page = createPage();

        JPanel titleRow = new JPanel(new BorderLayout(8, 0));
        titleRow.setBackground(PANEL_BG);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 27));

        JLabel title = new JLabel("Watchlist");
        title.setForeground(GOLD);
        title.setFont(PAGE_TITLE);

        watchCount = new JLabel();
        watchCount.setForeground(MUTED);
        watchCount.setFont(SMALL);

        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(watchCount, BorderLayout.EAST);

        page.add(titleRow);
        page.add(Box.createVerticalStrut(2));

        JLabel description = new JLabel("Live price and 5-minute activity");
        description.setForeground(MUTED);
        description.setFont(SMALL);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(description);

        page.add(Box.createVerticalStrut(9));

        watchRows = new JPanel();
        watchRows.setLayout(new BoxLayout(watchRows, BoxLayout.Y_AXIS));
        watchRows.setBackground(PANEL_BG);
        watchRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(watchRows);

        page.add(Box.createVerticalStrut(10));
        addDataFooter(page);

        refreshWatchlist();

        return page;
    }

    private JPanel createTradesPage()
    {
        JPanel page = createPage();

        addPageTitle(page, "Trades");

        JLabel intro =
                new JLabel(
                        "Automatic Grand Exchange tracking"
                );

        intro.setForeground(Color.WHITE);
        intro.setFont(BOLD);
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(intro);

        page.add(Box.createVerticalStrut(3));

        JLabel detail =
                new JLabel(
                        "<html>Buys and sells are recorded locally. "
                                + "W2 matches sells against your earlier buys "
                                + "and calculates realised profit after GE tax.</html>"
                );

        detail.setForeground(MUTED);
        detail.setFont(SMALL);
        detail.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(detail);

        page.add(Box.createVerticalStrut(10));

        JPanel summaryCard =
                new JPanel();

        summaryCard.setLayout(
                new BoxLayout(
                        summaryCard,
                        BoxLayout.Y_AXIS
                )
        );

        summaryCard.setBackground(CARD_BG);
        summaryCard.setBorder(
                BorderFactory.createEmptyBorder(
                        8,
                        8,
                        8,
                        8
                )
        );
        summaryCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        summaryCard.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        52
                )
        );

        tradeSummary =
                new JLabel(
                        "Realised after tax: 0"
                );

        tradeSummary.setFont(BOLD);
        tradeSummary.setAlignmentX(Component.LEFT_ALIGNMENT);

        tradeMeta =
                new JLabel(
                        "Tax paid: 0 • Open: 0"
                );

        tradeMeta.setForeground(MUTED);
        tradeMeta.setFont(SMALL);
        tradeMeta.setAlignmentX(Component.LEFT_ALIGNMENT);

        summaryCard.add(tradeSummary);
        summaryCard.add(Box.createVerticalStrut(2));
        summaryCard.add(tradeMeta);

        page.add(summaryCard);
        page.add(Box.createVerticalStrut(10));

        addSectionHeading(
                page,
                "OPEN POSITIONS"
        );

        openPositionRows =
                new JPanel();

        openPositionRows.setLayout(
                new BoxLayout(
                        openPositionRows,
                        BoxLayout.Y_AXIS
                )
        );

        openPositionRows.setBackground(PANEL_BG);
        openPositionRows.setAlignmentX(Component.LEFT_ALIGNMENT);

        page.add(openPositionRows);
        page.add(Box.createVerticalStrut(11));

        addSectionHeading(
                page,
                "RECENT FILLS"
        );

        tradeRows =
                new JPanel();

        tradeRows.setLayout(
                new BoxLayout(
                        tradeRows,
                        BoxLayout.Y_AXIS
                )
        );

        tradeRows.setBackground(PANEL_BG);
        tradeRows.setAlignmentX(Component.LEFT_ALIGNMENT);

        page.add(tradeRows);
        page.add(Box.createVerticalStrut(10));

        JPanel actions =
                new JPanel(
                        new BorderLayout()
                );

        actions.setBackground(PANEL_BG);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        20
                )
        );

        JLabel local =
                new JLabel(
                        "Stored locally • account profile"
                );

        local.setForeground(MUTED);
        local.setFont(SMALL);

        JLabel clear =
                new JLabel(
                        "<html><u>Clear history</u></html>"
                );

        clear.setForeground(MUTED);
        clear.setFont(SMALL);
        clear.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );

        clear.setToolTipText(
                "Delete W2's locally stored trade history"
        );

        clear.addMouseListener(
                new MouseAdapter()
                {
                    @Override
                    public void mouseClicked(
                            MouseEvent event)
                    {
                        int choice =
                                JOptionPane.showConfirmDialog(
                                        W2Panel.this,
                                        "Clear W2 trade history for this profile?",
                                        "Clear trade history",
                                        JOptionPane.YES_NO_OPTION,
                                        JOptionPane.WARNING_MESSAGE
                                );

                        if (choice
                                == JOptionPane.YES_OPTION)
                        {
                            tradeService.clearHistory();
                            refreshTrades();
                        }
                    }

                    @Override
                    public void mouseEntered(
                            MouseEvent event)
                    {
                        clear.setForeground(
                                Color.WHITE
                        );
                    }

                    @Override
                    public void mouseExited(
                            MouseEvent event)
                    {
                        clear.setForeground(
                                MUTED
                        );
                    }
                }
        );

        actions.add(
                local,
                BorderLayout.WEST
        );

        actions.add(
                clear,
                BorderLayout.EAST
        );

        page.add(createDivider());
        page.add(Box.createVerticalStrut(7));
        page.add(actions);

        refreshTrades();

        return page;
    }

    public void refreshTrades()
    {
        if (tradeRows == null
                || openPositionRows == null
                || tradeSummary == null
                || tradeMeta == null)
        {
            return;
        }

        tradeRows.removeAll();
        openPositionRows.removeAll();

        W2TradeService.TradeAnalysis analysis =
                tradeService.getAnalysis();

        long realised =
                analysis.getRealisedProfitAfterTax();

        tradeSummary.setText(
                "Realised after tax: "
                        + formatSignedCompact(
                        realised
                )
        );

        tradeSummary.setForeground(
                realised > 0
                        ? GREEN
                        : realised < 0
                        ? RED
                        : MUTED
        );

        int openCount =
                analysis
                        .getOpenPositions()
                        .size();

        tradeMeta.setText(
                "Tax paid: "
                        + formatCompact(
                        analysis.getTaxPaid()
                )
                        + "  •  Open: "
                        + openCount
                        + "  •  Cost: "
                        + formatCompact(
                        analysis.getOpenCost()
                )
        );

        List<W2TradeService.OpenPosition> positions =
                analysis.getOpenPositions();

        if (positions.isEmpty())
        {
            JLabel empty =
                    new JLabel(
                            "No open positions"
                    );

            empty.setForeground(MUTED);
            empty.setFont(BODY);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);

            openPositionRows.add(empty);
        }
        else
        {
            int shown =
                    Math.min(
                            8,
                            positions.size()
                    );

            for (int i = 0;
                 i < shown;
                 i++)
            {
                addOpenPositionRow(
                        positions.get(i)
                );

                if (i < shown - 1)
                {
                    openPositionRows.add(
                            Box.createVerticalStrut(5)
                    );
                }
            }

            if (positions.size() > shown)
            {
                openPositionRows.add(
                        Box.createVerticalStrut(5)
                );

                JLabel more =
                        new JLabel(
                                "+"
                                        + (positions.size() - shown)
                                        + " more open position"
                                        + (positions.size() - shown == 1
                                        ? ""
                                        : "s")
                        );

                more.setForeground(MUTED);
                more.setFont(SMALL);
                more.setAlignmentX(Component.LEFT_ALIGNMENT);

                openPositionRows.add(more);
            }
        }

        List<W2TradeService.TradeView> views =
                analysis.getTradeViews();

        if (views.isEmpty())
        {
            JLabel empty =
                    new JLabel(
                            "No GE fills recorded yet."
                    );

            empty.setForeground(MUTED);
            empty.setFont(BODY);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel hint =
                    new JLabel(
                            "Complete a buy or sell in the Grand Exchange."
                    );

            hint.setForeground(MUTED);
            hint.setFont(SMALL);
            hint.setAlignmentX(Component.LEFT_ALIGNMENT);

            tradeRows.add(empty);
            tradeRows.add(Box.createVerticalStrut(4));
            tradeRows.add(hint);
        }
        else
        {
            int shown =
                    Math.min(
                            25,
                            views.size()
                    );

            for (int i = 0;
                 i < shown;
                 i++)
            {
                addTradeRow(
                        views.get(i)
                );

                if (i < shown - 1)
                {
                    tradeRows.add(
                            Box.createVerticalStrut(5)
                    );
                }
            }

            if (views.size() > shown)
            {
                tradeRows.add(
                        Box.createVerticalStrut(5)
                );

                JLabel older =
                        new JLabel(
                                "Showing latest "
                                        + shown
                                        + " fills"
                        );

                older.setForeground(MUTED);
                older.setFont(SMALL);
                older.setAlignmentX(Component.LEFT_ALIGNMENT);
                tradeRows.add(older);
            }
        }

        openPositionRows.revalidate();
        openPositionRows.repaint();

        tradeRows.revalidate();
        tradeRows.repaint();

        revalidate();
        repaint();
    }

    private void addOpenPositionRow(
            W2TradeService.OpenPosition position)
    {
        JPanel row =
                new JPanel(
                        new BorderLayout(
                                7,
                                0
                        )
                );

        row.setBackground(CARD_BG);
        row.setBorder(
                BorderFactory.createEmptyBorder(
                        7,
                        7,
                        7,
                        7
                )
        );
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        56
                )
        );

        JLabel icon =
                new JLabel();

        icon.setPreferredSize(
                new Dimension(
                        32,
                        32
                )
        );

        itemManager
                .getImage(
                        position.getItemId()
                )
                .addTo(icon);

        JPanel details =
                new JPanel();

        details.setLayout(
                new BoxLayout(
                        details,
                        BoxLayout.Y_AXIS
                )
        );

        details.setBackground(CARD_BG);

        final String[] resolvedName =
                new String[]{
                        itemNameCache.get(
                                position.getItemId()
                        )
                };

        JLabel title =
                new JLabel(
                        resolvedName[0] == null
                                ? "Loading item..."
                                : shortenName(
                                resolvedName[0],
                                22
                        )
                );

        title.setForeground(Color.WHITE);
        title.setFont(BOLD);

        JLabel detail =
                new JLabel(
                        "Qty "
                                + formatCompact(
                                position.getQuantity()
                        )
                                + "  •  Avg "
                                + formatCompact(
                                position.getAverageBuyPrice()
                        )
                                + "  •  Cost "
                                + formatCompact(
                                position.getCost()
                        )
                );

        detail.setForeground(MUTED);
        detail.setFont(SMALL);

        details.add(title);
        details.add(detail);

        row.add(icon, BorderLayout.WEST);
        row.add(details, BorderLayout.CENTER);

        row.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );

        row.addMouseListener(
                new MouseAdapter()
                {
                    @Override
                    public void mouseClicked(
                            MouseEvent event)
                    {
                        if (resolvedName[0] != null)
                        {
                            openItem(
                                    position.getItemId(),
                                    resolvedName[0]
                            );
                        }
                    }
                }
        );

        openPositionRows.add(row);

        if (resolvedName[0] == null)
        {
            priceService.getItemName(
                    position.getItemId(),
                    itemName ->
                            SwingUtilities.invokeLater(
                                    () ->
                                    {
                                        resolvedName[0] =
                                                itemName;

                                        itemNameCache.put(
                                                position.getItemId(),
                                                itemName
                                        );

                                        title.setText(
                                                shortenName(
                                                        itemName,
                                                        22
                                                )
                                        );

                                        title.setToolTipText(
                                                itemName
                                        );
                                    }
                            ),
                    ignored ->
                            SwingUtilities.invokeLater(
                                    () ->
                                            title.setText(
                                                    "Item "
                                                            + position.getItemId()
                                            )
                            )
            );
        }
    }

    private void addTradeRow(
            W2TradeService.TradeView view)
    {
        W2TradeService.W2Trade trade =
                view.getTrade();

        JPanel row =
                new JPanel(
                        new BorderLayout(
                                8,
                                0
                        )
                );

        row.setBackground(CARD_BG);
        row.setBorder(
                BorderFactory.createEmptyBorder(
                        7,
                        7,
                        7,
                        7
                )
        );
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        72
                )
        );

        JLabel icon =
                new JLabel();

        icon.setPreferredSize(
                new Dimension(
                        32,
                        32
                )
        );

        itemManager
                .getImage(
                        trade.getItemId()
                )
                .addTo(icon);

        JPanel details =
                new JPanel();

        details.setLayout(
                new BoxLayout(
                        details,
                        BoxLayout.Y_AXIS
                )
        );

        details.setBackground(CARD_BG);

        final String[] resolvedName =
                new String[]{
                        itemNameCache.get(
                                trade.getItemId()
                        )
                };

        String prefix =
                trade.isBuy()
                        ? "BUY  "
                        : "SELL  ";

        JLabel title =
                new JLabel(
                        prefix
                                + (resolvedName[0] == null
                                ? "Item " + trade.getItemId()
                                : shortenName(
                                resolvedName[0],
                                20
                        ))
                );

        title.setForeground(
                trade.isBuy()
                        ? Color.WHITE
                        : GREEN
        );

        title.setFont(BOLD);

        JLabel price =
                new JLabel(
                        formatCompact(
                                trade.getQuantity()
                        )
                                + " @ "
                                + formatCompact(
                                trade.getAveragePrice()
                        )
                                + "  •  "
                                + formatCompact(
                                trade.getGrossValue()
                        )
                                + " total"
                );

        price.setForeground(MUTED);
        price.setFont(SMALL);

        JLabel result =
                new JLabel(" ");

        result.setFont(SMALL);

        if (!trade.isBuy())
        {
            if (view.hasRealisedProfit())
            {
                long profit =
                        view.getRealisedProfit();

                result.setText(
                        "P/L "
                                + formatSignedCompact(
                                profit
                        )
                                + "  •  Tax "
                                + formatCompact(
                                view.getTax()
                        )
                );

                result.setForeground(
                        profit > 0
                                ? GREEN
                                : profit < 0
                                ? RED
                                : MUTED
                );
            }
            else
            {
                result.setText(
                        "No earlier W2 buy to match"
                );

                result.setForeground(WARNING);
            }
        }
        else
        {
            result.setText(
                    "Added to open position"
            );

            result.setForeground(MUTED);
        }

        String timeText =
                DateTimeFormatter
                        .ofPattern("HH:mm")
                        .withZone(
                                ZoneId.systemDefault()
                        )
                        .format(
                                trade.getTime()
                        );

        JLabel time =
                new JLabel(timeText);

        time.setForeground(MUTED);
        time.setFont(SMALL);

        details.add(title);
        details.add(price);
        details.add(result);

        row.add(icon, BorderLayout.WEST);
        row.add(details, BorderLayout.CENTER);
        row.add(time, BorderLayout.EAST);

        row.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );

        row.addMouseListener(
                new MouseAdapter()
                {
                    @Override
                    public void mouseClicked(
                            MouseEvent event)
                    {
                        if (resolvedName[0] != null)
                        {
                            openItem(
                                    trade.getItemId(),
                                    resolvedName[0]
                            );
                        }
                    }
                }
        );

        tradeRows.add(row);

        if (resolvedName[0] == null)
        {
            priceService.getItemName(
                    trade.getItemId(),
                    itemName ->
                            SwingUtilities.invokeLater(
                                    () ->
                                    {
                                        resolvedName[0] =
                                                itemName;

                                        itemNameCache.put(
                                                trade.getItemId(),
                                                itemName
                                        );

                                        title.setText(
                                                prefix
                                                        + shortenName(
                                                        itemName,
                                                        20
                                                )
                                        );

                                        title.setToolTipText(
                                                itemName
                                        );
                                    }
                            ),
                    ignored ->
                    {
                    }
            );
        }
    }

    private JScrollPane wrapPage(JPanel page)
    {
        JScrollPane scroll = new JScrollPane(page);
        scroll.setBorder(null);
        scroll.setBackground(PANEL_BG);
        scroll.getViewport().setBackground(PANEL_BG);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        return scroll;
    }

    private JPanel createPage()
    {
        JPanel page = new JPanel();
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBackground(PANEL_BG);
        page.setBorder(BorderFactory.createEmptyBorder(4, 10, 12, 10));
        return page;
    }

    private void applyBank()
    {
        try
        {
            long bank = bankService.parseAmount(bankField.getText());
            bankService.setBank(bank);
            bankField.setText(formatCompact(bank));
            updateBankStatus();
            refreshFlips();
        }
        catch (IllegalArgumentException exception)
        {
            bankStatus.setText(exception.getMessage());
            bankStatus.setForeground(RED);
        }
    }

    private void addBankButton(JPanel panel, String text, long value)
    {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 10));
        button.setMargin(new Insets(2, 1, 2, 1));
        button.setFocusable(false);
        button.setFocusPainted(false);

        button.addActionListener(event ->
        {
            bankService.setBank(value);
            bankField.setText(text);
            updateBankStatus();
            refreshFlips();
        });

        panel.add(button);
    }

    private void updateBankStatus()
    {
        if (bankStatus == null)
        {
            return;
        }

        bankStatus.setForeground(MUTED);
        bankStatus.setText("Trading bank: " + formatCompact(bankService.getBank()));
    }

    private void refreshFlips()
    {
        if (flipRows == null || flipStatus == null)
        {
            return;
        }

        flipRows.removeAll();
        flipRows.revalidate();
        flipRows.repaint();

        flipStatus.setText("Reading the market...");
        flipStatus.setForeground(MUTED);

        priceService.getFlipCandidates(
                bankService.getBank(),
                candidates -> SwingUtilities.invokeLater(
                        () -> showFlipCandidates(candidates)
                ),
                exception -> SwingUtilities.invokeLater(() ->
                {
                    flipStatus.setText("Could not load live flips.");
                    flipStatus.setForeground(RED);
                })
        );
    }

    private void showFlipCandidates(List<W2PriceService.W2FlipCandidate> candidates)
    {
        flipRows.removeAll();

        if (candidates.isEmpty())
        {
            flipStatus.setText("No current flips fit this bank.");
            flipRows.revalidate();
            flipRows.repaint();
            return;
        }

        flipStatus.setForeground(MUTED);
        flipStatus.setText("Live prices • profit after tax");

        int shown = Math.min(12, candidates.size());

        for (int i = 0; i < shown; i++)
        {
            W2PriceService.W2FlipCandidate candidate = candidates.get(i);

            itemNameCache.put(candidate.getId(), candidate.getName());

            addFlipRow(candidate);

            if (i < shown - 1)
            {
                flipRows.add(Box.createVerticalStrut(5));
            }
        }

        flipRows.revalidate();
        flipRows.repaint();
    }

    private void addFlipRow(W2PriceService.W2FlipCandidate candidate)
    {
        boolean hasWarning =
                candidate.hasLiquidityWarning();

        int rowHeight =
                hasWarning
                        ? 92
                        : 77;

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(CARD_BG);
        row.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        rowHeight
                )
        );
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setToolTipText("Open " + candidate.getName() + " in Lookup");

        JLabel icon = new JLabel();
        icon.setPreferredSize(new Dimension(34, 34));
        itemManager.getImage(candidate.getId()).addTo(icon);

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBackground(CARD_BG);

        JLabel name = new JLabel(shortenName(candidate.getName(), 25));
        name.setForeground(Color.WHITE);
        name.setFont(BOLD);
        name.setToolTipText(candidate.getName());

        JLabel market = new JLabel(
                formatCompact(candidate.getBuy())
                        + " → "
                        + formatCompact(candidate.getSell())
        );
        market.setForeground(MUTED);
        market.setFont(SMALL);

        JLabel profit = new JLabel(
                "+"
                        + formatCompact(candidate.getProfit())
                        + " ea  •  "
                        + String.format(
                        Locale.ROOT,
                        "%.2f%%",
                        candidate.getRoi()
                )
        );
        profit.setForeground(GREEN);
        profit.setFont(BOLD);

        String activityText =
                "Qty "
                        + formatCompact(
                        candidate.getSuggestedQuantity()
                )
                        + "  •  "
                        + formatCompact(
                        candidate.getVolume()
                )
                        + "/5m"
                        + "  •  "
                        + candidate.getMarketSignal();

        /*
         * Fresh/Recent are normal and do not need to consume scarce sidebar
         * space. Aging is worth surfacing because it changes the decision.
         */
        if ("Aging".equals(candidate.getFreshnessSignal()))
        {
            activityText += "  •  Aging";
        }

        JLabel activity =
                new JLabel(activityText);

        activity.setFont(SMALL);

        boolean caution =
                !"Active".equals(
                        candidate.getMarketSignal()
                )
                        || "Aging".equals(
                        candidate.getFreshnessSignal()
                );

        activity.setForeground(
                caution
                        ? WARNING
                        : MUTED
        );
        activity.setToolTipText(
                "5-minute activity: "
                        + candidate.getVolume()
                        + " trades • "
                        + candidate.getMarketSignal()
                        + " • "
                        + candidate.getFreshnessSignal()
        );

        details.add(name);
        details.add(market);
        details.add(profit);
        details.add(activity);

        if (hasWarning)
        {
            JLabel warning =
                    new JLabel(
                            "⚠ "
                                    + candidate.getLiquidityWarning()
                    );

            warning.setFont(SMALL);
            warning.setForeground(WARNING);
            warning.setToolTipText(
                    candidate.getLiquidityWarning()
            );

            details.add(warning);
        }

        row.add(icon, BorderLayout.WEST);
        row.add(details, BorderLayout.CENTER);

        row.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent event)
            {
                openItem(
                        candidate.getId(),
                        candidate.getName()
                );
            }

            @Override
            public void mouseEntered(MouseEvent event)
            {
                row.setBackground(TAB_SELECTED);
                details.setBackground(TAB_SELECTED);
            }

            @Override
            public void mouseExited(MouseEvent event)
            {
                row.setBackground(CARD_BG);
                details.setBackground(CARD_BG);
            }
        });

        flipRows.add(row);
    }

    private void refreshWatchlist()
    {
        if (watchRows == null || watchCount == null)
        {
            return;
        }

        watchRows.removeAll();

        List<Integer> ids = watchlistService.getAll();

        watchCount.setText(
                ids.size()
                        + (ids.size() == 1 ? " item" : " items")
        );

        if (ids.isEmpty())
        {
            JLabel empty = new JLabel("No watched items");
            empty.setForeground(MUTED);
            empty.setFont(BODY);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel hint = new JLabel("Add one from Lookup to track it here.");
            hint.setForeground(MUTED);
            hint.setFont(SMALL);
            hint.setAlignmentX(Component.LEFT_ALIGNMENT);

            watchRows.add(empty);
            watchRows.add(Box.createVerticalStrut(4));
            watchRows.add(hint);
        }
        else
        {
            for (int i = 0; i < ids.size(); i++)
            {
                addWatchRow(ids.get(i));

                if (i < ids.size() - 1)
                {
                    watchRows.add(Box.createVerticalStrut(5));
                }
            }
        }

        watchRows.revalidate();
        watchRows.repaint();
    }

    private void addWatchRow(int itemId)
    {
        final String[] resolvedName =
                new String[]{
                        itemNameCache.get(itemId)
                };

        JPanel row = new JPanel(new BorderLayout(7, 0));
        row.setBackground(CARD_BG);
        row.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel icon = new JLabel();
        icon.setPreferredSize(new Dimension(34, 34));
        itemManager.getImage(itemId).addTo(icon);

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBackground(CARD_BG);

        JLabel name =
                new JLabel(
                        resolvedName[0] == null
                                ? "Loading item..."
                                : shortenName(
                                resolvedName[0],
                                23
                        )
                );

        name.setForeground(Color.WHITE);
        name.setFont(BOLD);

        if (resolvedName[0] != null)
        {
            name.setToolTipText(
                    resolvedName[0]
            );
        }

        JLabel prices = new JLabel("Loading prices...");
        prices.setForeground(MUTED);
        prices.setFont(SMALL);

        JLabel activity = new JLabel("Reading activity...");
        activity.setForeground(MUTED);
        activity.setFont(SMALL);

        details.add(name);
        details.add(prices);
        details.add(activity);

        JLabel remove = new JLabel("×");
        remove.setForeground(MUTED);
        remove.setFont(new Font("SansSerif", Font.BOLD, 18));
        remove.setToolTipText("Remove from watchlist");
        remove.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        row.addMouseListener(
                new MouseAdapter()
                {
                    @Override
                    public void mouseClicked(
                            MouseEvent event)
                    {
                        if (resolvedName[0] == null)
                        {
                            return;
                        }

                        openItem(
                                itemId,
                                resolvedName[0]
                        );
                    }
                }
        );

        remove.addMouseListener(
                new MouseAdapter()
                {
                    @Override
                    public void mouseClicked(
                            MouseEvent event)
                    {
                        watchlistService.remove(itemId);
                        refreshWatchlist();
                    }

                    @Override
                    public void mouseEntered(
                            MouseEvent event)
                    {
                        remove.setForeground(RED);
                    }

                    @Override
                    public void mouseExited(
                            MouseEvent event)
                    {
                        remove.setForeground(MUTED);
                    }
                }
        );

        row.add(icon, BorderLayout.WEST);
        row.add(details, BorderLayout.CENTER);
        row.add(remove, BorderLayout.EAST);

        watchRows.add(row);

        if (resolvedName[0] == null)
        {
            priceService.getItemName(
                    itemId,
                    itemName ->
                            SwingUtilities.invokeLater(
                                    () ->
                                    {
                                        resolvedName[0] =
                                                itemName;

                                        itemNameCache.put(
                                                itemId,
                                                itemName
                                        );

                                        name.setText(
                                                shortenName(
                                                        itemName,
                                                        23
                                                )
                                        );

                                        name.setToolTipText(
                                                itemName
                                        );

                                        row.revalidate();
                                        row.repaint();
                                    }
                            ),
                    exception ->
                            SwingUtilities.invokeLater(
                                    () ->
                                    {
                                        name.setText(
                                                "Item unavailable"
                                        );

                                        name.setForeground(
                                                MUTED
                                        );

                                        name.setToolTipText(
                                                "Could not resolve item "
                                                        + itemId
                                        );
                                    }
                            )
            );
        }

        priceService.getItemData(
                itemId,
                data ->
                        SwingUtilities.invokeLater(
                                () ->
                                {
                                    long tax =
                                            Math.min(
                                                    5_000_000L,
                                                    data.getHigh() / 50L
                                            );

                                    long profit =
                                            (long) data.getHigh()
                                                    - data.getLow()
                                                    - tax;

                                    double roi =
                                            data.getLow() <= 0
                                                    ? 0.0
                                                    : profit
                                                    * 100.0
                                                    / data.getLow();

                                    prices.setText(
                                            formatCompact(
                                                    data.getLow()
                                            )
                                                    + " → "
                                                    + formatCompact(
                                                    data.getHigh()
                                            )
                                                    + "  •  "
                                                    + formatSignedCompact(
                                                    profit
                                            )
                                    );

                                    prices.setForeground(
                                            profit > 0
                                                    ? GREEN
                                                    : profit < 0
                                                    ? RED
                                                    : MUTED
                                    );

                                    prices.setToolTipText(
                                            String.format(
                                                    Locale.ROOT,
                                                    "Post-tax profit: %,d gp • ROI %.2f%%",
                                                    profit,
                                                    roi
                                            )
                                    );

                                    Integer volume =
                                            data.getFiveMinuteVolume();

                                    if (volume == null)
                                    {
                                        activity.setText(
                                                "Activity unavailable"
                                        );

                                        activity.setForeground(
                                                MUTED
                                        );
                                    }
                                    else
                                    {
                                        int highVolume =
                                                data.getHighVolume() == null
                                                        ? 0
                                                        : data.getHighVolume();

                                        int lowVolume =
                                                data.getLowVolume() == null
                                                        ? 0
                                                        : data.getLowVolume();

                                        int smallerSide =
                                                Math.min(
                                                        highVolume,
                                                        lowVolume
                                                );

                                        int largerSide =
                                                Math.max(
                                                        highVolume,
                                                        lowVolume
                                                );

                                        boolean oneSided =
                                                largerSide > 0
                                                        && smallerSide
                                                        < Math.max(
                                                        1,
                                                        largerSide / 5
                                                );

                                        String market;

                                        if (volume < 10
                                                || highVolume == 0
                                                || lowVolume == 0)
                                        {
                                            market = "Thin";
                                        }
                                        else if (oneSided)
                                        {
                                            market = "One-sided";
                                        }
                                        else if (volume < 50)
                                        {
                                            market = "Low";
                                        }
                                        else
                                        {
                                            market = "Active";
                                        }

                                        String freshness = null;

                                        if (data.getHighTime() != null
                                                && data.getLowTime() != null)
                                        {
                                            long now =
                                                    Instant.now()
                                                            .getEpochSecond();

                                            long oldestAge =
                                                    Math.max(
                                                            Math.max(
                                                                    0,
                                                                    now - data.getHighTime()
                                                            ),
                                                            Math.max(
                                                                    0,
                                                                    now - data.getLowTime()
                                                            )
                                                    );

                                            if (oldestAge > 900)
                                            {
                                                freshness = "Aging";
                                            }
                                            else if (oldestAge <= 300)
                                            {
                                                freshness = "Fresh";
                                            }
                                            else
                                            {
                                                freshness = "Recent";
                                            }
                                        }

                                        activity.setText(
                                                formatCompact(
                                                        volume
                                                )
                                                        + "/5m  •  "
                                                        + market
                                                        + (freshness == null
                                                        ? ""
                                                        : "  •  " + freshness)
                                        );

                                        activity.setForeground(
                                                !"Active".equals(market)
                                                        || "Aging".equals(freshness)
                                                        ? WARNING
                                                        : MUTED
                                        );

                                        activity.setToolTipText(
                                                "5-minute activity: "
                                                        + volume
                                                        + " trades • High side "
                                                        + highVolume
                                                        + " • Low side "
                                                        + lowVolume
                                        );
                                    }
                                }
                        ),
                exception ->
                        SwingUtilities.invokeLater(
                                () ->
                                {
                                    prices.setText(
                                            "Live price unavailable"
                                    );

                                    prices.setForeground(RED);

                                    activity.setText(
                                            "Try again shortly"
                                    );
                                }
                        )
        );
    }

    private void configureSearch()
    {
        searchField.setFont(BODY);
        searchField.setToolTipText("Search Grand Exchange items");

        suggestionList.setFont(BODY);
        suggestionList.setBackground(CARD_BG);
        suggestionList.setFixedCellHeight(30);
        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        suggestionList.setCellRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean selected,
                    boolean focus)
            {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list,
                        value,
                        index,
                        selected,
                        focus
                );

                label.setFont(BODY);

                if (value instanceof ItemPrice)
                {
                    label.setText(((ItemPrice) value).getName());
                }

                label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

                return label;
            }
        });

        searchField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent event)
            {
                refreshSuggestions();
            }

            @Override
            public void removeUpdate(DocumentEvent event)
            {
                refreshSuggestions();
            }

            @Override
            public void changedUpdate(DocumentEvent event)
            {
                refreshSuggestions();
            }
        });

        searchField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent event)
            {
                handleSearchKey(event);
            }
        });

        suggestionList.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent event)
            {
                int index = suggestionList.locationToIndex(event.getPoint());

                if (index >= 0)
                {
                    suggestionList.setSelectedIndex(index);
                    selectSuggestion();
                }
            }
        });
    }

    private void refreshSuggestions()
    {
        if (suppressSuggestions)
        {
            return;
        }

        String query = searchField.getText().trim();

        suggestionModel.clear();

        if (query.length() < 2)
        {
            hideSuggestions();
            return;
        }

        List<ItemPrice> results = new ArrayList<>(itemManager.search(query));

        String lower = query.toLowerCase(Locale.ROOT);

        results.sort((first, second) ->
        {
            int score = Integer.compare(
                    scoreName(first.getName(), lower),
                    scoreName(second.getName(), lower)
            );

            if (score != 0)
            {
                return score;
            }

            return first.getName().compareToIgnoreCase(second.getName());
        });

        int count = Math.min(7, results.size());

        for (int i = 0; i < count; i++)
        {
            ItemPrice item = results.get(i);
            suggestionModel.addElement(item);
            itemNameCache.put(item.getId(), item.getName());
        }

        if (suggestionModel.isEmpty())
        {
            hideSuggestions();
            return;
        }

        suggestionList.setSelectedIndex(0);

        int height = count * 30;

        suggestions.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        suggestions.setPreferredSize(new Dimension(0, height));
        suggestions.setVisible(true);

        revalidate();
        repaint();
    }

    private int scoreName(String name, String query)
    {
        String lower = name.toLowerCase(Locale.ROOT);

        if (lower.equals(query))
        {
            return 0;
        }

        if (lower.startsWith(query))
        {
            return 1;
        }

        if (lower.contains(query))
        {
            return 2;
        }

        return 3;
    }

    private void handleSearchKey(KeyEvent event)
    {
        if (event.getKeyCode() == KeyEvent.VK_ESCAPE)
        {
            hideSuggestions();
            event.consume();
            return;
        }

        if (event.getKeyCode() == KeyEvent.VK_ENTER)
        {
            if (!suggestionModel.isEmpty())
            {
                selectSuggestion();
            }

            event.consume();
            return;
        }

        int index = suggestionList.getSelectedIndex();

        if (event.getKeyCode() == KeyEvent.VK_DOWN)
        {
            suggestionList.setSelectedIndex(
                    Math.min(
                            suggestionModel.size() - 1,
                            Math.max(0, index + 1)
                    )
            );

            event.consume();
        }

        if (event.getKeyCode() == KeyEvent.VK_UP)
        {
            suggestionList.setSelectedIndex(
                    Math.max(0, index - 1)
            );

            event.consume();
        }
    }

    private void selectSuggestion()
    {
        ItemPrice selected = suggestionList.getSelectedValue();

        if (selected == null)
        {
            return;
        }

        itemNameCache.put(selected.getId(), selected.getName());

        openItem(
                selected.getId(),
                selected.getName()
        );
    }

    private void openItem(int itemId, String itemName)
    {
        if (itemName != null
                && !itemName.trim().isEmpty()
                && !itemName.startsWith("Item #"))
        {
            itemNameCache.put(itemId, itemName);
        }

        suppressSuggestions = true;
        searchField.setText(itemName);
        suppressSuggestions = false;

        hideSuggestions();

        lookupPanel.setItem(itemId, itemName);

        showTab(TAB_LOOKUP);
    }

    private void hideSuggestions()
    {
        suggestions.setVisible(false);
        revalidate();
        repaint();
    }

    private void addPageTitle(JPanel page, String text)
    {
        JLabel title = new JLabel(text);
        title.setForeground(GOLD);
        title.setFont(PAGE_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        page.add(title);
        page.add(Box.createVerticalStrut(9));
    }

    private void addSectionHeading(JPanel page, String text)
    {
        JLabel heading = new JLabel(text);
        heading.setForeground(GOLD);
        heading.setFont(SMALL_BOLD);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        page.add(heading);
        page.add(Box.createVerticalStrut(5));
    }

    private JPanel createDivider()
    {
        JPanel divider = new JPanel();
        divider.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        divider.setPreferredSize(new Dimension(1, 1));
        divider.setMinimumSize(new Dimension(1, 1));
        return divider;
    }

    private JLabel linkLabel(String text, String url)
    {
        JLabel link = new JLabel("<html><u>" + text + "</u></html>");
        link.setForeground(MUTED);
        link.setFont(BODY);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        link.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent event)
            {
                LinkBrowser.browse(url);
            }
        });

        return link;
    }

    private void addWebsiteLink(JPanel page, String text, String url)
    {
        JLabel link = linkLabel(text, url);
        link.setForeground(GOLD);
        link.setFont(BOLD);
        link.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(link);
    }

    private void addDataFooter(JPanel page)
    {
        page.add(createDivider());
        page.add(Box.createVerticalStrut(7));

        JPanel sourceRow = new JPanel(new BorderLayout());
        sourceRow.setBackground(PANEL_BG);
        sourceRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sourceRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 19));

        JLabel source = linkLabel(
                "OSRS Wiki data",
                "https://oldschool.runescape.wiki/"
        );
        source.setFont(SMALL);

        JLabel live = new JLabel("● LIVE");
        live.setForeground(GREEN);
        live.setFont(SMALL_BOLD);

        sourceRow.add(source, BorderLayout.WEST);
        sourceRow.add(live, BorderLayout.EAST);

        page.add(sourceRow);
        page.add(Box.createVerticalStrut(5));

        addWebsiteLink(
                page,
                "Open on W2.gg",
                "https://w2.gg"
        );
    }

    private void showTab(String page)
    {
        ((CardLayout) pages.getLayout()).show(pages, page);

        styleTab(flipsTab, TAB_FLIPS.equals(page));
        styleTab(lookupTab, TAB_LOOKUP.equals(page));
        styleTab(watchTab, TAB_WATCH.equals(page));
        styleTab(tradesTab, TAB_TRADES.equals(page));

        if (TAB_WATCH.equals(page))
        {
            refreshWatchlist();
        }

        if (TAB_TRADES.equals(page))
        {
            refreshTrades();
        }

        revalidate();
        repaint();
    }

    private void styleTab(JButton tab, boolean selected)
    {
        if (tab == null)
        {
            return;
        }

        tab.setForeground(selected ? GOLD : MUTED);
        tab.setBackground(selected ? TAB_SELECTED : TAB_BG);

        tab.setBorder(
                selected
                        ? BorderFactory.createMatteBorder(0, 0, 2, 0, GOLD)
                        : BorderFactory.createMatteBorder(
                        0,
                        0,
                        2,
                        0,
                        TAB_BG
                )
        );
    }

    private String shortenName(String name, int maxLength)
    {
        if (name == null || name.length() <= maxLength)
        {
            return name;
        }

        return name.substring(0, Math.max(1, maxLength - 1)) + "…";
    }

    private String formatCompact(long value)
    {
        boolean negative =
                value < 0;

        long absolute =
                negative
                        ? -value
                        : value;

        String formatted;

        if (absolute >= 1_000_000_000L)
        {
            formatted =
                    trim(
                            absolute
                                    / 1_000_000_000.0
                    )
                            + "b";
        }
        else if (absolute >= 1_000_000L)
        {
            formatted =
                    trim(
                            absolute
                                    / 1_000_000.0
                    )
                            + "m";
        }
        else if (absolute >= 1_000L)
        {
            formatted =
                    trim(
                            absolute
                                    / 1_000.0
                    )
                            + "k";
        }
        else
        {
            formatted =
                    Long.toString(
                            absolute
                    );
        }

        return negative
                ? "-" + formatted
                : formatted;
    }

    private String formatSignedCompact(
            long value)
    {
        if (value > 0)
        {
            return "+"
                    + formatCompact(
                    value
            );
        }

        return formatCompact(value);
    }

    private String trim(double value)
    {
        if (value == Math.floor(value))
        {
            return String.format("%.0f", value);
        }

        return String.format("%.1f", value);
    }
}
