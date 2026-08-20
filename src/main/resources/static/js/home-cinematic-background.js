import * as THREE from "https://cdn.jsdelivr.net/npm/three@0.185.1/build/three.module.min.js";

/*
 * Film Archive — Cinematic Background
 *
 * Render mimarisi:
 * 1. Fullscreen GLSL plane: gradient, cursor aura, noise, grain.
 * 2. Tek BufferGeometry/Points: cinematic dust ve sahte bokeh.
 *
 * Parçacıklar CPU'da her frame güncellenmez.
 * Yalnızca shader uniform'ları değiştirilir.
 */

const BACKGROUND_VERTEX_SHADER = /* glsl */ `
    varying vec2 vUv;

    void main() {
        vUv = uv;

        gl_Position =
            projectionMatrix *
            modelViewMatrix *
            vec4(position, 1.0);
    }
`;

const BACKGROUND_FRAGMENT_SHADER = /* glsl */ `
    precision highp float;

    uniform float uTime;
    uniform float uPointerEnergy;

    uniform vec2 uResolution;
    uniform vec2 uPointer;
    uniform float uThemeMix;

    varying vec2 vUv;

    float hash21(vec2 value) {
        value = fract(
            value * vec2(
                123.34,
                456.21
            )
        );

        value += dot(
            value,
            value + 45.32
        );

        return fract(
            value.x * value.y
        );
    }

    float valueNoise(vec2 point) {
        vec2 cell = floor(point);
        vec2 local = fract(point);

        local =
            local *
            local *
            (
                3.0 -
                2.0 * local
            );

        float bottomLeft =
            hash21(cell);

        float bottomRight =
            hash21(
                cell +
                vec2(1.0, 0.0)
            );

        float topLeft =
            hash21(
                cell +
                vec2(0.0, 1.0)
            );

        float topRight =
            hash21(
                cell +
                vec2(1.0, 1.0)
            );

        return mix(
            mix(
                bottomLeft,
                bottomRight,
                local.x
            ),
            mix(
                topLeft,
                topRight,
                local.x
            ),
            local.y
        );
    }

    float fbm(vec2 point) {
        float result = 0.0;
        float amplitude = 0.5;

        mat2 rotation = mat2(
             0.80,  0.60,
            -0.60,  0.80
        );

        for (int octave = 0; octave < 4; octave++) {
            result +=
                amplitude *
                valueNoise(point);

            point =
                rotation *
                point *
                2.03;

            amplitude *= 0.5;
        }

        return result;
    }

    void main() {
        vec2 centered =
            vUv * 2.0 - 1.0;

        float aspect =
            uResolution.x /
            max(uResolution.y, 1.0);

        centered.x *= aspect;

        vec2 pointer =
            uPointer * 2.0 - 1.0;

        pointer.x *= aspect;

        float smokeNoise = fbm(
            centered * 0.86 +
            vec2(
                uTime * 0.022,
                -uTime * 0.016
            )
        );

        float detailNoise = fbm(
            centered * 1.72 +
            vec2(
                -uTime * 0.011,
                uTime * 0.014
            ) +
            smokeNoise * 0.19
        );

    vec3 nightBase =
        vec3(0.0392, 0.0392, 0.0471);

    vec3 nightSmoke =
        vec3(0.0706, 0.0706, 0.0706);

    vec3 nightLift =
        vec3(0.1020, 0.1020, 0.1176);

    vec3 dayBase =
        vec3(0.8902, 0.8353, 0.7373);

    vec3 daySmoke =
        vec3(0.9373, 0.9020, 0.8392);

    vec3 dayLift =
        vec3(0.9686, 0.9490, 0.9098);

    vec3 baseColor =
        mix(nightBase, dayBase, uThemeMix);

    vec3 smokeColor =
        mix(nightSmoke, daySmoke, uThemeMix);

    vec3 liftColor =
        mix(nightLift, dayLift, uThemeMix);

    vec3 color = mix(
        baseColor,
        smokeColor,
        0.42 + smokeNoise * 0.18
    );

    color = mix(
        color,
        liftColor,
        detailNoise * 0.18
    );

        /*
         * Farenin arkasındaki geniş ve düşük yoğunluklu aura.
         * Küçük, keskin bir neon halka yerine geniş bir sinema
         * projektörü yansıması gibi davranır.
         */
        vec2 glowDelta =
            centered - pointer;

        glowDelta.y *= 1.08;

        float nearAura = exp(
            -dot(glowDelta, glowDelta) *
            2.45
        );

        float farAura = exp(
            -dot(glowDelta, glowDelta) *
            0.52
        );

        vec3 imdbGold =
            vec3(
                0.9608,
                0.7725,
                0.0941
            );

        vec3 cinematicAmber =
            vec3(
                0.5490,
                0.4275,
                0.0745
            );

        vec3 auraColor = mix(
            cinematicAmber,
            imdbGold,
            0.24 + smokeNoise * 0.2
        );

      color +=
        auraColor *
        (
            nearAura * 0.082 +
            farAura * 0.019
        ) *
        uPointerEnergy *
        mix(1.0, 0.58, uThemeMix);

        /*
         * Arka plandaki çok yavaş hareket eden ışık şeridi.
         * Stripe-benzeri akış hissi verir, fakat kontrastı düşüktür.
         */
        float wavePosition =
            centered.y +
            0.34 +
            sin(
                centered.x * 1.42 +
                uTime * 0.105 +
                smokeNoise * 1.7
            ) * 0.115;

        float lightWave =
            exp(
                -abs(wavePosition) *
                8.2
            );

        color +=
            cinematicAmber *
            lightWave *
            (
                0.015 +
                detailNoise * 0.016
            ) *
        mix(1.0, 0.52, uThemeMix);

        /*
         * Kenarlara doğru sinematik vignette.
         */
        vec2 vignetteCoordinates =
            vUv * (1.0 - vUv);

        float vignette =
            pow(
                16.0 *
                vignetteCoordinates.x *
                vignetteCoordinates.y,
                0.18
            );

        color *= mix(
            mix(0.63, 0.88, uThemeMix),
            1.0,
            vignette
        );

        /*
         * Film greni.
         * Texture kullanılmadığı için ağ isteği ve texture buffer'ı yoktur.
         */
        float grain =
            hash21(
                gl_FragCoord.xy +
                vec2(
                    fract(uTime) * 937.0,
                    fract(uTime * 0.73) * 487.0
                )
            ) - 0.5;

        color +=
            grain *
            mix(0.014, 0.007, uThemeMix);

        gl_FragColor =
            vec4(color, 1.0);

        #include <tonemapping_fragment>
        #include <colorspace_fragment>
    }
`;

