# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows [Semantic Versioning](https://semver.org/).

## [1.0.0] - Unreleased

First release, as a Paper plugin and as a Fabric mod sharing the same core.

### Added
- Comic-style thought bubbles for tamed wolves, cats, parrots, horses, donkeys, mules and llamas, built from vanilla Text Displays: rounded pixel-art frame with outline, a colour per kind of thought (configurable), thought dots or a speech pointer, pop-in / float / shake animation. Text is measured with the real font widths so the frame always fits.
- Five personalities (Loyal, Drama Queen, Grumpy, Philosopher, Glutton), assigned when a pet is tamed or first seen and stored on the pet.
- 45+ triggers: danger and health, food, weather, dimensions, time of day, things the owner does (eating, fishing, finding diamonds, advancements, dying...), things the pet does (being tamed, fed, hurt, teleporting, having a baby...).
- Petting: look at your pet and press F (swap hands), or sneak + right click with an empty hand. Hearts and a happy thought.
- Species-specific lines mixed in with the personality lines.
- Anti-spam: global interval per pet, cooldown per trigger, pace per player, group-reaction limit, back-off for persistent dangers, no repeated lines, hard cap of bubbles per player.
- `visibility: all | owner`, `/petthoughts toggle` and `/petthoughts mute`.
- Commands `info`, `toggle`, `mute`, `set`, `test`, `reload` (alias `/pt`).
- English and Spanish language files (900+ lines each), fully editable, with fallback to the bundled text.
- Folia support (plugin).
