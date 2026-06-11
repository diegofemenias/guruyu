<?php

return [
    'db' => [
        'host' => '127.0.0.1',
        'port' => 3306,
        'name' => 'guruyu',
        'user' => 'guruyu',
        'pass' => 'guruyu_secret',
        'charset' => 'utf8mb4',
    ],
    'api_key' => 'cambia-esta-api-key-en-produccion',
    'admin' => [
        'session_name' => 'guruyu_admin',
    ],
    // Minutos sin reporte para considerar ubicación obsoleta (mostrar en gris)
    'stale_minutes' => 2,
    // Días de retención de ubicaciones en location_reports
    'retention_days' => 30,
    // Puntos por dispositivo para dibujar el recorrido en el mapa
    'track_points' => 50,
];