const DUST_VERTEX_SHADER = /* glsl */ `
    precision highp float;

    uniform float uTime;
    uniform float uAspect;
    uniform float uPointScale;
    uniform float uPointerEnergy;
    uniform float uThemeMix;

    uniform vec2 uPointer;

    attribute float aSize;
    attribute float aPhase;
    attribute float aSpeed;

    varying float vAlpha;
    varying float vDepth;
    varying float vTwinkle;

    void main() {
        float depth =
            smoothstep(
                -1.4,
                1.4,
                position.z
            );

        float wrappedY =
            mod(
                position.y +
                uTime * aSpeed +
                1.35,
                2.70
            ) - 1.35;

        vec3 transformed =
            vec3(
                position.x * uAspect,
                wrappedY,
                position.z
            );

        transformed.x +=
            sin(
                uTime * 0.15 +
                aPhase * 6.28318
            ) *
            0.026 *
            (0.55 + depth);

        transformed.y +=
            cos(
                uTime * 0.11 +
                aPhase * 4.73
            ) *
            0.018;

        vec2 pointerPosition =
            vec2(
                uPointer.x * uAspect,
                uPointer.y
            );

        vec2 pointerDelta =
            transformed.xy -
            pointerPosition;

        float pointerDistanceSquared =
            dot(
                pointerDelta,
                pointerDelta
            );

        float pointerInfluence =
            exp(
                -pointerDistanceSquared * 7.5
            ) *
            uPointerEnergy;

        vec2 direction =
            normalize(
                pointerDelta +
                vec2(0.0001)
            );

        /*
         * İmlecin yakınındaki parçacıklar sert biçimde kaçmaz.
         * Yalnızca çok hafifçe dağılır.
         */
        transformed.xy +=
            direction *
            pointerInfluence *
            0.052 *
            (0.55 + depth);

        float twinkle =
            0.72 +
            0.28 *
            sin(
                uTime * 0.72 +
                aPhase * 12.0
            );

        vAlpha =
            mix(
                0.065,
                0.21,
                depth
            ) *
            twinkle;

        vDepth = depth;
        vTwinkle = twinkle;

        gl_PointSize =
            aSize *
            uPointScale *
            mix(
                0.72,
                1.34,
                depth
            ) *
            (
                1.0 +
                pointerInfluence * 0.22
            );

        gl_Position =
            projectionMatrix *
            modelViewMatrix *
            vec4(transformed, 1.0);
    }
`;

