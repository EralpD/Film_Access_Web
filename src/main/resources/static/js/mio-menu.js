const PRESET_GROUPS = [
    {
        id: "mood",
        label: "Mood",
        items: [
            {
                label: "Clear my mind",
                prompt:
                    "I have a lot on my mind; recommend a light, clever "
                    + "film that will leave me feeling good.",
                tone: "cyan"
            },
            {
                label: "Warm my heart",
                prompt:
                    "I want a warm, hopeful film or series "
                    + "with characters I can connect with.",
                tone: "rose"
            },
            {
                label: "Dark and intense",
                prompt:
                    "I want a gripping psychological film "
                    + "with a dark atmosphere.",
                tone: "violet"
            },
            {
                label: "Family time",
                prompt:
                    "Recommend something warm and fun we can watch "
                    + "as a family, suitable for all ages.",
                tone: "blue"
            }
        ]
    },
    {
        id: "pace",
        label: "Pace",
        items: [
            {
                label: "90-minute escape",
                prompt:
                    "I want an engaging film that gets going quickly "
                    + "and runs no longer than 100 minutes.",
                tone: "cyan"
            },
            {
                label: "Slow and atmospheric",
                prompt:
                    "I want a slow, mysterious film or miniseries "
                    + "with a strong atmosphere.",
                tone: "violet"
            },
            {
                label: "Nonstop thrills",
                prompt:
                    "I want something full of suspense or action "
                    + "that never slows down.",
                tone: "rose"
            },
            {
                label: "Weekend marathon",
                prompt:
                    "Recommend a series I can finish over a weekend, "
                    + "with engaging episodes that keep me curious.",
                tone: "blue"
            }
        ]
    },
    {
        id: "discover",
        label: "Discover",
        items: [
            {
                label: "Hidden gem",
                prompt:
                    "Recommend a hidden gem that is not widely known "
                    + "but is loved by critics and audiences.",
                tone: "cyan"
            },
            {
                label: "Unexpected twists",
                prompt:
                    "I want a cleverly crafted film or series full "
                    + "of surprises and an unexpected ending.",
                tone: "violet"
            },
            {
                label: "Visual feast",
                prompt:
                    "Recommend something with stunning cinematography, "
                    + "art direction and atmosphere.",
                tone: "rose"
            },
            {
                label: "A new series",
                prompt:
                    "Recommend a recent series I can easily get into "
                    + "that could become a new favorite.",
                tone: "blue"
            }
        ]
    }
];

const SERVER_STATES = new Set([
    "idle",
    "results",
    "waiting",
    "empty",
    "failed"
]);

const TRANSIENT_STATES = new Set([
    "opening",
    "selected",
    "running",
    "review"
]);

const OPEN_DURATION = 400;
const CLOSE_DURATION = 220;
const PANEL_EASING = "cubic-bezier(0.05, 0.7, 0.1, 1)";
const CLOSE_EASING = "cubic-bezier(0.3, 0, 0.8, 0.15)";

const menu =
    document.getElementById("mio-menu");

const canvas =
    document.getElementById("mio-canvas");

const form =
    document.getElementById("mio-form");

const promptInput =
    document.getElementById("mio-prompt");

const submitButton =
    form?.querySelector('[type="submit"]');

const status =
    document.getElementById("mio-status");

const summary =
    menu?.querySelector(".mio-menu__launcher");

const panel =
    menu?.querySelector(".mio-menu__panel");

const reducedMotion =
    window.matchMedia(
        "(prefers-reduced-motion: reduce)"
    );

let mioSignal = null;
let transitionRevision = 0;
let selectedPrompt = "";
let activeOpeningAnimations = [];

class MioSignal {

    constructor(targetCanvas) {
        this.canvas = targetCanvas;
        this.context =
            targetCanvas.getContext("2d");

        if (!this.context) {
            throw new Error(
                "Canvas 2D context is unavailable."
            );
        }

        this.state = "idle";
        this.stateStartedAt = performance.now();
        this.frameId = null;
        this.timerId = null;
        this.logicalWidth = 58;
        this.logicalHeight = 58;
        this.pixelRatio = 1;

        this.pointer = {
            x: 0,
            y: 0,
            targetX: 0,
            targetY: 0
        };

        this.tick = this.tick.bind(this);
        this.handleMotionPreference =
            this.handleMotionPreference.bind(this);
        this.handleVisibility =
            this.handleVisibility.bind(this);
    }

    mount() {
        this.resizeObserver =
            new ResizeObserver(() => {
                this.resize();
                this.draw(performance.now());
            });

        this.resizeObserver.observe(this.canvas);

        reducedMotion.addEventListener(
            "change",
            this.handleMotionPreference
        );

        document.addEventListener(
            "visibilitychange",
            this.handleVisibility
        );

        this.resize();
        this.draw(performance.now());
        this.ensureLoop();
    }

