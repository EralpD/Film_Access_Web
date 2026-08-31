(() => {
    "use strict";

    const menus = document.querySelectorAll("[data-profile-menu]");

    menus.forEach((menu) => {
        const trigger = menu.querySelector("[data-profile-menu-trigger]");
        const panel = menu.querySelector("[data-profile-menu-panel]");

        if (!trigger || !panel) {
            return;
        }

        const setOpen = (open, returnFocus = false) => {
            menu.classList.toggle("is-open", open);
            trigger.setAttribute("aria-expanded", String(open));
            panel.hidden = !open;

            if (open) {
                panel.querySelector('[role="menuitem"]')?.focus();
            } else if (returnFocus) {
                trigger.focus();
            }
        };

        trigger.addEventListener("click", () => {
            setOpen(trigger.getAttribute("aria-expanded") !== "true");
        });

        panel.addEventListener("keydown", (event) => {
            if (event.key === "Escape") {
                event.preventDefault();
                setOpen(false, true);
            }
        });

        document.addEventListener("pointerdown", (event) => {
            if (!menu.contains(event.target)) {
                setOpen(false);
            }
        });
    });
})();