const DUST_FRAGMENT_SHADER = /* glsl */ `
    precision highp float;

    varying float vAlpha;
    varying float vDepth;
    varying float vTwinkle;

    void main() {
        vec2 point =
            gl_PointCoord -
            vec2(0.5);

        float radius =
            length(point) * 2.0;

        float softBody =
            1.0 -
            smoothstep(
                0.10,
                1.0,
                radius
            );

        float brightCore =
            1.0 -
            smoothstep(
                0.0,
                0.22,
                radius
            );

        float bokehHalo =
            1.0 -
            smoothstep(
                0.52,
                1.0,
                radius
            );

        float alpha =
            (
                softBody * 0.46 +
                bokehHalo * 0.32 +
                brightCore * 0.22
            ) *
            vAlpha;

        if (alpha < 0.003) {
            discard;
        }

       vec3 nightDeepAmber =
            vec3(0.5490, 0.4275, 0.0745);

        vec3 nightSoftGold =
            vec3(0.8863, 0.7137, 0.0863);

        vec3 dayDeepAmber =
            vec3(0.3800, 0.2800, 0.0500);

        vec3 daySoftGold =
            vec3(0.6400, 0.4700, 0.0600);

        float particleMix =
            vDepth * 0.72 +
            brightCore * 0.18;

        vec3 nightParticle =
            mix(
                nightDeepAmber,
                nightSoftGold,
                particleMix
            );

        vec3 dayParticle =
            mix(
                dayDeepAmber,
                daySoftGold,
                particleMix
            );

        vec3 particleColor =
            mix(
                nightParticle,
                dayParticle,
                uThemeMix
            );

        particleColor *=
            0.84 +
            vTwinkle * 0.16;

        alpha *=
            mix(1.0, 0.56, uThemeMix);

        gl_FragColor =
            vec4(
                particleColor,
                alpha
            );

        #include <tonemapping_fragment>
        #include <colorspace_fragment>
    }
`;

class CinematicBackground {
    constructor(root, canvas) {
        this.root = root;
        this.canvas = canvas;

        this.destroyed = false;
        this.contextLost = false;
        this.running = false;

        this.elapsedTime = 0;
        this.lastTimestamp = null;
        this.resizeFrame = null;

        this.themeMix =
            document.documentElement
                .dataset
                .theme === "light"
                    ? 1
                    : 0;

        this.themeMixTarget =
            this.themeMix;

        this.cssWidth = 1;
        this.cssHeight = 1;

        this.pointerCurrent =
            new THREE.Vector2(
                0.5,
                0.55
            );

        this.pointerTarget =
            new THREE.Vector2(
                0.5,
                0.55
            );

        this.pointerEnergy = 0.34;
        this.pointerEnergyTarget = 0.34;

        this.reducedMotionQuery =
            window.matchMedia(
                "(prefers-reduced-motion: reduce)"
            );

        this.coarsePointerQuery =
            window.matchMedia(
                "(pointer: coarse)"
            );

        const processorCount =
            navigator.hardwareConcurrency ?? 8;

        const deviceMemory =
            navigator.deviceMemory ?? 8;

        this.lowPowerDevice =
            this.coarsePointerQuery.matches ||
            processorCount <= 4 ||
            deviceMemory <= 4;

        this.quality = {
            maximumPixelRatio:
                this.lowPowerDevice
                    ? 1.15
                    : 1.5,

            maximumPixelCount:
                this.lowPowerDevice
                    ? 1_350_000
                    : 2_600_000,

            particleCount:
                this.lowPowerDevice
                    ? 130
                    : 230
        };

        this.renderFrame =
            this.renderFrame.bind(this);

        this.queueResize =
            this.queueResize.bind(this);

        this.handlePointerMove =
            this.handlePointerMove.bind(this);

        this.handleVisibilityChange =
            this.handleVisibilityChange.bind(this);

        this.handleThemeChange =
            this.handleThemeChange.bind(this);

        this.handleMotionPreferenceChange =
            this.handleMotionPreferenceChange.bind(this);

        this.handleContextLost =
            this.handleContextLost.bind(this);

        this.handleContextRestored =
            this.handleContextRestored.bind(this);

        this.handlePageHide =
            this.handlePageHide.bind(this);
    }