    resize() {
        const bounds =
            this.canvas.getBoundingClientRect();

        this.logicalWidth =
            Math.max(1, bounds.width || 58);

        this.logicalHeight =
            Math.max(1, bounds.height || 58);

        this.pixelRatio =
            Math.min(
                window.devicePixelRatio || 1,
                2
            );

        this.canvas.width =
            Math.max(
                1,
                Math.round(
                    this.logicalWidth
                    * this.pixelRatio
                )
            );

        this.canvas.height =
            Math.max(
                1,
                Math.round(
                    this.logicalHeight
                    * this.pixelRatio
                )
            );
    }

    setState(state) {
        if (
            !SERVER_STATES.has(state)
            && !TRANSIENT_STATES.has(state)
        ) {
            state = "idle";
        }

        this.cancelLoop();
        this.state = state;
        this.stateStartedAt = performance.now();
        this.draw(this.stateStartedAt);
        this.ensureLoop();
    }

    lookAt(clientX, clientY) {
        const bounds =
            this.canvas.getBoundingClientRect();

        const dx =
            (
                clientX
                - bounds.left
                - bounds.width / 2
            )
            / Math.max(bounds.width, 1);

        const dy =
            (
                clientY
                - bounds.top
                - bounds.height / 2
            )
            / Math.max(bounds.height, 1);

        this.pointer.targetX =
            clamp(dx * 2.8, -1.35, 1.35);

        this.pointer.targetY =
            clamp(dy * 2.8, -1.15, 1.15);
    }

    resetLook() {
        this.pointer.targetX = 0;
        this.pointer.targetY = 0;
    }

    handleMotionPreference() {
        if (reducedMotion.matches) {
            this.cancelLoop();
            this.pointer.x = 0;
            this.pointer.y = 0;
            this.draw(performance.now());
            return;
        }

        this.ensureLoop();
    }

    handleVisibility() {
        if (document.hidden) {
            this.cancelLoop();
            return;
        }

        this.stateStartedAt =
            performance.now();

        this.ensureLoop();
    }

    ensureLoop() {
        if (
            reducedMotion.matches
            || document.hidden
            || this.frameId !== null
            || this.timerId !== null
        ) {
            return;
        }

        const isQuietLauncher =
            !menu?.open
            && (
                this.state === "idle"
                || this.state === "review"
            );

        if (isQuietLauncher) {
            this.timerId =
                window.setTimeout(
                    () => {
                        this.timerId = null;
                        this.frameId =
                            requestAnimationFrame(
                                this.tick
                            );
                    },
                    50
                );
            return;
        }

        this.frameId =
            requestAnimationFrame(this.tick);
    }

    cancelLoop() {
        if (this.frameId !== null) {
            cancelAnimationFrame(
                this.frameId
            );
            this.frameId = null;
        }

        if (this.timerId !== null) {
            window.clearTimeout(
                this.timerId
            );
            this.timerId = null;
        }
    }

    tick(timestamp) {
        this.frameId = null;

        this.pointer.x +=
            (
                this.pointer.targetX
                - this.pointer.x
            )
            * 0.08;

        this.pointer.y +=
            (
                this.pointer.targetY
                - this.pointer.y
            )
            * 0.08;

        const elapsed =
            timestamp - this.stateStartedAt;

        if (
            this.state === "opening"
            && elapsed > 720
        ) {
            this.setState("idle");
            return;
        }

        if (
            this.state === "selected"
            && elapsed > 620
        ) {
            this.setState("idle");
            return;
        }

        if (
            this.state === "results"
            && elapsed > 1050
        ) {
            this.setState("review");
            return;
        }

        if (
            this.state === "failed"
            && elapsed > 900
        ) {
            this.setState("idle");
            return;
        }

        this.draw(timestamp);
        this.ensureLoop();
    }

    draw(timestamp) {
        const context = this.context;
        const width = this.logicalWidth;
        const height = this.logicalHeight;
        const elapsed =
            timestamp - this.stateStartedAt;
        const time = timestamp / 1000;

        context.setTransform(
            this.pixelRatio,
            0,
            0,
            this.pixelRatio,
            0,
            0
        );

        context.clearRect(
            0,
            0,
            width,
            height
        );

        const isReduced =
            reducedMotion.matches;

        const isOpening =
            this.state === "opening";

        const openingProgress =
            isOpening
                ? easeOutCubic(
                    clamp(elapsed / 520, 0, 1)
                )
                : 1;

        const floatOffset =
            isReduced
                ? 0
                : Math.sin(time * 1.9) * 0.75;

        const bodyScale =
            isOpening
                ? 0.88 + 0.12 * openingProgress
                : 1;

        const centerX = width / 2;
        const centerY =
            height / 2 + 1.5 + floatOffset;

        context.save();
        context.translate(centerX, centerY);
        context.scale(bodyScale, bodyScale);
        context.translate(-centerX, -centerY);

        this.drawAura(
            context,
            centerX,
            centerY,
            time,
            openingProgress,
            isReduced
        );

        this.drawAntennae(
            context,
            centerX,
            centerY,
            isOpening,
            openingProgress
        );

        const body = {
            x: width * 0.11,
            y: height * 0.2,
            width: width * 0.78,
            height: height * 0.65
        };

        this.drawTelevision(
            context,
            body,
            timestamp,
            elapsed,
            openingProgress,
            isReduced
        );

        context.restore();
    }

