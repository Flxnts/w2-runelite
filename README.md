# W2

W2 is a RuneLite Grand Exchange plugin for OSRS players who flip and merch.

It provides live market information, flip discovery and automatic local trade tracking directly inside RuneLite.

## Features

- Live OSRS Wiki Grand Exchange prices
- Live flip suggestions based on current market conditions
- Post-tax profit and ROI
- Recent 5-minute market activity
- Liquidity and stale-price warnings
- Suggested trade quantities based on recent activity and GE limits
- Fast item lookup
- Local watchlist
- Automatic Grand Exchange buy and sell detection
- Open-position tracking
- Realised profit after GE tax
- Per-account local trade history

## Market data

W2 uses public Grand Exchange market data from the OSRS Wiki Prices API.

Flip suggestions use recent price and market-activity data to surface opportunities worth investigating. Grand Exchange prices and margins can change quickly, so suggested trades are not guaranteed to fill at the displayed prices.

## Trade tracking

Grand Exchange fills are detected automatically through RuneLite. W2 uses these fills to track open positions and calculate realised profit after Grand Exchange tax.

Trade history is stored locally through RuneLite configuration and is not uploaded to w2.gg.

## W2.gg

W2 also has a web version with additional Grand Exchange tools at w2.gg.

## Privacy

W2 communicates with the OSRS Wiki Prices API to retrieve public Grand Exchange market data.

W2 does not send your RuneScape account credentials or locally recorded Grand Exchange trade history to the OSRS Wiki Prices API or w2.gg.

## Licence

W2 is released under the BSD 2-Clause License.