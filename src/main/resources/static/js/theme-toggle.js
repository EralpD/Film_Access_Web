(() => {
    "use strict";

    const STORAGE_KEY = "film-archive-theme";
    const THEME_EVENT = "filmarchive:themechange";

    const root = document.documentElement;
    const systemThemeQuery =
        window.matchMedia(
            "(prefers-color-scheme: light)"
        );

    const themeColorMeta =
        document.querySelector(
            "[data-theme-color]"
        );

    const toggleButtons =
        document.querySelectorAll(
            "[data-theme-toggle]"
        );

    function readStoredTheme() {
        try {
            const theme =
                window.localStorage.getItem(
                    STORAGE_KEY
                );

            return theme === "light" ||
                   theme === "dark"
                ? theme
                : null;
        } catch {
            return null;
        }
    }

    function storeTheme(theme) {
        try {
            window.localStorage.setItem(
                STORAGE_KEY,
                theme
            );
        } catch {
            // Tema mevcut sayfada yine çalışmaya devam eder.
        }
    }

    function updateControls(theme) {
        const isLight = theme === "light";

        const actionText =
            isLight
                ? "Switch to night mode"
                : "Switch to day mode";

        toggleButtons.forEach((button) => {
            button.setAttribute(
                "aria-checked",
                String(isLight)
            );

            button.setAttribute(
                "aria-label",
                actionText
            );

            button.title = actionText;
        });
    }

    function updateThemeColor(theme) {
        if (!themeColorMeta) {
            return;
        }

        themeColorMeta.content =
            theme === "light"
                ? "#f5f2ea"
                : "#0b0d11";
    }

    function applyTheme(
        theme,
        {
            persist = false,
            emit = true
        } = {}
    ) {
        const normalizedTheme =
            theme === "light"
                ? "light"
                : "dark";

        root.dataset.theme =
            normalizedTheme;

        root.style.colorScheme =
            normalizedTheme;

        if (persist) {
            storeTheme(normalizedTheme);
        }

        updateControls(normalizedTheme);
        updateThemeColor(normalizedTheme);

        if (emit) {
            window.dispatchEvent(
                new CustomEvent(
                    THEME_EVENT,
                    {
                        detail: {
                            theme: normalizedTheme
                        }
                    }
                )
            );
        }
    }

    const initialTheme =
        root.dataset.theme === "light"
            ? "light"
            : "dark";

    applyTheme(
        initialTheme,
        {
            emit: false
        }
    );

    toggleButtons.forEach((button) => {
        button.addEventListener(
            "click",
            () => {
                const nextTheme =
                    root.dataset.theme === "light"
                        ? "dark"
                        : "light";

                applyTheme(
                    nextTheme,
                    {
                        persist: true
                    }
                );
            }
        );
    });

    systemThemeQuery.addEventListener(
        "change",
        (event) => {
            /*
             * Kullanıcı elle seçim yapmadıysa
             * işletim sistemi tercihini takip eder.
             */
            if (readStoredTheme() !== null) {
                return;
            }

            applyTheme(
                event.matches
                    ? "light"
                    : "dark"
            );
        }
    );

    /*
     * Başka bir sekmede değiştirilen temayı da uygular.
     */
    window.addEventListener(
        "storage",
        (event) => {
            if (event.key !== STORAGE_KEY) {
                return;
            }

            const theme =
                event.newValue === "light" ||
                event.newValue === "dark"
                    ? event.newValue
                    : systemThemeQuery.matches
                        ? "light"
                        : "dark";

            applyTheme(
                theme,
                {
                    emit: true
                }
            );
        }
    );
})();