    drawAura(
        context,
        centerX,
        centerY,
        time,
        openingProgress,
        isReduced
    ) {
        const pulse =
            isReduced
                ? 0.82
                : 0.76
                    + Math.sin(time * 2.25) * 0.08;

        const aura =
            context.createRadialGradient(
                centerX,
                centerY,
                2,
                centerX,
                centerY,
                this.logicalWidth * 0.47
            );

        aura.addColorStop(
            0,
            "rgba(139, 92, 246, "
            + (0.2 * pulse * openingProgress)
            + ")"
        );

        aura.addColorStop(
            0.56,
            "rgba(91, 140, 255, "
            + (0.1 * pulse)
            + ")"
        );

        aura.addColorStop(
            1,
            "rgba(45, 212, 191, 0)"
        );

        context.fillStyle = aura;
        context.beginPath();
        context.arc(
            centerX,
            centerY,
            this.logicalWidth * 0.47,
            0,
            Math.PI * 2
        );
        context.fill();
    }

    drawAntennae(
        context,
        centerX,
        centerY,
        isOpening,
        openingProgress
    ) {
        const bounce =
            isOpening
                ? Math.sin(
                    openingProgress * Math.PI
                ) * 2.2
                : 0;

        const baseY =
            centerY
            - this.logicalHeight * 0.28;

        context.save();
        context.lineCap = "round";
        context.lineWidth = 2;
        context.strokeStyle =
            "rgba(167, 139, 250, 0.78)";

        context.beginPath();
        context.moveTo(
            centerX - 3,
            baseY + 2
        );
        context.lineTo(
            centerX - 10 - bounce,
            baseY - 7 - bounce
        );
        context.moveTo(
            centerX + 3,
            baseY + 2
        );
        context.lineTo(
            centerX + 10 + bounce,
            baseY - 7 - bounce
        );
        context.stroke();

        context.fillStyle =
            "rgba(45, 212, 191, 0.9)";

        for (const direction of [-1, 1]) {
            context.beginPath();
            context.arc(
                centerX
                    + direction * (10 + bounce),
                baseY - 7 - bounce,
                1.7,
                0,
                Math.PI * 2
            );
            context.fill();
        }

        context.restore();
    }

