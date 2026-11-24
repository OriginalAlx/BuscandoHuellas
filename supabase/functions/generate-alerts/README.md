# generate-alerts — Edge Function

Genera alertas persistentes y envía notificaciones push cuando se inserta un
reporte en `public.reports`.

## 1. Requisitos

- Haber ejecutado `supabase/alerts.sql` en el SQL Editor.
- Tener una cuenta de servicio de Firebase con permiso para FCM.
- Supabase CLI instalado (`npm i -g supabase`).

## 2. Secretos de la función

En **Project Settings → Edge Functions → Secrets** (o con la CLI):

```bash
supabase secrets set \
  FCM_PROJECT_ID="tu-proyecto-firebase" \
  FCM_SERVICE_ACCOUNT_JSON="$(cat service-account.json)" \
  WEBHOOK_SECRET="un-secreto-largo-y-aleatorio"
```

`SUPABASE_URL` y `SUPABASE_SERVICE_ROLE_KEY` los inyecta Supabase
automáticamente.

La cuenta de servicio se descarga en **Firebase Console → Configuración del
proyecto → Cuentas de servicio → Generar nueva clave privada**.

> No subas `service-account.json` al repositorio.

## 3. Deploy

```bash
supabase functions deploy generate-alerts --no-verify-jwt
```

`--no-verify-jwt` permite autenticar la función con `WEBHOOK_SECRET` en lugar
del JWT de Supabase. Si prefieres la verificación JWT, elimina esa bandera y
configura el webhook con el `service_role` key.

## 4. Configurar el webhook

Según la versión del Dashboard, los Database Webhooks están en
**Integrations → Webhooks** (antes en **Database → Webhooks**). Si no aparecen,
usa directamente el trigger SQL de `supabase/webhook_trigger.sql`, que hace lo
mismo con `pg_net` + Vault.

Si lo creas desde el Dashboard:

| Campo | Valor |
| --- | --- |
| Name | `on_report_created` |
| Table | `public.reports` |
| Events | `Insert` |
| Type | `HTTP Request` (**no** `Supabase Edge Function`) |
| Method | `POST` |
| URL | `https://<project-ref>.supabase.co/functions/v1/generate-alerts` |
| HTTP header | `Authorization: Bearer <WEBHOOK_SECRET>` |

Se usa `HTTP Request` porque el tipo `Supabase Edge Function` no permite
controlar el header `Authorization`, y la función valida `WEBHOOK_SECRET`
(desplegada con `--no-verify-jwt`).

## 5. Prueba manual

```bash
curl -X POST \
  "https://<project-ref>.supabase.co/functions/v1/generate-alerts" \
  -H "Authorization: Bearer <WEBHOOK_SECRET>" \
  -H "Content-Type: application/json" \
  -d '{"record":{"id":"<report-uuid>","user_id":"<author-uuid>","pet_name":"Luna","description":"Beagle perdida","status":"LOST","latitude":-25.75,"longitude":-57.25}}'
```

Respuesta esperada:

```json
{ "report_id": "...", "inserted": 2, "sent": 2, "invalid_tokens": 0 }
```

## 6. Cómo funciona el filtrado

Por cada fila de `user_notification_tokens` con `notifications_enabled = true`:

1. Se descarta al autor del reporte.
2. Se comprueba la preferencia del estado (`notify_lost`, `notify_sighted` o
   `notify_found`).
3. Si `radius_km` no es nulo, se calcula la distancia Haversine entre la
   ubicación guardada del usuario (`latitude`/`longitude`) y la del reporte.
   Sin ubicación del usuario, no se envía cuando hay radio definido.
4. Se inserta la alerta y se envía un push **data-only** con
   `report_id`, `title`, `message` y `status`.

La app sincroniza `notifications_enabled`, `radius_km`, `notify_*` y la
ubicación en la fila del token cada vez que arranca y cuando el usuario cambia
la configuración. La columna nace en `false` para respetar el valor por defecto
de la app (notificaciones desactivadas).

### Coordenadas del reporte

La tabla `reports` guarda la ubicación en una columna PostGIS `location`, no en
columnas `latitude`/`longitude`. La función lee `reports_view`, que expone
`location_text` como WKT (`POINT(lon lat)`), y lo parsea. Si el webhook ya trae
`latitude`/`longitude` (por ejemplo, en una prueba manual), se usan tal cual.

Si el reporte no tiene coordenadas legibles y el usuario sí tiene ubicación,
solo se envía cuando `radius_km` es nulo (sin límite).

El push data-only es intencional: Android lo entrega a
`BuscandoHuellasFirebaseMessagingService`, que crea la notificación local con
el canal `reportes_cercanos` y el `PendingIntent` hacia `MainActivity` con el
`report_id`.

## 7. Tokens inválidos

Si FCM responde `404`, `UNREGISTERED`, `INVALID_ARGUMENT` o
`SENDER_ID_MISMATCH`, el token se elimina de `user_notification_tokens`.
