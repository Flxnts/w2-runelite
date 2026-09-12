package gg.w2;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
		name = "W2",
		description = "Grand Exchange tools for Old School RuneScape"
)
public class W2Plugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;

	private W2Panel panel;
	private NavigationButton navigationButton;

	@Override
	protected void startUp()
	{
		panel = new W2Panel();

		navigationButton = NavigationButton.builder()
				.tooltip("W2")
				.icon(createTemporaryIcon())
				.priority(7)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navigationButton);

		log.debug("W2 started");
	}

	@Override
	protected void shutDown()
	{
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
		}

		navigationButton = null;
		panel = null;

		log.debug("W2 stopped");
	}

	private BufferedImage createTemporaryIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();

		graphics.setRenderingHint(
				RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON
		);

		graphics.setColor(new Color(220, 220, 220));
		graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
		graphics.drawString("W2", 1, 11);
		graphics.dispose();

		return image;
	}

	@Provides
	W2Config provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(W2Config.class);
	}
}