    drawTelevision(
        context,
        body,
        timestamp,
        elapsed,
        openingProgress,
        isReduced
    ) {
        const shellGradient =
            context.createLinearGradient(
                body.x,
                body.y,
                body.x + body.width,
                body.y + body.height
            );

        shellGradient.addColorStop(
            0,
            "#342755"
        );
        shellGradient.addColorStop(
            0.52,
            "#211b3c"
        );
        shellGradient.addColorStop(
            1,
            "#131726"
        );

        roundedRectPath(
            context,
            body.x,
            body.y,
            body.width,
            body.height,
            body.width * 0.19
        );

        context.fillStyle = shellGradient;
        context.shadowColor =
            "rgba(15, 8, 43, 0.38)";
        context.shadowBlur = 9;
        context.shadowOffsetY = 5;
        context.fill();

        context.shadowColor = "transparent";
        context.lineWidth = 1.2;
        context.strokeStyle =
            "rgba(205, 194, 255, 0.48)";
        context.stroke();

        const screen = {
            x: body.x + body.width * 0.095,
            y: body.y + body.height * 0.105,
            width: body.width * 0.81,
            height: body.height * 0.68
        };

        context.save();
        roundedRectPath(
            context,
            screen.x,
            screen.y,
            screen.width,
            screen.height,
            screen.width * 0.16
        );
        context.clip();

        const screenGradient =
            context.createLinearGradient(
                screen.x,
                screen.y,
                screen.x + screen.width,
                screen.y + screen.height
            );

        if (this.state === "failed") {
            screenGradient.addColorStop(
                0,
                "#3a1939"
            );
            screenGradient.addColorStop(
                1,
                "#261425"
            );
        } else {
            screenGradient.addColorStop(
                0,
                "#17264a"
            );
            screenGradient.addColorStop(
                0.5,
                "#201940"
            );
            screenGradient.addColorStop(
                1,
                "#102d36"
            );
        }

        context.fillStyle = screenGradient;
        context.fillRect(
            screen.x,
            screen.y,
            screen.width,
            screen.height
        );

        context.globalAlpha =
            0.13 * openingProgress;
        context.strokeStyle = "#8bb6ff";
        context.lineWidth = 0.6;

        for (let row = 1; row < 4; row += 1) {
            const y =
                screen.y
                + screen.height * row / 4;

            context.beginPath();
            context.moveTo(screen.x, y);
            context.lineTo(
                screen.x + screen.width,
                y
            );
            context.stroke();
        }

        context.globalAlpha = 1;

        if (!isReduced) {
            const scan =
                (
                    timestamp / 18
                )
                % (
                    screen.height
                    + 12
                )
                - 6;

            const scanGradient =
                context.createLinearGradient(
                    0,
                    screen.y + scan - 5,
                    0,
                    screen.y + scan + 5
                );

            scanGradient.addColorStop(
                0,
                "rgba(139, 92, 246, 0)"
            );
            scanGradient.addColorStop(
                0.5,
                "rgba(91, 140, 255, 0.1)"
            );
            scanGradient.addColorStop(
                1,
                "rgba(45, 212, 191, 0)"
            );

            context.fillStyle = scanGradient;
            context.fillRect(
                screen.x,
                screen.y + scan - 5,
                screen.width,
                10
            );
        }

        if (this.state === "opening") {
            const revealHeight =
                Math.max(
                    1.5,
                    screen.height
                    * openingProgress
                );

            context.fillStyle =
                "rgba(8, 8, 20, "
                + (1 - openingProgress)
                + ")";

            context.fillRect(
                screen.x,
                screen.y,
                screen.width,
                (
                    screen.height
                    - revealHeight
                )
                / 2
            );

            context.fillRect(
                screen.x,
                screen.y
                    + (
                        screen.height
                        + revealHeight
                    )
                    / 2,
                screen.width,
                (
                    screen.height
                    - revealHeight
                )
                / 2
            );
        }

        const sparkleX =
            screen.x
            + screen.width / 2
            + this.pointer.x;

        const sparkleY =
            screen.y
            + screen.height / 2
            + this.pointer.y;

        this.drawSparkle(
            context,
            sparkleX,
            sparkleY,
            Math.min(
                screen.width,
                screen.height
            )
            * 0.34,
            timestamp,
            elapsed,
            openingProgress,
            isReduced
        );

        if (
            this.state === "running"
            || this.state === "waiting"
            || this.state === "empty"
            || this.state === "selected"
        ) {
            this.drawSignalOrbit(
                context,
                sparkleX,
                sparkleY,
                screen.width * 0.31,
                timestamp
            );
        }

        if (
            this.state === "results"
            && !isReduced
        ) {
            this.drawParticles(
                context,
                sparkleX,
                sparkleY,
                elapsed
            );
        }

        context.restore();

        roundedRectPath(
            context,
            screen.x,
            screen.y,
            screen.width,
            screen.height,
            screen.width * 0.16
        );
        context.lineWidth = 1;
        context.strokeStyle =
            "rgba(171, 205, 255, 0.34)";
        context.stroke();

        context.fillStyle =
            "rgba(45, 212, 191, 0.78)";
        context.shadowColor =
            "rgba(45, 212, 191, 0.45)";
        context.shadowBlur = 5;
        context.beginPath();
        context.arc(
            body.x + body.width * 0.77,
            body.y + body.height * 0.885,
            body.width * 0.025,
            0,
            Math.PI * 2
        );
        context.fill();
        context.shadowColor = "transparent";

        context.strokeStyle =
            "rgba(167, 139, 250, 0.68)";
        context.lineWidth = 2.1;
        context.lineCap = "round";

        for (const direction of [-1, 1]) {
            context.beginPath();
            context.moveTo(
                body.x
                    + body.width
                    * (
                        direction < 0
                            ? 0.3
                            : 0.7
                    ),
                body.y + body.height
            );
            context.lineTo(
                body.x
                    + body.width
                    * (
                        direction < 0
                            ? 0.25
                            : 0.75
                    ),
                body.y + body.height + 2.3
            );
            context.stroke();
        }
    }

    drawSparkle(
        context,
        centerX,
        centerY,
        radius,
        timestamp,
        elapsed,
        openingProgress,
        isReduced
    ) {
        let scale = openingProgress;

        if (
            !isReduced
            && (
                this.state === "idle"
                || this.state === "review"
            )
        ) {
            scale *=
                0.96
                + Math.sin(
                    timestamp / 520
                )
                * 0.04;
        }

        if (this.state === "selected") {
            scale *=
                1
                + Math.sin(
                    clamp(
                        elapsed / 420,
                        0,
                        1
                    )
                    * Math.PI
                )
                * 0.18;
        }

        if (this.state === "results") {
            scale *=
                1
                + Math.sin(
                    clamp(
                        elapsed / 720,
                        0,
                        1
                    )
                    * Math.PI
                )
                * 0.24;
        }

        if (this.state === "failed") {
            scale *=
                0.94
                + Math.sin(
                    elapsed / 72
                )
                * 0.035;
        }

        const gradient =
            context.createLinearGradient(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius
            );

        if (this.state === "failed") {
            gradient.addColorStop(
                0,
                "#ff9aab"
            );
            gradient.addColorStop(
                1,
                "#f472b6"
            );
        } else {
            gradient.addColorStop(
                0,
                "#8bb6ff"
            );
            gradient.addColorStop(
                0.47,
                "#a78bfa"
            );
            gradient.addColorStop(
                0.78,
                "#f472b6"
            );
            gradient.addColorStop(
                1,
                "#2dd4bf"
            );
        }

        context.save();
        context.translate(centerX, centerY);
        context.scale(scale, scale);
        context.translate(-centerX, -centerY);
        context.fillStyle = gradient;
        context.shadowColor =
            this.state === "failed"
                ? "rgba(244, 114, 182, 0.5)"
                : "rgba(139, 92, 246, 0.56)";
        context.shadowBlur =
            6 + 4 * openingProgress;

        sparklePath(
            context,
            centerX,
            centerY,
            radius
        );

        context.fill();
        context.shadowColor = "transparent";

        context.globalAlpha = 0.36;
        sparklePath(
            context,
            centerX - radius * 0.13,
            centerY - radius * 0.14,
            radius * 0.45
        );
        context.fillStyle = "#ffffff";
        context.fill();
        context.restore();
    }

