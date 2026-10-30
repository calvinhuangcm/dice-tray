(function () {
    "use strict";

    var container = document.getElementById("scene-container");
    var sceneRoot = document.getElementById("scene-root");
    var box = null;
    var sceneReady = false;
    var pendingRoll = null;
    var currentRequestId = 0;
    var lastFinishedRequestId = -1;
    var tilt = {
        x: 0,
        y: 0,
        targetX: 0,
        targetY: 0,
        normalZ: 1,
        shakeX: 0,
        shakeY: 0,
        shakeZ: 0,
        sensitivity: 2.2,
        enabled: false
    };
    var tiltLoopActive = false;
    var pointerStart = null;
    var deviceHorizonResizeTimer = null;

    var BASE_GRAVITY = 9.8 * 400;
    var TILT_DEADZONE = 0.015;
    var TILT_RESPONSE = 0.42;
    var TILT_WAKE_THRESHOLD = 0.012;
    var TILT_GRAVITY_SCALE = 1.25;
    var SHAKE_GRAVITY = BASE_GRAVITY * 0.9;
    var SHAKE_WAKE_THRESHOLD = 0.045;

    function clamp(value, minimum, maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    function applyDeviceHorizon() {
        if (!box || !box.camera) return;

        var distance = box.cameraHeight && box.cameraHeight.far;
        if (!isFinite(distance) || distance <= 0) {
            distance = box.camera.position.length() || 1000;
        }

        // Android sends the gravity vector in display coordinates. Treat its
        // positive normal as the real-world up direction and build a stable
        // orthonormal frame from it. This makes the virtual tabletop remain
        // horizontal when the device is pitched or rolled in either axis.
        // The Android sensor values describe the direction of gravity, while
        // the renderer uses the opposite screen-to-table direction. The
        // physics path applies the same basis in applyGravity().
        var normalX = -(Number(tilt.targetX) || 0);
        var normalY = -(Number(tilt.targetY) || 0);
        var normalZ = Math.max(0.12, Number(tilt.normalZ) || 1);
        var normalLength = Math.hypot(normalX, normalY, normalZ) || 1;
        normalX /= normalLength;
        normalY /= normalLength;
        normalZ /= normalLength;

        // Project the device's horizontal right vector onto the plane
        // perpendicular to gravity. Fall back to the screen-up vector when
        // the device is almost edge-on and the projection becomes singular.
        var axisXx = 1 - normalX * normalX;
        var axisXy = -normalX * normalY;
        var axisXz = -normalX * normalZ;
        var axisXLength = Math.hypot(axisXx, axisXy, axisXz);
        if (axisXLength < 0.001) {
            axisXx = -normalY * normalX;
            axisXy = 1 - normalY * normalY;
            axisXz = -normalY * normalZ;
            axisXLength = Math.hypot(axisXx, axisXy, axisXz) || 1;
        }
        axisXx /= axisXLength;
        axisXy /= axisXLength;
        axisXz /= axisXLength;

        // axisY = normal x axisX. At rest this is the screen-up direction.
        var axisYx = normalY * axisXz - normalZ * axisXy;
        var axisYy = normalZ * axisXx - normalX * axisXz;
        var axisYz = normalX * axisXy - normalY * axisXx;

        // Express the device camera's forward and up directions in the
        // fixed tabletop world used by dice-box-threejs.
        var cameraX = axisXz;
        var cameraY = axisYz;
        var cameraZ = normalZ;
        var upX = axisXy;
        var upY = axisYy;
        var upZ = normalY;

        // The felt and wooden frame are HTML layers behind the transparent
        // WebGL canvas. Keep both pitch and roll aligned with the camera so
        // the empty tray and the dice share the same real-world horizon.
        if (sceneRoot) {
            var pitch = Math.atan2(cameraY, cameraZ) * 180 / Math.PI;
            var roll = Math.atan2(cameraX, cameraZ) * 180 / Math.PI;
            sceneRoot.style.setProperty("--table-tilt-x", roll + "deg");
            sceneRoot.style.setProperty("--table-tilt-y", pitch + "deg");
        }

        box.camera.position.set(cameraX * distance, cameraY * distance, cameraZ * distance);
        box.camera.up.set(upX, upY, upZ);
        box.camera.lookAt(0, 0, 0);
        if (typeof box.camera.updateProjectionMatrix === "function") {
            box.camera.updateProjectionMatrix();
        }
        if (box.renderer && box.scene) {
            box.renderer.render(box.scene, box.camera);
        }
    }

    function scheduleDeviceHorizonApply() {
        if (deviceHorizonResizeTimer) clearTimeout(deviceHorizonResizeTimer);
        deviceHorizonResizeTimer = setTimeout(function () {
            deviceHorizonResizeTimer = null;
            applyDeviceHorizon();
        }, 32);
    }

    function notify(method, value) {
        if (window.AndroidDiceBridge && typeof window.AndroidDiceBridge[method] === "function") {
            if (typeof value === "undefined") {
                window.AndroidDiceBridge[method]();
            } else {
                window.AndroidDiceBridge[method](value);
            }
        }
    }

    function finishRoll(requestId) {
        if (requestId <= 0 || requestId !== currentRequestId ||
            lastFinishedRequestId === requestId) {
            return;
        }
        lastFinishedRequestId = requestId;
        notify("onRollFinished", requestId);
        wakeDiceForMotion(true);
    }

    function moveTowardTiltTarget() {
        var targetX = tilt.enabled ? tilt.targetX : 0;
        var targetY = tilt.enabled ? tilt.targetY : 0;
        var response = Math.min(0.86, TILT_RESPONSE * tilt.sensitivity);
        tilt.x += (targetX - tilt.x) * response;
        tilt.y += (targetY - tilt.y) * response;

        if (Math.abs(targetX - tilt.x) < 0.0005) tilt.x = targetX;
        if (Math.abs(targetY - tilt.y) < 0.0005) tilt.y = targetY;
    }

    function physicalTiltComponent(value) {
        var magnitude = Math.abs(value);
        if (magnitude <= TILT_DEADZONE) return 0;
        var normalized = (magnitude - TILT_DEADZONE) / (1 - TILT_DEADZONE);
        return (value < 0 ? -1 : 1) * normalized;
    }

    function applyGravity() {
        if (!box || !box.world || !box.world.gravity) return;

        moveTowardTiltTarget();

        // Sensor x/y are the horizontal gravity components. Using the full
        // component of g makes a larger physical tilt produce larger linear
        // acceleration instead of a fixed-speed slide.
        var sensitivity = Math.max(0.5, tilt.sensitivity);
        // The projection uses the opposite screen-to-table direction. Apply
        // the same basis to the physical world so dice roll toward the tray's
        // visual low side instead of appearing to climb uphill.
        var x = tilt.enabled ? -physicalTiltComponent(tilt.x) * BASE_GRAVITY *
            TILT_GRAVITY_SCALE * sensitivity : 0;
        var y = tilt.enabled ? -physicalTiltComponent(tilt.y) * BASE_GRAVITY *
            TILT_GRAVITY_SCALE * sensitivity : 0;
        var shakeX = tilt.enabled ? -tilt.shakeX * SHAKE_GRAVITY * sensitivity : 0;
        var shakeY = tilt.enabled ? -tilt.shakeY * SHAKE_GRAVITY * sensitivity : 0;
        var shakeZ = tilt.enabled ? tilt.shakeZ * SHAKE_GRAVITY * 0.45 * sensitivity : 0;
        box.world.gravity.set(x + shakeX, y + shakeY, -BASE_GRAVITY + shakeZ);
    }

    function syncDiceToBodies() {
        if (!box || !box.scene || !box.diceList || !box.renderer) return false;

        var moving = hasMotionInput();
        // dice-box adds the same die object to both scene and diceList. Use
        // the smaller, authoritative list instead of scanning lights, table
        // meshes and other scene nodes on every physics frame.
        for (var i = 0; i < box.diceList.length; i++) {
            var die = box.diceList[i];
            var body = die && die.body;
            if (!die || !body) continue;
            die.position.copy(body.position);
            die.quaternion.copy(body.quaternion);
            if (body.velocity.length() > 5 || body.angularVelocity.length() > 5) {
                moving = true;
            }
        }

        box.renderer.render(box.scene, box.camera);
        return moving;
    }

    function hasMotionInput() {
        if (!tilt.enabled) return false;
        var tiltMagnitude = Math.hypot(tilt.targetX, tilt.targetY);
        var shakeMagnitude = Math.sqrt(
            tilt.shakeX * tilt.shakeX +
            tilt.shakeY * tilt.shakeY +
            tilt.shakeZ * tilt.shakeZ
        );
        var sensitivity = Math.max(0.5, tilt.sensitivity);
        return tiltMagnitude >= TILT_WAKE_THRESHOLD / sensitivity ||
            shakeMagnitude >= SHAKE_WAKE_THRESHOLD / sensitivity;
    }

    function wakeMotionBodies() {
        if (!box || !box.diceList.length || box.rolling) return;

        for (var i = 0; i < box.diceList.length; i++) {
            var body = box.diceList[i] && box.diceList[i].body;
            if (!body) continue;
            // dice-box makes settled dice kinematic after a throw. Motion input
            // must turn them back into dynamic Cannon bodies before stepping.
            body.type = 1;
            body.linearDamping = 0.035;
            body.angularDamping = 0.06;
            if (typeof body.wakeUp === "function") body.wakeUp();
        }
    }

    function startTiltLoop() {
        if (tiltLoopActive || !box) return;
        tiltLoopActive = true;
        var lastTime = performance.now();
        var stillFrames = 0;

        function frame(now) {
            if (!tiltLoopActive || !box || !tilt.enabled || !box.diceList.length) {
                tiltLoopActive = false;
                return;
            }

            var delta = Math.min((now - lastTime) / 1000, 0.05);
            lastTime = now;
            applyGravity();

            // dice-box-threejs owns the render/step loop while a throw is in
            // progress. Do not double-step Cannon, but keep this loop alive so
            // the latest phone acceleration is ready for the next frame.
            if (box.rolling) {
                stillFrames = 0;
                requestAnimationFrame(frame);
                return;
            }

            if (hasMotionInput()) wakeMotionBodies();
            box.world.step(1 / 60, delta, 3);
            var moving = syncDiceToBodies();
            stillFrames = moving ? 0 : stillFrames + 1;
            if (stillFrames > 30) {
                tiltLoopActive = false;
                return;
            }
            requestAnimationFrame(frame);
        }

        requestAnimationFrame(frame);
    }

    function wakeDiceForMotion(force) {
        if (!box || !tilt.enabled || !box.diceList.length) return;
        if (!force && !hasMotionInput()) return;
        wakeMotionBodies();
        startTiltLoop();
    }

    function configureD6Pips() {
        if (!box || !box.DiceFactory || !box.DiceFactory.constructor) return;

        // dice-box-threejs already ships a tested "dpip" six-sided definition.
        // Reuse it for ordinary d6 notation so Kotlin can keep using numeric
        // results while the physical faces render as pip layouts.
        var registry = box.DiceFactory.constructor.dice;
        if (!registry || typeof box.DiceFactory.get !== "function") return;
        var pipDefinition = registry.dpip || box.DiceFactory.get("dpip");
        if (!pipDefinition) return;
        registry.d6 = pipDefinition;
        box.DiceFactory.materials_cache = {};
    }

    function dispatchPendingRoll() {
        if (!sceneReady || !box || !pendingRoll) {
            return;
        }

        var payload = pendingRoll;
        pendingRoll = null;
        var requestId = Number(payload.id || 0);
        currentRequestId = requestId;
        lastFinishedRequestId = -1;
        applyGravity();

        // dice-box-threejs uses Cannon physics for gravity, die-to-die contacts,
        // wall contacts and sleeping. The @ suffix keeps the Kotlin result card
        // and the physically rendered top faces in sync.
        var notation = payload.results.length + "d" + payload.sides + "@" +
            payload.results.join(",");
        console.info("[DiceScene] physical roll", notation);

        Promise.resolve(box.roll(notation))
            .then(function () {
                finishRoll(requestId);
            })
            .catch(function (error) {
                console.error("[DiceScene] roll failed", error);
                notify("onError");
            });
    }

    function initialize() {
        var DiceBox = window["dice-box-threejs"];
        if (!DiceBox || !container) {
            throw new Error("dice-box-threejs bundle or scene container was not loaded");
        }

        box = new DiceBox("#scene-container", {
            assetPath: "./",
            sounds: false,
            shadows: true,
            theme_surface: "green-felt",
            theme_colorset: "white",
            theme_texture: "",
            theme_material: "plastic",
            gravity_multiplier: 400,
            light_intensity: 0.75,
            strength: 1.0
        });

        return box.initialize().then(function () {
            configureD6Pips();
            applyGravity();
            applyDeviceHorizon();
        });
    }

    function roll(payload) {
        if (!payload || !Array.isArray(payload.results) || payload.results.length === 0 ||
            !payload.sides) {
            return;
        }
        pendingRoll = payload;
        dispatchPendingRoll();
    }

    function clear() {
        pendingRoll = null;
        currentRequestId = 0;
        lastFinishedRequestId = -1;
        tiltLoopActive = false;

        if (!box) return;
        box.rolling = false;
        if (typeof box.clearDice === "function") {
            box.clearDice();
        }
    }

    function setMotion(payload) {
        if (!payload) return;
        tilt.enabled = payload.enabled === true;
        tilt.targetX = Number(payload.tiltX || 0);
        tilt.targetY = Number(payload.tiltY || 0);
        var normalZ = Number(payload.tiltZ);
        tilt.normalZ = isFinite(normalZ) ? clamp(normalZ, 0, 1) : 1;
        tilt.shakeX = Number(payload.shakeX || 0);
        tilt.shakeY = Number(payload.shakeY || 0);
        tilt.shakeZ = Number(payload.shakeZ || 0);
        tilt.sensitivity = Math.max(0.5, Math.min(3.0, Number(payload.sensitivity || 2.2)));
        applyDeviceHorizon();
        applyGravity();

        // Starting the loop for every sensor payload is intentional. The
        // payload may be a small acceleration change below the wake threshold,
        // but it still has to be consumed by the active physical simulation.
        if (tilt.enabled && box && box.diceList.length) startTiltLoop();
        wakeDiceForMotion(false);
    }

    if (container) {
        container.addEventListener("pointerdown", function (event) {
            pointerStart = { x: event.clientX, y: event.clientY };
        });
        container.addEventListener("pointerup", function (event) {
            if (!pointerStart) return;
            var dx = event.clientX - pointerStart.x;
            var dy = event.clientY - pointerStart.y;
            pointerStart = null;
            if (Math.hypot(dx, dy) <= 12 && box) {
                notify("onSceneTap");
            }
        });
        container.addEventListener("pointercancel", function () {
            pointerStart = null;
        });
    }

    window.DiceScene = {
        roll: roll,
        clear: clear,
        setMotion: setMotion
    };

    window.addEventListener("resize", scheduleDeviceHorizonApply);

    // Start through a resolved promise so synchronous constructor/WebGL errors
    // are routed to the Android error callback as well.
    Promise.resolve()
        .then(initialize)
        .then(function () {
            sceneReady = true;
            notify("onReady");
            dispatchPendingRoll();
        })
        .catch(function (error) {
            console.error("[DiceScene] initialization failed", error);
            notify("onError");
        });
})();
