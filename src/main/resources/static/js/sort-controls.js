document.querySelectorAll("select[data-auto-submit]").forEach((select) => {
    select.addEventListener("change", () => {
        select.form?.requestSubmit();
    });
});
