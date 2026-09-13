package gg.w2;

import java.math.BigDecimal;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;

public class W2BankService
{
    private static final String CONFIG_GROUP = "w2";
    private static final String CONFIG_KEY = "tradingBank";

    private static final long DEFAULT_BANK =
            100_000_000L;

    private final ConfigManager configManager;

    @Inject
    public W2BankService(
            ConfigManager configManager)
    {
        this.configManager = configManager;
    }

    public long getBank()
    {
        String stored =
                configManager.getConfiguration(
                        CONFIG_GROUP,
                        CONFIG_KEY
                );

        if (stored == null
                || stored.trim().isEmpty())
        {
            return DEFAULT_BANK;
        }

        try
        {
            long bank =
                    Long.parseLong(
                            stored.trim()
                    );

            return bank > 0
                    ? bank
                    : DEFAULT_BANK;
        }
        catch (NumberFormatException ignored)
        {
            return DEFAULT_BANK;
        }
    }

    public void setBank(long bank)
    {
        if (bank < 1)
        {
            return;
        }

        configManager.setConfiguration(
                CONFIG_GROUP,
                CONFIG_KEY,
                Long.toString(bank)
        );
    }

    public long parseAmount(String input)
    {
        if (input == null)
        {
            throw new IllegalArgumentException(
                    "Enter a cash amount"
            );
        }

        String value =
                input.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace(",", "");

        if (value.isEmpty())
        {
            throw new IllegalArgumentException(
                    "Enter a cash amount"
            );
        }

        long multiplier = 1L;

        if (value.endsWith("k"))
        {
            multiplier = 1_000L;
            value =
                    value.substring(
                            0,
                            value.length() - 1
                    );
        }
        else if (value.endsWith("m"))
        {
            multiplier = 1_000_000L;
            value =
                    value.substring(
                            0,
                            value.length() - 1
                    );
        }
        else if (value.endsWith("b"))
        {
            multiplier = 1_000_000_000L;
            value =
                    value.substring(
                            0,
                            value.length() - 1
                    );
        }

        if (!value.matches("\\d+(\\.\\d+)?"))
        {
            throw new IllegalArgumentException(
                    "Use amounts like 50m or 1.2b"
            );
        }

        try
        {
            BigDecimal number =
                    new BigDecimal(value);

            BigDecimal result =
                    number.multiply(
                            BigDecimal.valueOf(
                                    multiplier
                            )
                    );

            if (result.compareTo(
                    BigDecimal.valueOf(Long.MAX_VALUE)) > 0)
            {
                throw new IllegalArgumentException(
                        "Cash amount is too large"
                );
            }

            long amount =
                    result.longValue();

            if (amount < 1)
            {
                throw new IllegalArgumentException(
                        "Enter a cash amount"
                );
            }

            return amount;
        }
        catch (NumberFormatException exception)
        {
            throw new IllegalArgumentException(
                    "Use amounts like 50m or 1.2b"
            );
        }
    }
}