    drawSignalOrbit(
        context,
        centerX,
        centerY,
        orbitRadius,
        timestamp
    ) {
        const speed =
            this.state === "selected"
                ? 440
                : 690;

        const angle =
            timestamp / speed;

        const colors = [
            "#2dd4bf",
            "#8bb6ff"
        ];

        colors.forEach((color, index) => {
            const currentAngle =
                angle
                + index * Math.PI;

            context.fillStyle = color;
            context.shadowColor = color;
            context.shadowBlur = 5;
            context.beginPath();
            context.arc(
                centerX
                    + Math.cos(currentAngle)
                    * orbitRadius,
                centerY
                    + Math.sin(currentAngle)
                    * orbitRadius
                    * 0.62,
                1.2,
                0,
                Math.PI * 2
            );
            context.fill();
        });

        context.shadowColor = "transparent";
    }

    drawParticles(
        context,
        centerX,
        centerY,
        elapsed
    ) {
        const progress =
            clamp(elapsed / 900, 0, 1);

        const particles = [
            [-0.9, -0.8, "#2dd4bf"],
            [0.92, -0.64, "#8bb6ff"],
            [-1, 0.25, "#f472b6"],
            [0.86, 0.54, "#a78bfa"],
            [0.08, -1, "#e6b566"]
        ];

        particles.forEach(
            ([x, y, color], index) => {
                const eased =
                    easeOutCubic(progress);

                context.globalAlpha =
                    Math.max(
                        0,
                        1
                        - progress
                        + index * 0.03
                    );

                context.fillStyle = color;
                context.beginPath();
                context.arc(
                    centerX
                        + x * 14 * eased,
                    centerY
                        + y * 11 * eased,
                    1.05,
                    0,
                    Math.PI * 2
                );
                context.fill();
            }
        );

        context.globalAlpha = 1;
    }
}

function roundedRectPath(
    context,
    x,
    y,
    width,
    height,
    radius
) {
    const safeRadius =
        Math.min(
            radius,
            width / 2,
            height / 2
        );

    context.beginPath();
    context.moveTo(
        x + safeRadius,
        y
    );
    context.lineTo(
        x + width - safeRadius,
        y
    );
    context.quadraticCurveTo(
        x + width,
        y,
        x + width,
        y + safeRadius
    );
    context.lineTo(
        x + width,
        y + height - safeRadius
    );
    context.quadraticCurveTo(
        x + width,
        y + height,
        x + width - safeRadius,
        y + height
    );
    context.lineTo(
        x + safeRadius,
        y + height
    );
    context.quadraticCurveTo(
        x,
        y + height,
        x,
        y + height - safeRadius
    );
    context.lineTo(
        x,
        y + safeRadius
    );
    context.quadraticCurveTo(
        x,
        y,
        x + safeRadius,
        y
    );
    context.closePath();
}

function sparklePath(
    context,
    centerX,
    centerY,
    radius
) {
    const inner =
        radius * 0.22;

    context.beginPath();
    context.moveTo(
        centerX,
        centerY - radius
    );

    context.bezierCurveTo(
        centerX + inner * 0.3,
        centerY - inner,
        centerX + inner,
        centerY - inner * 0.3,
        centerX + radius,
        centerY
    );

    context.bezierCurveTo(
        centerX + inner,
        centerY + inner * 0.3,
        centerX + inner * 0.3,
        centerY + inner,
        centerX,
        centerY + radius
    );

    context.bezierCurveTo(
        centerX - inner * 0.3,
        centerY + inner,
        centerX - inner,
        centerY + inner * 0.3,
        centerX - radius,
        centerY
    );

    context.bezierCurveTo(
        centerX - inner,
        centerY - inner * 0.3,
        centerX - inner * 0.3,
        centerY - inner,
        centerX,
        centerY - radius
    );

    context.closePath();
}