    mount() {
        this.createRenderer();
        this.createBackgroundScene();
        this.createDustScene();
        this.bindEvents();

        this.resize();
        this.renderScene();

        this.root.classList.add("is-ready");
        this.root.classList.remove("is-fallback");

        this.syncAnimationLoop();
    }

    createRenderer() {
        this.renderer =
            new THREE.WebGLRenderer({
                canvas: this.canvas,
                alpha: false,
                antialias: false,
                depth: false,
                stencil: false,
                preserveDrawingBuffer: false,
                powerPreference: "high-performance",
                failIfMajorPerformanceCaveat: true
            });

        /*
         * Pixel ratio elle hesaplanıyor.
         * Böylece DPR ve toplam drawing-buffer piksel sayısı
         * ayrı ayrı sınırlandırılabiliyor.
         */
        this.renderer.setPixelRatio(1);

        this.renderer.setClearColor(
            this.themeMix > 0.5
                ? 0xf7f2e8
                : 0x0a0a0c,
            1
        );

        this.renderer.outputColorSpace =
            THREE.SRGBColorSpace;

        this.renderer.toneMapping =
            THREE.ACESFilmicToneMapping;

        this.renderer.toneMappingExposure =
            0.88;

        this.renderer.autoClear = false;
    }

    createBackgroundScene() {
        this.backgroundScene =
            new THREE.Scene();

        this.backgroundCamera =
            new THREE.OrthographicCamera(
                -1,
                1,
                1,
                -1,
                0,
                2
            );

        this.backgroundCamera.position.z = 1;

        this.backgroundUniforms = {
            uTime: {
                value: 0
            },

            uResolution: {
                value:
                    new THREE.Vector2(
                        1,
                        1
                    )
            },

            uPointer: {
                value:
                    new THREE.Vector2(
                        0.5,
                        0.55
                    )
            },

            uPointerEnergy: {
                value: 0.34
            },

            uThemeMix: {
                value: this.themeMix
            }
        };

        this.backgroundGeometry =
            new THREE.PlaneGeometry(
                2,
                2,
                1,
                1
            );

        this.backgroundMaterial =
            new THREE.ShaderMaterial({
                uniforms:
                    this.backgroundUniforms,

                vertexShader:
                    BACKGROUND_VERTEX_SHADER,

                fragmentShader:
                    BACKGROUND_FRAGMENT_SHADER,

                depthTest: false,
                depthWrite: false,
                transparent: false
            });

        this.backgroundPlane =
            new THREE.Mesh(
                this.backgroundGeometry,
                this.backgroundMaterial
            );

        this.backgroundPlane.frustumCulled = false;

        this.backgroundScene.add(
            this.backgroundPlane
        );
    }

