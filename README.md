# W2

W2 is a RuneLite Grand Exchange plugin for OSRS players who flip and merch.

It focuses on useful live market information without turning the sidebar into a dashboard.

## Features

- Live OSRS Wiki Grand Exchange prices
- Flip suggestions based on your available bank
- Post-tax profit and ROI
- Recent 5-minute market activity
- Liquidity and stale-price warnings
- Fast item lookup and autocomplete
- Local watchlist
- Automatic Grand Exchange trade detection
- Open-position tracking
- Realised profit after GE tax
- Per-account local trade history

## Market data

W2 requests public Grand Exchange price and volume data from the OSRS Wiki Prices API.

Trade history, watchlists and W2 settings are stored locally through RuneLite configuration. W2 does not upload your Grand Exchange trade history to w2.gg.

## W2.gg

More Grand Exchange tools are available at:

https://w2.gg

## Privacy

W2 communicates with the OSRS Wiki Prices API to request public item market data.

The requests contain item/market lookup information required to retrieve prices. W2 does not send your RuneScape account credentials or locally recorded Grand Exchange trade history to the OSRS Wiki API or w2.gg.

## Development

Build:

```powershell
.\gradlew.bat clean build
