package io.github.clintgalendez.haritone.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Settings a player may change for themselves with {@code /haritone set}. Server settings always
 * cap them: a player can lower the fall height but not raise it above the server's, and break-aware
 * planning also needs the server switch and the {@code haritone.break} permission.
 *
 * @param allowBreak plan routes that break blocks
 * @param maxFallHeight personal fall limit, or null for the server's
 * @param guidanceIntervalSeconds how often instructions repeat, or null for the server's
 */
public record PlayerPrefs(
    boolean allowBreak, Integer maxFallHeight, Integer guidanceIntervalSeconds) {

  /** Keys accepted by {@link #with(String, String, Settings)}. */
  public static final List<String> KEYS =
      List.of("allowBreak", "maxFallHeight", "guidanceInterval");

  public static PlayerPrefs defaults() {
    return new PlayerPrefs(false, null, null);
  }

  /** Thrown for an unknown key or an invalid value. The message is meant for the player. */
  public static final class InvalidPreferenceException extends Exception {
    private static final long serialVersionUID = 1L;

    InvalidPreferenceException(String message) {
      super(message);
    }
  }

  /** Canonical key spelling for {@code key} (case-insensitive), or null if unknown. */
  public static String canonicalKey(String key) {
    for (String known : KEYS) {
      if (known.equalsIgnoreCase(key)) {
        return known;
      }
    }
    return null;
  }

  /** Returns a copy with {@code key} set from text, validated against the server settings. */
  public PlayerPrefs with(String key, String value, Settings server)
      throws InvalidPreferenceException {
    String canonical = canonicalKey(key);
    if (canonical == null) {
      throw new InvalidPreferenceException(
          "Unknown setting '" + key + "'. Settings: " + String.join(", ", KEYS));
    }
    String v = value.trim();
    return switch (canonical) {
      case "allowBreak" -> new PlayerPrefs(parseBoolean(v), maxFallHeight, guidanceIntervalSeconds);
      case "maxFallHeight" ->
          new PlayerPrefs(
              allowBreak,
              parseInt(canonical, v, 0, server.maxFallHeight()),
              guidanceIntervalSeconds);
      case "guidanceInterval" ->
          new PlayerPrefs(allowBreak, maxFallHeight, parseInt(canonical, v, 1, 600));
      default -> throw new IllegalStateException(canonical);
    };
  }

  /** Returns a copy with {@code key} back at its default. */
  public PlayerPrefs reset(String key) throws InvalidPreferenceException {
    String canonical = canonicalKey(key);
    if (canonical == null) {
      throw new InvalidPreferenceException(
          "Unknown setting '" + key + "'. Settings: " + String.join(", ", KEYS));
    }
    return switch (canonical) {
      case "allowBreak" -> new PlayerPrefs(false, maxFallHeight, guidanceIntervalSeconds);
      case "maxFallHeight" -> new PlayerPrefs(allowBreak, null, guidanceIntervalSeconds);
      case "guidanceInterval" -> new PlayerPrefs(allowBreak, maxFallHeight, null);
      default -> throw new IllegalStateException(canonical);
    };
  }

  /** Effective value of each key as text, with server defaults filled in. */
  public Map<String, String> describe(Settings server) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("allowBreak", Boolean.toString(allowBreak));
    values.put(
        "maxFallHeight",
        maxFallHeight == null
            ? server.maxFallHeight() + " (server default)"
            : maxFallHeight.toString());
    values.put(
        "guidanceInterval",
        guidanceIntervalSeconds == null
            ? server.guidanceIntervalSeconds() + "s (server default)"
            : guidanceIntervalSeconds + "s");
    return values;
  }

  /** Stored form: only non-default values. */
  public Map<String, String> toMap() {
    Map<String, String> map = new LinkedHashMap<>();
    if (allowBreak) {
      map.put("allowBreak", "true");
    }
    if (maxFallHeight != null) {
      map.put("maxFallHeight", maxFallHeight.toString());
    }
    if (guidanceIntervalSeconds != null) {
      map.put("guidanceInterval", guidanceIntervalSeconds.toString());
    }
    return map;
  }

  /** Rebuilds from {@link #toMap()} output, skipping anything invalid. */
  public static PlayerPrefs fromMap(Map<String, String> map, Settings server) {
    PlayerPrefs prefs = defaults();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      try {
        prefs = prefs.with(entry.getKey(), entry.getValue(), server);
      } catch (InvalidPreferenceException e) {
        // Stored data may predate a settings change; drop what no longer validates.
      }
    }
    return prefs;
  }

  private static boolean parseBoolean(String value) throws InvalidPreferenceException {
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "true", "on", "yes" -> true;
      case "false", "off", "no" -> false;
      default ->
          throw new InvalidPreferenceException("Expected true or false but got '" + value + "'");
    };
  }

  private static int parseInt(String key, String value, int min, int max)
      throws InvalidPreferenceException {
    int parsed;
    try {
      parsed = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new InvalidPreferenceException(key + " must be a whole number but got '" + value + "'");
    }
    if (parsed < min || parsed > max) {
      throw new InvalidPreferenceException(key + " must be between " + min + " and " + max);
    }
    return parsed;
  }
}