    createDustScene() {
        this.dustScene =
            new THREE.Scene();

        this.dustCamera =
            new THREE.OrthographicCamera(
                -1,
                1,
                1,
                -1,
                -10,
                10
            );

        this.dustCamera.position.z = 2;

        const particleCount =
            this.quality.particleCount;

        /*
         * Buffer'lar yalnızca bir kez oluşturulur.
         * Render döngüsünde push/splice, yeni array veya
         * needsUpdate işlemi yapılmaz.
         */
        const positions =
            new Float32Array(
                particleCount * 3
            );

        const sizes =
            new Float32Array(
                particleCount
            );

        const phases =
            new Float32Array(
                particleCount
            );

        const speeds =
            new Float32Array(
                particleCount
            );

        const random =
            createSeededRandom(
                0x51f1a4c7
            );

        for (
            let index = 0;
            index < particleCount;
            index += 1
        ) {
            const offset =
                index * 3;

            positions[offset] =
                (
                    random() -
                    0.5
                ) * 2.45;

            positions[offset + 1] =
                (
                    random() -
                    0.5
                ) * 2.70;

            positions[offset + 2] =
                THREE.MathUtils.lerp(
                    -1.4,
                    1.4,
                    random()
                );

            sizes[index] =
                THREE.MathUtils.lerp(
                    1.4,
                    4.1,
                    Math.pow(
                        random(),
                        1.65
                    )
                );

            phases[index] =
                random();

            speeds[index] =
                THREE.MathUtils.lerp(
                    0.018,
                    0.055,
                    random()
                );
        }

        this.dustGeometry =
            new THREE.BufferGeometry();

        this.dustGeometry.setAttribute(
            "position",
            new THREE.BufferAttribute(
                positions,
                3
            )
        );

        this.dustGeometry.setAttribute(
            "aSize",
            new THREE.BufferAttribute(
                sizes,
                1
            )
        );

        this.dustGeometry.setAttribute(
            "aPhase",
            new THREE.BufferAttribute(
                phases,
                1
            )
        );

        this.dustGeometry.setAttribute(
            "aSpeed",
            new THREE.BufferAttribute(
                speeds,
                1
            )
        );

        this.dustUniforms = {
            uTime: {
                value: 0
            },

            uAspect: {
                value: 1
            },

            uPointScale: {
                value: 1
            },

            uPointer: {
                value:
                    new THREE.Vector2(
                        0,
                        0.1
                    )
            },

            uPointerEnergy: {
                value: 0.34
            },

            uThemeMix: {
                value: this.themeMix
            }
        };

        this.dustMaterial =
            new THREE.ShaderMaterial({
                uniforms:
                    this.dustUniforms,

                vertexShader:
                    DUST_VERTEX_SHADER,

                fragmentShader:
                    DUST_FRAGMENT_SHADER,

                transparent: true,
                depthTest: false,
                depthWrite: false,
                blending:
                    THREE.AdditiveBlending
            });

        this.dustPoints =
            new THREE.Points(
                this.dustGeometry,
                this.dustMaterial
            );

        this.dustPoints.frustumCulled = false;

        this.dustScene.add(
            this.dustPoints
        );
    }

    bindEvents() {
        window.addEventListener(
            "pointermove",
            this.handlePointerMove,
            {
                passive: true
            }
        );

        document.addEventListener(
            "visibilitychange",
            this.handleVisibilityChange
        );

        this.canvas.addEventListener(
            "webglcontextlost",
            this.handleContextLost
        );

        this.canvas.addEventListener(
            "webglcontextrestored",
            this.handleContextRestored
        );

        window.addEventListener(
            "pagehide",
            this.handlePageHide
        );

        window.addEventListener(
            "filmarchive:themechange",
            this.handleThemeChange
        );

        if (
            typeof this.reducedMotionQuery
                .addEventListener === "function"
        ) {
            this.reducedMotionQuery.addEventListener(
                "change",
                this.handleMotionPreferenceChange
            );
        } else {
            this.reducedMotionQuery.addListener(
                this.handleMotionPreferenceChange
            );
        }

        /*
         * Zaman tabanlı debounce yerine ResizeObserver sonuçlarını
         * tek requestAnimationFrame içinde birleştiriyoruz.
         * Böylece resize, browser paint döngüsüne hizalanıyor.
         */
        if ("ResizeObserver" in window) {
            this.resizeObserver =
                new ResizeObserver(
                    this.queueResize
                );

            this.resizeObserver.observe(
                this.root
            );
        } else {
            window.addEventListener(
                "resize",
                this.queueResize,
                {
                    passive: true
                }
            );
        }
    }

