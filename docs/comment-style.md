# Comment style

Keep comments brief, factual, and in the present tense. Explain responsibility,
ownership, units, or reasoning that is not clear from the code.

- **Classes:** use Javadoc above annotations to explain the class's purpose. Mention
  client/server ownership and state cleanup when relevant.
- **Fields:** document unclear units, synchronization, or lifetime. Names ending in
  `_TICKS` usually do not need another comment saying "ticks."
- **Methods:** use Javadoc for important contracts, side effects, sentinel values,
  and units. Skip obvious getters and routine interface overrides.
- **Implementation:** use `//` comments to explain constraints and workarounds.
  Keep related sentences together and wrap near 100 columns.
- **Sections:** use short labels such as `// Persistence.` in large files. Avoid
  decorative banners and labels for every method.
- **Mixins:** explain the target behavior and why the interception point matters.
- **Payloads:** state the intended direction and purpose. Document validation or
  scheduling constraints when they are not obvious from the handler.
- **Scripts and configuration:** explain paths, dependencies, and environment
  constraints. Remove unused template examples.

```java
/**
 * Tracks server ability state and synchronizes the owner's HUD.
 */
public final class ExampleAbilityState {
    // The deadline is an absolute level game tick, not a remaining duration.
    private long expiresAt;

    /**
     * Returns the remaining duration in game ticks, or zero after expiry.
     */
    public long remainingTicks(long gameTime) {
        return Math.max(0, expiresAt - gameTime);
    }
}
```

Avoid comments that repeat a method name, describe obsolete balance changes, or
leave disabled code behind. Preserve explanations of vanilla behavior, rendering
passes, cleanup, packet ordering, and other non-obvious constraints.

For new classes, prefer constants, state, initialization, public operations,
event handlers, then private helpers. Keep dependent field initialization order
intact when maintaining existing classes.
