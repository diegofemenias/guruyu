<?php

declare(strict_types=1);

require_once __DIR__ . '/../../src/Database.php';
require_once __DIR__ . '/../../src/Auth.php';
require_once __DIR__ . '/../../src/helpers.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    jsonResponse(['error' => 'Método no permitido'], 405);
}

if (!Auth::validateApiKey()) {
    jsonResponse(['error' => 'API key inválida'], 401);
}

$body = readJsonBody();
$uuid = trim((string) ($body['device_uuid'] ?? ''));
$latitude = $body['latitude'] ?? null;
$longitude = $body['longitude'] ?? null;
$reportedAt = trim((string) ($body['reported_at'] ?? ''));

if ($uuid === '' || !preg_match('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i', $uuid)) {
    jsonResponse(['error' => 'device_uuid inválido'], 422);
}

if (!is_numeric($latitude) || !is_numeric($longitude)) {
    jsonResponse(['error' => 'Coordenadas inválidas'], 422);
}

if ($reportedAt === '' || !preg_match('/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/', $reportedAt)) {
    jsonResponse(['error' => 'reported_at debe ser YYYY-MM-DD HH:MM:SS'], 422);
}

$pdo = Database::connection();
ensureDeviceExists($pdo, $uuid);

$enabled = isDeviceEnabled($pdo, $uuid);
if (!$enabled) {
    jsonResponse([
        'success' => false,
        'enabled' => false,
        'message' => 'Dispositivo no habilitado',
    ], 403);
}

$stmt = $pdo->prepare(
    'INSERT INTO location_reports (device_uuid, latitude, longitude, reported_at) VALUES (?, ?, ?, ?)'
);
$stmt->execute([
    $uuid,
    round((float) $latitude, 8),
    round((float) $longitude, 8),
    $reportedAt,
]);

maybePurgeOldLocationReports($pdo);

jsonResponse([
    'success' => true,
    'enabled' => true,
    'message' => 'Ubicación registrada',
]);
