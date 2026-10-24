# Home 3.0 design review

## Direction and tokens

Preserve the existing red/charcoal Home identity, panoramic Cinemana artwork and Outfit typography. Use a calm full-screen illustrated river at idle. Make focus, title identity and navigation clearer before adding decoration.

| Token | Value / role |
| --- | --- |
| Home atmosphere | Existing `#491620 → #261018 → #0B0B10` gradient |
| Foreground | `#F5F4EF` |
| Secondary text | `#BAC4C5` on dark surfaces |
| Focus | `#9CE8D5`, 2dp outline over `#173D3C` |
| Typeface | Outfit; explicit 450–600 weights, clock 350 |
| Spacing | 48dp safe horizontal margin; 18–22dp shelf/section gaps |
| Targets | Remote-focusable controls approximately 48dp high or larger |
| Motion | Short focus response; 900ms ambient entrance; sparse text/cloud drift |

The home screen is a TV content browser, not a marketing landing page. Collections remain appropriate interaction surfaces. The ambient display uses one landscape plane, one dominant clock, and supporting weather/football/travel information without floating cards.

## Findings addressed

- **High: navigation and focus.** A real All apps destination includes sideloaded apps; Settings separates launcher choices from Android settings. Dynamic rows reuse their views/adapters, keeping identity and focus instead of rebuilding the entire home collection.
- **High: connection feedback.** HDMI switching now has an explicit selected input, connection status, retry and escape actions. Actual tuning behavior remains a hardware verification item.
- **High: selected-control contrast.** Native render review caught mint text on the inherited white input-row focus state. New controls now use a dark green surface, mint outline and readable text.
- **Medium: typography.** Programmatic text initially inherited an overly thin variable-font default. Explicit font weights improve viewing from a sofa. Title, rating and genre remain on the same line for featured banners, with bounded title width.
- **Medium: layout resilience.** The header reserves space for the clock and bounds network labels. Automated renders check clock/header separation and ambient vertical overflow.
- **Medium: freshness.** Weather, fixtures and routing show retrieval times or unavailable states. No fake scores, weather readings or ETA are used as an offline fallback.
- **Medium: idle behavior and motion.** Playback guards prevent automatic activation during media or unknown access. The timer suspends while Home loses focus. Motion is sparse and can be reduced.

## Design references applied

The local `apple-design` reference set was used as a cross-platform review lens, while keeping Android TV navigation conventions. Reviewed topics: accessibility, layout, typography, color, focus and selection, collections, settings, motion and loading.

- `references/hig/layout.md` → **Best practices**: “Make essential information easy to find by giving it sufficient space.” Applied through safe margins, a dominant clock and separation between navigation and content.
- `references/hig/typography.md` → **Ensuring legibility**: “Use font sizes that most people can read easily.” Applied through explicit readable weights and living-room-sized type.
- `references/hig/accessibility.md` → **Vision**: contrast and non-color focus cues informed the outlined control state; reduced motion also respects Android's animation setting.

## Validation boundary

Native Robolectric previews are generated from actual Android layouts and Canvas rendering at a 960×540dp TV viewport. Their fixture names, scores, weather and travel values are synthetic. They validate layout and rendering, not the live provider data shown on the owner's television.

Hardware follow-up must check overscan, Arabic titles, actual remote focus/repeats, HDMI callbacks, Cinemana accessibility navigation, Stremio links, standby recovery and system screensaver behavior. The local review does not certify those device-specific interactions.