function buildPresetExperience() {
    const root =
        form?.querySelector(
            ".mio-form__suggestions"
        );

    if (!root || !promptInput) {
        return;
    }

    const heading =
        document.createElement("div");
    heading.className =
        "mio-form__discovery-heading";

    const headingTitle =
        document.createElement("strong");
    headingTitle.textContent =
        "Quick discovery with AI";

    const headingHint =
        document.createElement("span");
    headingHint.textContent =
        "Choose, then personalize";

    heading.append(
        headingTitle,
        headingHint
    );

    const tablist =
        document.createElement("div");
    tablist.className = "mio-preset-tabs";
    tablist.setAttribute("role", "tablist");
    tablist.setAttribute(
        "aria-label",
        "Mio discovery categories"
    );

    const panelContainer =
        document.createElement("div");
    panelContainer.className =
        "mio-preset-panels";

    const feedback =
        document.createElement("p");
    feedback.className =
        "mio-selection-feedback";
    feedback.setAttribute("role", "status");
    feedback.setAttribute(
        "aria-live",
        "polite"
    );
    feedback.textContent =
        "Choose a starting point or write your own request.";

    const tabs = [];
    const panels = [];
    const presetButtons = [];

    PRESET_GROUPS.forEach(
        (group, groupIndex) => {
            const tab =
                document.createElement("button");
            tab.type = "button";
            tab.className = "mio-preset-tab";
            tab.id =
                "mio-preset-tab-"
                + group.id;
            tab.textContent = group.label;
            tab.setAttribute("role", "tab");
            tab.setAttribute(
                "aria-selected",
                groupIndex === 0
                    ? "true"
                    : "false"
            );
            tab.setAttribute(
                "aria-controls",
                "mio-preset-panel-"
                + group.id
            );
            tab.tabIndex =
                groupIndex === 0
                    ? 0
                    : -1;

            const groupPanel =
                document.createElement("div");
            groupPanel.className =
                "mio-preset-panel";
            groupPanel.id =
                "mio-preset-panel-"
                + group.id;
            groupPanel.setAttribute(
                "role",
                "tabpanel"
            );
            groupPanel.setAttribute(
                "aria-labelledby",
                tab.id
            );
            groupPanel.hidden =
                groupIndex !== 0;

            const grid =
                document.createElement("div");
            grid.className =
                "mio-preset-grid";

            group.items.forEach(item => {
                const button =
                    document.createElement(
                        "button"
                    );

                button.type = "button";
                button.className = "mio-preset";
                button.dataset.tone = item.tone;
                button.dataset.mioPrompt =
                    item.prompt;
                button.setAttribute(
                    "aria-pressed",
                    "false"
                );

                const signal =
                    document.createElement(
                        "span"
                    );
                signal.className =
                    "mio-preset__signal";
                signal.setAttribute(
                    "aria-hidden",
                    "true"
                );

                const label =
                    document.createElement(
                        "span"
                    );
                label.className =
                    "mio-preset__label";
                label.textContent = item.label;

                const check =
                    document.createElement(
                        "span"
                    );
                check.className =
                    "mio-preset__check";
                check.setAttribute(
                    "aria-hidden",
                    "true"
                );
                check.textContent = "✓";

                button.append(
                    signal,
                    label,
                    check
                );

                button.addEventListener(
                    "click",
                    () => {
                        presetButtons.forEach(
                            preset => {
                                preset.classList
                                    .remove(
                                        "is-selected"
                                    );
                                preset.setAttribute(
                                    "aria-pressed",
                                    "false"
                                );
                            }
                        );

                        button.classList.add(
                            "is-selected"
                        );
                        button.setAttribute(
                            "aria-pressed",
                            "true"
                        );

                        selectedPrompt =
                            item.prompt;
                        promptInput.value =
                            item.prompt;

                        promptInput.dispatchEvent(
                            new Event(
                                "input",
                                {
                                    bubbles: true
                                }
                            )
                        );

                        feedback.textContent =
                            "Selection added · "
                            + "Add more details if you like.";
                        feedback.classList.add(
                            "is-active"
                        );

                        mioSignal?.setState(
                            "selected"
                        );
                    }
                );

                presetButtons.push(button);
                grid.append(button);
            });

            groupPanel.append(grid);
            tabs.push(tab);
            panels.push(groupPanel);
            tablist.append(tab);
            panelContainer.append(groupPanel);
        }
    );

    function activateTab(
        index,
        {
            moveFocus = false,
            animate = true
        } = {}
    ) {
        const safeIndex =
            (
                index
                + tabs.length
            )
            % tabs.length;

        tabs.forEach((tab, tabIndex) => {
            const isActive =
                tabIndex === safeIndex;

            tab.setAttribute(
                "aria-selected",
                isActive
                    ? "true"
                    : "false"
            );
            tab.tabIndex =
                isActive
                    ? 0
                    : -1;

            panels[tabIndex].hidden =
                !isActive;
        });

        const activePanel =
            panels[safeIndex];

        if (
            animate
            && !reducedMotion.matches
            && typeof activePanel.animate
                === "function"
        ) {
            activePanel.animate(
                [
                    {
                        opacity: 0,
                        transform:
                            "translateY(6px)"
                    },
                    {
                        opacity: 1,
                        transform:
                            "translateY(0)"
                    }
                ],
                {
                    duration: 180,
                    easing:
                        "cubic-bezier(0.2, 0, 0, 1)"
                }
            );
        }

        if (moveFocus) {
            tabs[safeIndex].focus();
        }
    }

    tabs.forEach((tab, index) => {
        tab.addEventListener(
            "click",
            () => {
                activateTab(index);
            }
        );

        tab.addEventListener(
            "keydown",
            event => {
                let nextIndex = null;

                switch (event.key) {
                    case "ArrowRight":
                        nextIndex = index + 1;
                        break;

                    case "ArrowLeft":
                        nextIndex = index - 1;
                        break;

                    case "Home":
                        nextIndex = 0;
                        break;

                    case "End":
                        nextIndex =
                            tabs.length - 1;
                        break;

                    default:
                        return;
                }

                event.preventDefault();
                activateTab(
                    nextIndex,
                    {
                        moveFocus: true
                    }
                );
            }
        );
    });

    promptInput.addEventListener(
        "input",
        () => {
            if (
                !selectedPrompt
                || promptInput.value
                    === selectedPrompt
            ) {
                return;
            }

            selectedPrompt = "";

            presetButtons.forEach(button => {
                button.classList.remove(
                    "is-selected"
                );
                button.setAttribute(
                    "aria-pressed",
                    "false"
                );
            });

            feedback.textContent =
                "Your custom request is ready · "
                + "Mio will take it from here.";
            feedback.classList.remove(
                "is-active"
            );
        }
    );

    root.replaceChildren(
        heading,
        tablist,
        panelContainer,
        feedback
    );
}

