package gg.w2;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;

public class W2WatchlistService
{
    private static final String CONFIG_GROUP = "w2";
    private static final String CONFIG_KEY = "watchlist";

    private final ConfigManager configManager;

    private final Set<Integer> itemIds =
            new LinkedHashSet<>();

    @Inject
    public W2WatchlistService(
            ConfigManager configManager)
    {
        this.configManager = configManager;

        load();
    }

    public boolean contains(
            int itemId)
    {
        return itemIds.contains(
                itemId
        );
    }

    public void add(
            int itemId)
    {
        if (itemIds.add(itemId))
        {
            save();
        }
    }

    public void remove(
            int itemId)
    {
        if (itemIds.remove(itemId))
        {
            save();
        }
    }

    public List<Integer> getAll()
    {
        return new ArrayList<>(
                itemIds
        );
    }

    private void load()
    {
        itemIds.clear();

        String stored =
                configManager.getConfiguration(
                        CONFIG_GROUP,
                        CONFIG_KEY
                );

        if (stored == null
                || stored.trim().isEmpty())
        {
            return;
        }

        String[] parts =
                stored.split(",");

        for (String part : parts)
        {
            try
            {
                int itemId =
                        Integer.parseInt(
                                part.trim()
                        );

                itemIds.add(itemId);
            }
            catch (NumberFormatException ignored)
            {
            }
        }
    }

    private void save()
    {
        StringBuilder value =
                new StringBuilder();

        for (Integer itemId : itemIds)
        {
            if (value.length() > 0)
            {
                value.append(',');
            }

            value.append(itemId);
        }

        configManager.setConfiguration(
                CONFIG_GROUP,
                CONFIG_KEY,
                value.toString()
        );
    }
}