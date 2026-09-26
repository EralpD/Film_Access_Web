(() => {
            const sortForm =
                document.querySelector("[data-archive-sort-form]");

            const sortSelect =
                document.querySelector("[data-archive-sort-select]");

            if (sortForm && sortSelect) {
                sortSelect.addEventListener("change", () => {
                    sortForm.setAttribute("aria-busy", "true");
                    sortForm.requestSubmit();
                });
            }

            const dialog =
                document.getElementById("archive-remove-dialog");

            const message =
                document.getElementById("archive-remove-message");

            const confirmButton =
                document.getElementById("archive-remove-confirm");

            if (
                !dialog ||
                !message ||
                !confirmButton ||
                typeof dialog.showModal !== "function"
            ) {
                return;
            }

            let pendingForm = null;

            document
                .querySelectorAll(".archive-remove-form")
                .forEach((form) => {
                    form.addEventListener("submit", (event) => {
                        if (form.dataset.confirmed === "true") {
                            return;
                        }

                        event.preventDefault();

                        pendingForm = form;

                        const filmTitle =
                            form.dataset.filmTitle?.trim() ||
                            "this film";

                        message.textContent =
                            `Do you want to remove ${filmTitle} from your archive?`;

                        dialog.showModal();
                    });
                });

            confirmButton.addEventListener("click", () => {
                if (!pendingForm) {
                    return;
                }

                const formToSubmit = pendingForm;

                pendingForm = null;
                formToSubmit.dataset.confirmed = "true";

                dialog.close();
                formToSubmit.requestSubmit();
            });

            dialog.addEventListener("close", () => {
                pendingForm = null;
            });
        })();
