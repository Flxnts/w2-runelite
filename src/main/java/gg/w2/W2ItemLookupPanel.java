package gg.w2;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

public class W2ItemLookupPanel extends JPanel
{
    private static final Color W2_GREEN = new Color(94, 186, 125);
    private static final Color MUTED = new Color(150, 150, 150);

    public W2ItemLookupPanel()
    {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

        addItemHeader();
        add(Box.createRigidArea(new Dimension(0, 8)));
        addPriceRows();
        add(Box.createRigidArea(new Dimension(0, 8)));
        addLiquidityStatus();
    }

    private void addItemHeader()
    {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel itemName = new JLabel("Dragon boots");
        itemName.setFont(itemName.getFont().deriveFont(Font.BOLD, 14f));

        JLabel itemId = new JLabel("#11840");
        itemId.setForeground(MUTED);
        itemId.setFont(itemId.getFont().deriveFont(11f));

        header.add(itemName, BorderLayout.WEST);
        header.add(itemId, BorderLayout.EAST);

        add(header);
    }

    private void addPriceRows()
    {
        JPanel prices = new JPanel(new GridLayout(0, 2, 0, 5));
        prices.setBackground(ColorScheme.DARK_GRAY_COLOR);
        prices.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        addRow(prices, "Buy", "128,410", Color.WHITE);
        addRow(prices, "Sell", "131,880", Color.WHITE);
        addRow(prices, "Margin", "+3,470", W2_GREEN);
        addRow(prices, "ROI", "2.7%", W2_GREEN);
        addRow(prices, "Volume", "1,284", Color.WHITE);

        add(prices);
    }

    private void addRow(JPanel panel, String labelText, String valueText, Color valueColor)
    {
        JLabel label = new JLabel(labelText);
        label.setForeground(MUTED);

        JLabel value = new JLabel(valueText, JLabel.RIGHT);
        value.setForeground(valueColor);
        value.setFont(value.getFont().deriveFont(Font.BOLD));

        panel.add(label);
        panel.add(value);
    }

    private void addLiquidityStatus()
    {
        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        status.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
        status.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JLabel statusText = new JLabel("\u25CF  Healthy volume");
        statusText.setForeground(W2_GREEN);
        statusText.setFont(statusText.getFont().deriveFont(Font.BOLD, 11f));

        status.add(statusText, BorderLayout.WEST);

        add(status);
    }
}