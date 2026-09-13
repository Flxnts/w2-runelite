package gg.w2;

import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.logging.Logger;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@PluginDescriptor(
		name = "W2",
		description = "Grand Exchange flips, live OSRS Wiki prices and local trade tracking",
		tags = {"grand exchange", "ge", "flipping", "merching", "prices", "trades"}
)
public class W2Plugin extends Plugin
{
	private static final Logger LOGGER =
			Logger.getLogger(W2Plugin.class.getName());

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private W2PriceService priceService;

	@Inject
	private W2WatchlistService watchlistService;

	@Inject
	private W2BankService bankService;

	@Inject
	private W2TradeService tradeService;

	private W2Panel panel;
	private NavigationButton navigationButton;

	@Override
	protected void startUp()
	{
		tradeService.bootstrap(client.getGrandExchangeOffers());

		panel =
				new W2Panel(
						itemManager,
						priceService,
						watchlistService,
						bankService,
						tradeService
				);

		navigationButton =
				NavigationButton.builder()
						.tooltip("W2")
						.icon(createIcon())
						.priority(7)
						.panel(panel)
						.build();

		clientToolbar.addNavigation(
				navigationButton
		);

		LOGGER.fine("W2 started");
	}

	@Override
	protected void shutDown()
	{
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(
					navigationButton
			);
		}

		navigationButton = null;
		panel = null;

		LOGGER.fine("W2 stopped");
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(
			GrandExchangeOfferChanged event)
	{
		GrandExchangeOffer offer = event.getOffer();

		if (offer == null)
		{
			return;
		}

		/*
		 * RuneLite clears offers while transitioning away from a logged-in
		 * session. Ignore those EMPTY events so W2 does not mistake a hop or
		 * logout for a trade update.
		 */
		if (offer.getState() == GrandExchangeOfferState.EMPTY
				&& client.getGameState() != net.runelite.api.GameState.LOGGED_IN)
		{
			return;
		}

		tradeService.onOfferChanged(
				event.getSlot(),
				offer
		);

		if (panel != null)
		{
			panel.refreshTrades();
		}
	}

	private BufferedImage createIcon()
	{
		BufferedImage image =
				new BufferedImage(
						16,
						16,
						BufferedImage.TYPE_INT_ARGB
				);

		Graphics2D g =
				image.createGraphics();

		g.setRenderingHint(
				RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON
		);

		g.setRenderingHint(
				RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON
		);

		Color gold =
				new Color(
						205,
						173,
						92
				);

		g.setColor(
				new Color(
						25,
						25,
						25,
						240
				)
		);

		g.fillOval(
				1,
				1,
				13,
				13
		);

		g.setStroke(
				new BasicStroke(1.4f)
		);

		g.setColor(gold);

		g.drawOval(
				1,
				1,
				13,
				13
		);

		g.setFont(
				new Font(
						"SansSerif",
						Font.BOLD,
						7
				)
		);

		g.drawString(
				"w2",
				3,
				10
		);

		g.dispose();

		return image;
	}

	@Provides
	W2Config provideConfig(
			ConfigManager configManager)
	{
		return configManager.getConfig(
				W2Config.class
		);
	}
}
