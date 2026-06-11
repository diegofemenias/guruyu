<?php

declare(strict_types=1);

require_once __DIR__ . '/../../src/Database.php';
require_once __DIR__ . '/../../src/Auth.php';
require_once __DIR__ . '/../../src/helpers.php';

Auth::requireAdmin();
$pdo = Database::connection();
$config = require __DIR__ . '/../../config/config.php';
$trackPoints = (int) ($config['track_points'] ?? 50);

$deviceUuid = trim((string) ($_GET['uuid'] ?? ''));
if ($deviceUuid !== '' && !preg_match('/^[0-9a-f-]{36}$/i', $deviceUuid)) {
    $deviceUuid = '';
}

$locations = recentLocationsByDevice($pdo, 300, $deviceUuid !== '' ? $deviceUuid : null);
$tracks = deviceTracks($pdo, $trackPoints, $deviceUuid !== '' ? $deviceUuid : null, false);

$grouped = [];
foreach ($locations as $row) {
    $grouped[$row['device_uuid']][] = $row;
}

$tracksByUuid = [];
foreach ($tracks as $track) {
    $tracksByUuid[$track['uuid']] = $track;
}

$devices = $pdo->query('SELECT uuid, display_name FROM devices ORDER BY display_name ASC')->fetchAll();
$sectionUuids = array_unique(array_merge(array_keys($tracksByUuid), array_keys($grouped)));
usort($sectionUuids, static function (string $a, string $b) use ($tracksByUuid, $grouped): int {
    $nameA = $tracksByUuid[$a]['display_name'] ?? ($grouped[$a][0]['display_name'] ?? $a);
    $nameB = $tracksByUuid[$b]['display_name'] ?? ($grouped[$b][0]['display_name'] ?? $b);
    return strcasecmp($nameA, $nameB);
});
?>
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Guruyu Admin - Historial de ubicaciones</title>
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin="">
    <style>
        * { box-sizing: border-box; }
        body { font-family: system-ui, sans-serif; background: #f4f6f8; margin: 0; color: #111827; }
        header { background: #111827; color: #fff; padding: 1rem 1.5rem; display: flex; justify-content: space-between; align-items: center; gap: 1rem; flex-wrap: wrap; }
        header nav a { color: #fff; text-decoration: none; margin-left: 1rem; }
        main { padding: 1.5rem; max-width: 1100px; margin: 0 auto; }
        .filter { background: #fff; padding: 1rem; border-radius: 12px; margin-bottom: 1.5rem; box-shadow: 0 4px 16px rgba(0,0,0,.06); }
        .filter form { display: flex; gap: .75rem; flex-wrap: wrap; align-items: center; }
        select, button { padding: .5rem .75rem; border-radius: 8px; border: 1px solid #d1d5db; }
        button { background: #2563eb; color: #fff; border: 0; cursor: pointer; font-weight: 600; }
        section { margin-bottom: 2rem; }
        h2 { margin: 0 0 .75rem; font-size: 1.1rem; }
        .track-map { height: 320px; border-radius: 12px; margin-bottom: 1rem; box-shadow: 0 4px 16px rgba(0,0,0,.06); }
        table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 16px rgba(0,0,0,.06); }
        th, td { padding: .75rem 1rem; border-bottom: 1px solid #e5e7eb; text-align: left; vertical-align: top; }
        th { background: #f9fafb; font-size: .85rem; text-transform: uppercase; letter-spacing: .03em; }
        a { color: #2563eb; }
        .muted { color: #6b7280; font-size: .9rem; }
    </style>
</head>
<body>
    <header>
        <strong>Guruyu - Historial de ubicaciones</strong>
        <nav>
            <a href="/admin/index.php">Dispositivos</a>
            <a href="/admin/logout.php">Cerrar sesión</a>
        </nav>
    </header>
    <main>
        <p class="muted">Últimas 300 ubicaciones por dispositivo. Recorrido en mapa con las últimas <?= (int) $trackPoints ?> posiciones.</p>

        <div class="filter">
            <form method="get">
                <label for="uuid">Filtrar por dispositivo</label>
                <select id="uuid" name="uuid">
                    <option value="">Todos los dispositivos</option>
                    <?php foreach ($devices as $device): ?>
                        <option value="<?= htmlspecialchars($device['uuid']) ?>" <?= $deviceUuid === $device['uuid'] ? 'selected' : '' ?>>
                            <?= htmlspecialchars($device['display_name']) ?>
                        </option>
                    <?php endforeach; ?>
                </select>
                <button type="submit">Filtrar</button>
                <?php if ($deviceUuid !== ''): ?>
                    <a href="/admin/history.php">Ver todos</a>
                <?php endif; ?>
            </form>
        </div>

        <?php if (!$sectionUuids): ?>
            <p class="muted">No hay ubicaciones registradas.</p>
        <?php endif; ?>

        <?php foreach ($sectionUuids as $uuid): ?>
            <?php
            $track = $tracksByUuid[$uuid] ?? null;
            $rows = $grouped[$uuid] ?? [];
            $deviceName = $track['display_name'] ?? ($rows[0]['display_name'] ?? $uuid);
            ?>
            <section>
                <h2><?= htmlspecialchars($deviceName) ?> (<?= count($rows) ?> registros)</h2>

                <?php if ($track && count($track['points']) >= 2): ?>
                    <div id="map-<?= htmlspecialchars($uuid) ?>" class="track-map"></div>
                <?php elseif ($track && count($track['points']) === 1): ?>
                    <p class="muted">Se necesitan al menos 2 puntos para dibujar el recorrido.</p>
                <?php endif; ?>

                <?php if ($rows): ?>
                <table>
                    <thead>
                        <tr>
                            <th>Nombre</th>
                            <th>Ubicación</th>
                            <th>Fecha</th>
                        </tr>
                    </thead>
                    <tbody>
                        <?php foreach ($rows as $row): ?>
                            <?php
                            $lat = (float) $row['latitude'];
                            $lon = (float) $row['longitude'];
                            $mapUrl = openStreetMapUrl($lat, $lon);
                            $label = sprintf('%.6f, %.6f', $lat, $lon);
                            ?>
                            <tr>
                                <td><?= htmlspecialchars($deviceName) ?></td>
                                <td>
                                    <a href="<?= htmlspecialchars($mapUrl) ?>" target="_blank" rel="noopener noreferrer">
                                        <?= htmlspecialchars($label) ?>
                                    </a>
                                </td>
                                <td><?= htmlspecialchars((string) $row['reported_at']) ?></td>
                            </tr>
                        <?php endforeach; ?>
                    </tbody>
                </table>
                <?php else: ?>
                    <p class="muted">Sin registros en la tabla.</p>
                <?php endif; ?>
            </section>
        <?php endforeach; ?>
    </main>

    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
    <script>
        const tracks = <?= json_encode(array_values($tracksByUuid), JSON_UNESCAPED_UNICODE) ?>;
        const trackColors = ['#2563eb', '#16a34a', '#dc2626', '#9333ea', '#ea580c', '#0891b2', '#be185d'];

        tracks.forEach((track, index) => {
            if (!track.points || track.points.length < 2) {
                return;
            }

            const mapEl = document.getElementById('map-' + track.uuid);
            if (!mapEl) {
                return;
            }

            const latLngs = track.points.map((point) => [point.latitude, point.longitude]);
            const color = trackColors[index % trackColors.length];
            const map = L.map(mapEl).setView(latLngs[latLngs.length - 1], 14);

            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                attribution: '&copy; OpenStreetMap contributors',
            }).addTo(map);

            L.polyline(latLngs, { color, weight: 4, opacity: 0.85 }).addTo(map);

            const start = track.points[0];
            const end = track.points[track.points.length - 1];
            L.circleMarker(latLngs[0], { radius: 6, color: '#6b7280', fillColor: '#9ca3af', fillOpacity: 1 })
                .bindPopup('Inicio<br>' + start.reported_at)
                .addTo(map);
            L.circleMarker(latLngs[latLngs.length - 1], { radius: 7, color, fillColor: color, fillOpacity: 1 })
                .bindPopup('Última posición<br>' + end.reported_at)
                .addTo(map);

            map.fitBounds(L.polyline(latLngs).getBounds(), { padding: [24, 24] });
        });
    </script>
</body>
</html>