    handlePointerMove(event) {
        if (
            this.destroyed ||
            this.reducedMotionQuery.matches ||
            event.pointerType === "touch"
        ) {
            return;
        }

        /*
         * Pointer event içerisinde render veya DOM ölçümü yoktur.
         * Yalnızca mevcut sayısal hedef değerler güncellenir.
         */
        const normalizedX =
            THREE.MathUtils.clamp(
                event.clientX /
                Math.max(
                    this.cssWidth,
                    1
                ),
                0,
                1
            );

        const normalizedY =
            THREE.MathUtils.clamp(
                1 -
                event.clientY /
                Math.max(
                    this.cssHeight,
                    1
                ),
                0,
                1
            );

        this.pointerTarget.set(
            normalizedX,
            normalizedY
        );

        this.pointerEnergyTarget = 1;
    }

    handleVisibilityChange() {
        this.syncAnimationLoop();
    }

    handleMotionPreferenceChange() {
        if (
            this.reducedMotionQuery.matches
        ) {
            this.pointerTarget.set(
                0.5,
                0.55
            );

            this.pointerCurrent.copy(
                this.pointerTarget
            );

            this.pointerEnergy = 0.28;
            this.pointerEnergyTarget = 0.28;

            this.backgroundUniforms.uTime.value = 0;
            this.dustUniforms.uTime.value = 0;
        }

        this.syncAnimationLoop();

        if (
            !this.contextLost &&
            !this.destroyed
        ) {
            this.updateUniforms();
            this.renderScene();
        }
    }

    handleContextLost(event) {
        /*
         * preventDefault, tarayıcının context'i yeniden
         * oluşturmasına izin verir.
         */
        event.preventDefault();

        this.contextLost = true;

        this.root.classList.remove(
            "is-ready"
        );

        this.root.classList.add(
            "is-context-lost"
        );

        this.syncAnimationLoop();
    }

    handleContextRestored() {
        this.contextLost = false;

        this.root.classList.remove(
            "is-context-lost"
        );

        /*
         * Three.js kendi WebGL cache ve state'ini restore eder.
         * Uygulama tarafında yalnızca ölçüler/uniform'lar yenilenir.
         */
        this.resize();
        this.updateUniforms();
        this.renderScene();

        this.root.classList.add(
            "is-ready"
        );

        this.syncAnimationLoop();
    }

    handlePageHide(event) {
        /*
         * Back-forward cache'e giren sayfayı yok etmiyoruz.
         * Normal sayfa kapanışında GPU kaynaklarını temizliyoruz.
         */
        if (!event.persisted) {
            this.destroy();
        }
    }

    queueResize() {
        if (
            this.resizeFrame !== null ||
            this.destroyed
        ) {
            return;
        }

        this.resizeFrame =
            window.requestAnimationFrame(
                () => {
                    this.resizeFrame = null;
                    this.resize();
                }
            );
    }

    resize() {
        if (
            this.destroyed ||
            this.contextLost
        ) {
            return;
        }

        const bounds =
            this.root.getBoundingClientRect();

        this.cssWidth =
            Math.max(
                1,
                Math.round(
                    bounds.width ||
                    window.innerWidth
                )
            );

        this.cssHeight =
            Math.max(
                1,
                Math.round(
                    bounds.height ||
                    window.innerHeight
                )
            );

        const requestedPixelRatio =
            Math.min(
                window.devicePixelRatio || 1,
                this.quality.maximumPixelRatio
            );

        let bufferWidth =
            Math.max(
                1,
                Math.round(
                    this.cssWidth *
                    requestedPixelRatio
                )
            );

        let bufferHeight =
            Math.max(
                1,
                Math.round(
                    this.cssHeight *
                    requestedPixelRatio
                )
            );

        const requestedPixelCount =
            bufferWidth *
            bufferHeight;

        /*
         * 3x DPR, 9 kat piksel anlamına gelebilir.
         * Toplam drawing buffer büyüklüğü ayrıca sınırlandırılıyor.
         */
        if (
            requestedPixelCount >
            this.quality.maximumPixelCount
        ) {
            const renderScale =
                Math.sqrt(
                    this.quality.maximumPixelCount /
                    requestedPixelCount
                );

            bufferWidth =
                Math.max(
                    1,
                    Math.floor(
                        bufferWidth *
                        renderScale
                    )
                );

            bufferHeight =
                Math.max(
                    1,
                    Math.floor(
                        bufferHeight *
                        renderScale
                    )
                );
        }

        if (
            this.canvas.width !== bufferWidth ||
            this.canvas.height !== bufferHeight
        ) {
            this.renderer.setSize(
                bufferWidth,
                bufferHeight,
                false
            );
        }

        const aspect =
            this.cssWidth /
            this.cssHeight;

        this.dustCamera.left =
            -aspect;

        this.dustCamera.right =
            aspect;

        this.dustCamera.top = 1;
        this.dustCamera.bottom = -1;

        this.dustCamera.updateProjectionMatrix();

        this.backgroundUniforms
            .uResolution
            .value
            .set(
                bufferWidth,
                bufferHeight
            );

        this.dustUniforms
            .uAspect
            .value =
                aspect;

        /*
         * Point sprite boyutu CSS pikselinden drawing-buffer
         * pikseline çevrilir.
         */
        this.dustUniforms
            .uPointScale
            .value =
                bufferWidth /
                this.cssWidth;

        this.updateUniforms();
        this.renderScene();
    }

