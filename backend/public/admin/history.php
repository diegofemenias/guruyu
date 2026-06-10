<?php

declare(strict_types=1);

require_once __DIR__ . '/../../src/Database.php';
require_once __DIR__ . '/../../src/Auth.php';
require_once __DIR__ . '/../../src/helpers.php';

Auth::requireAdmin();
$pdo = Database::connection();

$deviceUuid = trim((string) ($_GET['uuid'] ?? ''));
if ($deviceUuid !== '' && !preg_match('/^[0-9a-f-]{36}$/i', $deviceUuid)) {
    $deviceUuid = '';
}

$locations = recentLocationsByDevice($pdo, 300, $deviceUuid !== '' ? $deviceUuid : null);

$grouped = [];
foreach ($locations as $row) {
    $grouped[$row['display_name']][] = $row;
}

$devices = $pdo->query('SELECT uuid, display_name FROM devices ORDER BY display_name ASC')->fetchAll();
?>
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Guruyu Admin - Historial de ubicaciones</title>
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
        <p class="muted">Últimas 300 ubicaciones reportadas por dispositivo.</p>

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

        <?php if (!$grouped): ?>
            <p class="muted">No hay ubicaciones registradas.</p>
        <?php endif; ?>

        <?php foreach ($grouped as $deviceName => $rows): ?>
            <section>
                <h2><?= htmlspecialchars($deviceName) ?> (<?= count($rows) ?>)</h2>
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
            </section>
        <?php endforeach; ?>
    </main>
</body>
</html>
