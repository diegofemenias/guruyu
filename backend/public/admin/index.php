<?php

declare(strict_types=1);

require_once __DIR__ . '/../../src/Database.php';
require_once __DIR__ . '/../../src/Auth.php';

Auth::requireAdmin();
$pdo = Database::connection();

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $uuid = trim((string) ($_POST['uuid'] ?? ''));
    $action = $_POST['action'] ?? '';
    $displayName = trim((string) ($_POST['display_name'] ?? ''));

    if ($uuid !== '' && preg_match('/^[0-9a-f-]{36}$/i', $uuid)) {
        if ($action === 'toggle') {
            $stmt = $pdo->prepare('UPDATE devices SET is_enabled = NOT is_enabled WHERE uuid = ?');
            $stmt->execute([$uuid]);
        } elseif ($action === 'rename' && $displayName !== '') {
            $stmt = $pdo->prepare('UPDATE devices SET display_name = ? WHERE uuid = ?');
            $stmt->execute([$displayName, $uuid]);
        }
    }

    header('Location: /admin/index.php');
    exit;
}

$devices = $pdo->query(<<<SQL
    SELECT
        d.uuid,
        d.display_name,
        d.is_enabled,
        d.created_at,
        lr.latitude,
        lr.longitude,
        lr.reported_at
    FROM devices d
    LEFT JOIN location_reports lr ON lr.id = (
        SELECT id FROM location_reports WHERE device_uuid = d.uuid ORDER BY reported_at DESC LIMIT 1
    )
    ORDER BY d.created_at DESC
SQL)->fetchAll();
?>
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Guruyu Admin - Dispositivos</title>
    <style>
        * { box-sizing: border-box; }
        body { font-family: system-ui, sans-serif; background: #f4f6f8; margin: 0; color: #111827; }
        header { background: #111827; color: #fff; padding: 1rem 1.5rem; display: flex; justify-content: space-between; align-items: center; }
        main { padding: 1.5rem; max-width: 1100px; margin: 0 auto; }
        table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 16px rgba(0,0,0,.06); }
        th, td { padding: .85rem 1rem; border-bottom: 1px solid #e5e7eb; text-align: left; vertical-align: top; }
        th { background: #f9fafb; font-size: .85rem; text-transform: uppercase; letter-spacing: .03em; }
        .badge { display: inline-block; padding: .2rem .55rem; border-radius: 999px; font-size: .8rem; font-weight: 600; }
        .on { background: #dcfce7; color: #166534; }
        .off { background: #fee2e2; color: #991b1b; }
        button, .btn { border: 0; border-radius: 8px; padding: .45rem .8rem; cursor: pointer; font-weight: 600; }
        .enable { background: #16a34a; color: #fff; }
        .disable { background: #dc2626; color: #fff; }
        .save { background: #2563eb; color: #fff; }
        .logout { color: #fff; text-decoration: none; }
        code { font-size: .85rem; word-break: break-all; }
        form.inline { display: flex; gap: .5rem; flex-wrap: wrap; align-items: center; }
        input[type=text] { padding: .45rem .6rem; border: 1px solid #d1d5db; border-radius: 8px; min-width: 160px; }
        .muted { color: #6b7280; font-size: .9rem; }
    </style>
</head>
<body>
    <header>
        <strong>Guruyu - Panel administrativo</strong>
        <nav style="display:flex;gap:1rem;">
            <a class="logout" href="/admin/history.php">Historial de ubicaciones</a>
            <a class="logout" href="/admin/logout.php">Cerrar sesión</a>
        </nav>
    </header>
    <main>
        <p class="muted">Activa o desactiva dispositivos por UUID. Los nuevos aparecen al intentar reportar ubicación.</p>
        <table>
            <thead>
                <tr>
                    <th>UUID</th>
                    <th>Nombre</th>
                    <th>Estado</th>
                    <th>Última ubicación</th>
                    <th>Acciones</th>
                </tr>
            </thead>
            <tbody>
            <?php if (!$devices): ?>
                <tr><td colspan="5">No hay dispositivos registrados aún.</td></tr>
            <?php endif; ?>
            <?php foreach ($devices as $device): ?>
                <tr>
                    <td><code><?= htmlspecialchars($device['uuid']) ?></code></td>
                    <td>
                        <form class="inline" method="post">
                            <input type="hidden" name="uuid" value="<?= htmlspecialchars($device['uuid']) ?>">
                            <input type="hidden" name="action" value="rename">
                            <input type="text" name="display_name" value="<?= htmlspecialchars($device['display_name']) ?>" required>
                            <button class="save" type="submit">Guardar</button>
                        </form>
                    </td>
                    <td>
                        <span class="badge <?= $device['is_enabled'] ? 'on' : 'off' ?>">
                            <?= $device['is_enabled'] ? 'Habilitado' : 'Deshabilitado' ?>
                        </span>
                    </td>
                    <td>
                        <?php if ($device['latitude'] !== null): ?>
                            <?= htmlspecialchars((string) $device['latitude']) ?>,
                            <?= htmlspecialchars((string) $device['longitude']) ?><br>
                            <span class="muted"><?= htmlspecialchars((string) $device['reported_at']) ?></span>
                        <?php else: ?>
                            <span class="muted">Sin reportes</span>
                        <?php endif; ?>
                    </td>
                    <td>
                        <form method="post" style="margin-bottom:.5rem;">
                            <input type="hidden" name="uuid" value="<?= htmlspecialchars($device['uuid']) ?>">
                            <input type="hidden" name="action" value="toggle">
                            <button class="<?= $device['is_enabled'] ? 'disable' : 'enable' ?>" type="submit">
                                <?= $device['is_enabled'] ? 'Deshabilitar' : 'Habilitar' ?>
                            </button>
                        </form>
                        <a href="/admin/history.php?uuid=<?= urlencode($device['uuid']) ?>">Ver historial</a>
                    </td>
                </tr>
            <?php endforeach; ?>
            </tbody>
        </table>
    </main>
</body>
</html>