    syncAnimationLoop() {
        const shouldRun =
            !this.destroyed &&
            !this.contextLost &&
            !document.hidden &&
            !this.reducedMotionQuery.matches;

        if (
            shouldRun &&
            !this.running
        ) {
            this.running = true;
            this.lastTimestamp = null;

            /*
             * Three.js setAnimationLoop, requestAnimationFrame
             * zamanlamasını yönetir.
             */
            this.renderer.setAnimationLoop(
                this.renderFrame
            );

            return;
        }

        if (
            !shouldRun &&
            this.running
        ) {
            this.running = false;
            this.lastTimestamp = null;

            this.renderer.setAnimationLoop(
                null
            );
        }
    }

    renderFrame(timestamp) {
        if (
            this.destroyed ||
            this.contextLost
        ) {
            return;
        }

        const safeTimestamp =
            Number.isFinite(timestamp)
                ? timestamp
                : 0;

        const deltaTime =
            this.lastTimestamp === null
                ? 0
                : Math.min(
                    (
                        safeTimestamp -
                        this.lastTimestamp
                    ) / 1000,
                    0.05
                );

        this.lastTimestamp =
            safeTimestamp;

        this.elapsedTime +=
            deltaTime;

        /*
         * Frame-rate bağımsız exponential damping.
         * 60 Hz ve 120/144 Hz ekranlarda benzer hız hissi verir.
         */
        const pointerDamping =
                    1 -
                    Math.exp(
                        -deltaTime * 5.6
                    );

                this.pointerCurrent.lerp(
                    this.pointerTarget,
                    pointerDamping
                );

                this.pointerEnergyTarget =
                    Math.max(
                        0.34,
                        this.pointerEnergyTarget -
                        deltaTime * 0.36
                    );

                this.pointerEnergy =
                    THREE.MathUtils.lerp(
                        this.pointerEnergy,
                        this.pointerEnergyTarget,
                        pointerDamping
                    );

                const themeDamping =
            1 -
            Math.exp(
                -deltaTime * 4.2
            );

        this.themeMix =
            THREE.MathUtils.lerp(
                this.themeMix,
                this.themeMixTarget,
                themeDamping
            );

        this.updateUniforms();
        this.renderScene();
    }

    updateUniforms() {
        this.backgroundUniforms
            .uTime
            .value =
                this.elapsedTime;

        this.dustUniforms
            .uTime
            .value =
                this.elapsedTime;

        this.backgroundUniforms
            .uPointer
            .value
            .copy(
                this.pointerCurrent
            );

        this.dustUniforms
            .uPointer
            .value
            .set(
                this.pointerCurrent.x * 2 - 1,
                this.pointerCurrent.y * 2 - 1
            );

        this.backgroundUniforms
            .uPointerEnergy
            .value =
                this.pointerEnergy;

        this.dustUniforms
            .uPointerEnergy
            .value =
                this.pointerEnergy;

        this.backgroundUniforms
            .uThemeMix
            .value =
                this.themeMix;

        this.dustUniforms
            .uThemeMix
            .value =
                this.themeMix;

        this.renderer.toneMappingExposure =
            THREE.MathUtils.lerp(
                0.88,
                0.96,
                this.themeMix
            );
    }

