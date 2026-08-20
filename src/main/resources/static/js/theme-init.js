(() => {
    "use strict";

    const STORAGE_KEY = "film-archive-theme";
    const root = document.documentElement;

    let storedTheme = null;

    try {
        storedTheme = window.localStorage.getItem(STORAGE_KEY);
    } catch {
        // localStorage engelliyse sistem tercihi kullanılacak.
    }

    const validStoredTheme =
        storedTheme === "light" ||
        storedTheme === "dark";

    const prefersLight =
        window.matchMedia(
            "(prefers-color-scheme: light)"
        ).matches;

    const theme =
        validStoredTheme
            ? storedTheme
            : prefersLight
                ? "light"
                : "dark";

    root.dataset.theme = theme;
    root.style.colorScheme = theme;
})();