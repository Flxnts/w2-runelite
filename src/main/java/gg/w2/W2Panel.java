package gg.w2;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

public class W2Panel extends PluginPanel
{
    private final JPanel content = new JPanel();

    public W2Panel()
    {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);

        addHeader();
        addSectionDivider();

        addSectionTitle("MARKET");
        addStatusRow("Price data", "Not connected yet");

        addSectionSpacing();

        addSectionTitle("QUICK LOOKUP");
        addItemSearch();

        addSectionSpacing();

        addSectionTitle("ITEM LOOKUP");
        content.add(new W2ItemLookupPanel());

        addSectionSpacing();

        JButton openWebsiteButton = new JButton("Open W2.gg");
        openWebsiteButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        openWebsiteButton.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, openWebsiteButton.getPreferredSize().height)
        );
        openWebsiteButton.addActionListener(event ->
                LinkBrowser.browse("https://w2.gg")
        );

        content.add(openWebsiteButton);

        add(content, BorderLayout.NORTH);
    }

    private void addHeader()
    {
        JLabel title = new JLabel("W2");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        JLabel subtitle = new JLabel("Grand Exchange tools");
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        content.add(title);
        content.add(Box.createVerticalStrut(2));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(10));
    }

    private void addSectionTitle(String text)
    {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));

        content.add(label);
        content.add(Box.createVerticalStrut(6));
    }

    private void addStatusRow(String labelText, String valueText)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel label = new JLabel(labelText);
        JLabel value = new JLabel(valueText);

        value.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        row.add(label, BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);

        content.add(row);
    }

    private void addItemSearch()
    {
        JTextField searchField = new JTextField();
        searchField.setToolTipText("Search for an item");
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JButton searchButton = new JButton("Search");
        searchButton.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, searchButton.getPreferredSize().height)
        );

        JLabel helper = new JLabel(
                "<html>Inspect margin, ROI, volume and realistic fill potential.</html>"
        );
        helper.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        helper.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(searchField);
        content.add(Box.createVerticalStrut(5));
        content.add(searchButton);
        content.add(Box.createVerticalStrut(6));
        content.add(helper);
    }

    private void addEmptyState(String titleText, String bodyText)
    {
        JLabel title = new JLabel(titleText);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD));

        JLabel body = new JLabel("<html>" + bodyText + "</html>");
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        content.add(title);
        content.add(Box.createVerticalStrut(3));
        content.add(body);
    }

    private void addSectionDivider()
    {
        JPanel divider = new JPanel();
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);
        divider.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        divider.setPreferredSize(new Dimension(0, 1));

        content.add(divider);
        content.add(Box.createVerticalStrut(12));
    }

    private void addSectionSpacing()
    {
        content.add(Box.createVerticalStrut(16));
    }
}