    renderScene() {
        if (
            this.destroyed ||
            this.contextLost
        ) {
            return;
        }

        this.renderer.clear();

        this.renderer.render(
            this.backgroundScene,
            this.backgroundCamera
        );

        this.renderer.render(
            this.dustScene,
            this.dustCamera
        );
    }

    destroy() {
        if (this.destroyed) {
            return;
        }

        this.destroyed = true;
        this.running = false;

        this.renderer.setAnimationLoop(
            null
        );

        if (
            this.resizeFrame !== null
        ) {
            window.cancelAnimationFrame(
                this.resizeFrame
            );

            this.resizeFrame = null;
        }

        this.resizeObserver?.disconnect();

        window.removeEventListener(
            "resize",
            this.queueResize
        );

        window.removeEventListener(
            "pointermove",
            this.handlePointerMove
        );

        document.removeEventListener(
            "visibilitychange",
            this.handleVisibilityChange
        );

        window.removeEventListener(
            "pagehide",
            this.handlePageHide
        );

        window.removeEventListener(
            "filmarchive:themechange",
            this.handleThemeChange
        );

        this.canvas.removeEventListener(
            "webglcontextlost",
            this.handleContextLost
        );

        this.canvas.removeEventListener(
            "webglcontextrestored",
            this.handleContextRestored
        );

        if (
            typeof this.reducedMotionQuery
                .removeEventListener === "function"
        ) {
            this.reducedMotionQuery.removeEventListener(
                "change",
                this.handleMotionPreferenceChange
            );
        } else {
            this.reducedMotionQuery.removeListener(
                this.handleMotionPreferenceChange
            );
        }

        /*
         * Three.js GPU buffer/program kaynaklarını otomatik
         * bırakmaz; lifecycle sonunda açıkça dispose edilir.
         */
        this.backgroundGeometry.dispose();
        this.backgroundMaterial.dispose();

        this.dustGeometry.dispose();
        this.dustMaterial.dispose();

        this.renderer.dispose();
    }

    handleThemeChange(event) {
        const theme =
            event.detail?.theme ??
            document.documentElement
                .dataset
                .theme;

        const isLight =
            theme === "light";

        this.themeMixTarget =
            isLight
                ? 1
                : 0;

        this.renderer.setClearColor(
            isLight
                ? 0xf7f2e8
                : 0x0a0a0c,
            1
        );

        /*
        * Animasyon kapalıysa geçişi hemen tamamla
        * ve yalnızca tek kare çiz.
        */
        if (
            this.reducedMotionQuery.matches ||
            !this.running
        ) {
            this.themeMix =
                this.themeMixTarget;

            this.updateUniforms();

            if (
                !this.destroyed &&
                !this.contextLost
            ) {
                this.renderScene();
            }
        }
    }
}

/*
 * Sayfa her açıldığında aynı fakat doğal görünen dağılımı
 * üretir. Math.random kaynaklı görsel sıçramalar engellenir.
 */
function createSeededRandom(seed) {
    let state =
        seed >>> 0;

    return function nextRandom() {
        state +=
            0x6d2b79f5;

        let value =
            state;

        value =
            Math.imul(
                value ^ value >>> 15,
                value | 1
            );

        value ^=
            value +
            Math.imul(
                value ^ value >>> 7,
                value | 61
            );

        return (
            (
                value ^
                value >>> 14
            ) >>> 0
        ) / 4294967296;
    };
}



const root =
    document.getElementById(
        "cinematic-background"
    );

const canvas =
    document.getElementById(
        "cinematic-background-canvas"
    );

if (root && canvas) {
    try {
        const cinematicBackground =
            new CinematicBackground(
                root,
                canvas
            );

        cinematicBackground.mount();
    } catch (error) {
        root.classList.remove(
            "is-ready"
        );

        root.classList.add(
            "is-fallback"
        );

        console.warn(
            "[Film Archive] WebGL background could not start; CSS fallback is active.",
            error
        );
    }
}