function activateCanvasFallback() {
    if (!menu || !canvas) {
        return;
    }

    const fallback =
        document.createElement("span");

    fallback.className =
        "mio-menu__avatar-fallback";
    fallback.setAttribute(
        "aria-hidden",
        "true"
    );

    canvas.after(fallback);
    menu.classList.add(
        "has-canvas-fallback"
    );
}

function cancelOpeningAnimations() {
    activeOpeningAnimations.forEach(
        animation => {
            animation.cancel();
        }
    );

    activeOpeningAnimations = [];
}

async function openMenu() {
    if (
        !menu
        || !summary
        || !panel
    ) {
        return;
    }

    const revision =
        ++transitionRevision;

    cancelOpeningAnimations();

    menu.open = true;
    menu.classList.remove(
        "is-closing"
    );
    menu.classList.add(
        "is-open",
        "is-opening"
    );

    summary.setAttribute(
        "aria-expanded",
        "true"
    );

    mioSignal?.setState("opening");

    if (
        reducedMotion.matches
        || typeof panel.animate
            !== "function"
    ) {
        menu.classList.remove(
            "is-opening"
        );
        return;
    }

    const panelAnimation =
        panel.animate(
            [
                {
                    opacity: 0,
                    transform:
                        "translateY(16px) "
                        + "scale(0.965)"
                },
                {
                    opacity: 1,
                    transform:
                        "translateY(0) scale(1)"
                }
            ],
            {
                duration: OPEN_DURATION,
                easing: PANEL_EASING,
                fill: "both"
            }
        );

    const stagedElements = [
        panel.querySelector(
            ".mio-menu__header"
        ),
        panel.querySelector(
            ".mio-form textarea"
        ),
        panel.querySelector(
            ".mio-form__suggestions"
        ),
        panel.querySelector(
            ".mio-form__submit"
        ),
        panel.querySelector(
            ".mio-results"
        )
    ].filter(Boolean);

    const stagedAnimations =
        stagedElements.map(
            (element, index) =>
                element.animate(
                [
                    {
                        opacity: 0,
                        transform:
                            "translateY(8px)"
                    },
                    {
                        opacity: 1,
                        transform:
                            "translateY(0)"
                    }
                ],
                {
                    duration: 260,
                    delay:
                        90 + index * 42,
                    easing: PANEL_EASING,
                    fill: "both"
                }
            )
        );

    activeOpeningAnimations = [
        panelAnimation,
        ...stagedAnimations
    ];

    await settleAnimations(
        activeOpeningAnimations,
        OPEN_DURATION + 180
    );

    if (
        revision
        !== transitionRevision
    ) {
        cancelOpeningAnimations();
        return;
    }

    cancelOpeningAnimations();
    menu.classList.remove(
        "is-opening"
    );
}

async function closeMenu({
    returnFocus = false
} = {}) {
    if (
        !menu
        || !summary
        || !panel
        || !menu.open
    ) {
        return;
    }

    const revision =
        ++transitionRevision;

    cancelOpeningAnimations();

    menu.classList.remove(
        "is-opening"
    );
    menu.classList.add(
        "is-closing"
    );

    let closeAnimation = null;

    if (
        !reducedMotion.matches
        && typeof panel.animate
            === "function"
    ) {
        closeAnimation =
            panel.animate(
                [
                    {
                        opacity: 1,
                        transform:
                            "translateY(0) scale(1)"
                    },
                    {
                        opacity: 0,
                        transform:
                            "translateY(10px) "
                            + "scale(0.98)"
                    }
                ],
                {
                    duration: CLOSE_DURATION,
                    easing: CLOSE_EASING,
                    fill: "both"
                }
            );

        await settleAnimation(
            closeAnimation,
            CLOSE_DURATION + 80
        );
    }

    if (
        revision
        !== transitionRevision
    ) {
        closeAnimation?.cancel();
        return;
    }

    closeAnimation?.cancel();
    menu.open = false;
    menu.classList.remove(
        "is-open",
        "is-closing"
    );

    summary.setAttribute(
        "aria-expanded",
        "false"
    );

    mioSignal?.setState("idle");

    if (returnFocus) {
        summary.focus({
            preventScroll: true
        });
    }
}

function settleAnimation(
    animation,
    timeout
) {
    return Promise.race([
        animation.finished.catch(
            () => undefined
        ),
        new Promise(resolve => {
            window.setTimeout(
                resolve,
                timeout
            );
        })
    ]);
}

function settleAnimations(
    animations,
    timeout
) {
    return Promise.race([
        Promise.all(
            animations.map(
                animation =>
                    animation.finished.catch(
                        () => undefined
                    )
            )
        ),
        new Promise(resolve => {
            window.setTimeout(
                resolve,
                timeout
            );
        })
    ]);
}

function applyServerState(state) {
    const safeState =
        SERVER_STATES.has(state)
            ? state
            : "idle";

    switch (safeState) {
        case "results":
            mioSignal?.setState("results");
            revealResults();
            break;

        case "waiting":
        case "empty":
            mioSignal?.setState("waiting");
            break;

        case "failed":
            mioSignal?.setState("failed");
            break;

        default:
            mioSignal?.setState("idle");
    }
}

function revealResults() {
    if (
        !menu
        || reducedMotion.matches
    ) {
        return;
    }

    menu
        .querySelectorAll(".mio-film")
        .forEach((film, index) => {
            film.style.setProperty(
                "--mio-result-index",
                String(index)
            );

            film.classList.add(
                "is-revealing"
            );

            film.addEventListener(
                "animationend",
                () => {
                    film.classList.remove(
                        "is-revealing"
                    );
                },
                {
                    once: true
                }
            );
        });
}

function clamp(value, minimum, maximum) {
    return Math.min(
        maximum,
        Math.max(minimum, value)
    );
}

function easeOutCubic(value) {
    return 1 - Math.pow(1 - value, 3);
}

function toggleMenu() {
    if (menu?.open) {
        void closeMenu();
        return;
    }

    void openMenu();
}

if (menu && summary && panel) {
    summary.setAttribute(
        "aria-expanded",
        menu.open
            ? "true"
            : "false"
    );

    if (menu.open) {
        menu.classList.add("is-open");
    }

    summary.addEventListener(
        "click",
        event => {
            event.preventDefault();
            toggleMenu();
        }
    );

    summary.addEventListener(
        "keydown",
        event => {
            if (
                event.repeat
                || (
                    event.key !== "Enter"
                    && event.key !== " "
                )
            ) {
                return;
            }

            event.preventDefault();
            toggleMenu();
        }
    );

    document.addEventListener(
        "keydown",
        event => {
            if (
                event.key !== "Escape"
                || !menu.open
            ) {
                return;
            }

            event.preventDefault();

            void closeMenu({
                returnFocus: true
            });
        }
    );

    document.addEventListener(
        "pointerdown",
        event => {
            if (
                !menu.open
                || menu.contains(event.target)
            ) {
                return;
            }

            void closeMenu();
        },
        {
            passive: true
        }
    );
}

if (canvas && menu) {
    try {
        mioSignal =
            new MioSignal(canvas);
        mioSignal.mount();
    } catch (error) {
        activateCanvasFallback();
        console.warn(
            "[Mio] Canvas simgesi "
            + "kullanılamadı; statik simge "
            + "etkinleştirildi.",
            error
        );
    }
}

window.addEventListener(
    "pointermove",
    event => {
        if (
            !menu?.open
            || event.pointerType === "touch"
        ) {
            return;
        }

        mioSignal?.lookAt(
            event.clientX,
            event.clientY
        );
    },
    {
        passive: true
    }
);

window.addEventListener(
    "pointerleave",
    () => {
        mioSignal?.resetLook();
    }
);

buildPresetExperience();

form?.addEventListener(
    "submit",
    () => {
        if (!form.checkValidity()) {
            return;
        }

        if (submitButton) {
            submitButton.disabled = true;
            submitButton.classList.add(
                "is-busy"
            );
            submitButton.setAttribute(
                "aria-busy",
                "true"
            );
        }

        form.setAttribute(
            "aria-busy",
            "true"
        );

        if (status) {
            status.textContent =
                "I am interpreting your request "
                + "and finding options "
                + "just for you…";
        }

        mioSignal?.setState("running");
    }
);

applyServerState(
    menu?.dataset.mioState
);
