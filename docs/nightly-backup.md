# Nightly backup / Respaldo nocturno

> 🇪🇸 [Español](#español) · 🇬🇧 [English](#english)

---

## Español

`scripts/nightly-backup.sh` (Linux/macOS) y `scripts/nightly-backup.ps1` (Windows) suben cada noche el trabajo sin terminar a la rama **`wip/nightly`**.

> ⚠️ `wip/nightly` es **solo un respaldo**. La historia real del proyecto está en las ramas `feature/*` y en sus pull requests hacia `develop`. Nunca se une a `develop` ni a `main`.

### Qué hace

1. Toma una foto del directorio de trabajo: archivos modificados, en *staging* y nuevos, respetando `.gitignore`.
2. Si no hay cambios sin subir, o son iguales al último respaldo, **no hace nada**.
3. Antes de subir, revisa que no haya archivos sensibles (`.env`, keystores, llaves, `google-services.json`…) ni secretos en el contenido (llaves privadas, `sk_live_…`, tokens de GitHub o AWS). Si encuentra alguno, **aborta sin subir nada** y lo registra en el log. En Windows además muestra una notificación.
4. Si todo está bien, crea el commit `chore(wip): nightly backup YYYY-MM-DD` encima del respaldo anterior y lo sube a `origin/wip/nightly`.

**No cambia** tu rama actual, tu *staging* ni tus archivos, y **nunca toca** `main`, `develop` ni `feature/*`.

Log: `~/.geops-backup/nightly-backup.log`

### Programarlo a las 11:30 p. m. (hora de Lima)

**Windows (Programador de tareas)**, en PowerShell:

```powershell
$repo = 'D:\Personal\geops-api'   # ajusta la ruta
$action = New-ScheduledTaskAction -Execute 'powershell.exe' `
  -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$repo\scripts\nightly-backup.ps1`""
$trigger = New-ScheduledTaskTrigger -Daily -At '23:30'
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -RunOnlyIfNetworkAvailable
Register-ScheduledTask -TaskName 'GeoPS nightly backup (geops-api)' -Action $action -Trigger $trigger -Settings $settings
```

`-StartWhenAvailable` lo ejecuta al encender la PC si a las 11:30 p. m. estaba apagada. La hora usa la zona horaria de Windows (Lima, UTC-5).

**Linux / macOS (cron)**: ejecuta `crontab -e` y agrega:

```cron
CRON_TZ=America/Lima
30 23 * * * /ruta/a/geops-api/scripts/nightly-backup.sh >/dev/null 2>&1
```

En macOS, cron ignora `CRON_TZ`. Si tu Mac no está en hora de Lima, ajusta la hora.

### Recuperar un respaldo

```bash
git fetch origin wip/nightly
git switch -c recover origin/wip/nightly   # o: git checkout origin/wip/nightly -- ruta/al/archivo
```

---

## English

`scripts/nightly-backup.sh` (Linux/macOS) and `scripts/nightly-backup.ps1` (Windows) push unfinished work every night to the **`wip/nightly`** branch.

> ⚠️ `wip/nightly` is **only a backup**. The real project history lives in the `feature/*` branches and their pull requests into `develop`. It is never merged into `develop` or `main`.

### What it does

1. Snapshots the working tree: modified, staged and new files, honoring `.gitignore`.
2. If there is nothing unpushed, or it matches the last backup, it **does nothing**.
3. Before pushing, it checks for sensitive files (`.env`, keystores, keys, `google-services.json`…) and secret-looking content (private keys, `sk_live_…`, GitHub or AWS tokens). If any are found, it **aborts without pushing** and writes it to the log. On Windows it also shows a notification.
4. Otherwise it commits `chore(wip): nightly backup YYYY-MM-DD` on top of the previous backup and pushes it to `origin/wip/nightly`.

It **does not change** your current branch, staging area or files, and **never touches** `main`, `develop` or `feature/*`.

Log: `~/.geops-backup/nightly-backup.log`

### Schedule it at 11:30 p.m. (Lima time)

**Windows (Task Scheduler)**, in PowerShell:

```powershell
$repo = 'D:\Personal\geops-api'   # adjust the path
$action = New-ScheduledTaskAction -Execute 'powershell.exe' `
  -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$repo\scripts\nightly-backup.ps1`""
$trigger = New-ScheduledTaskTrigger -Daily -At '23:30'
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -RunOnlyIfNetworkAvailable
Register-ScheduledTask -TaskName 'GeoPS nightly backup (geops-api)' -Action $action -Trigger $trigger -Settings $settings
```

`-StartWhenAvailable` runs it at the next startup if the PC was off at 11:30 p.m. The time follows the Windows time zone (Lima, UTC-5).

**Linux / macOS (cron)**: run `crontab -e` and add:

```cron
CRON_TZ=America/Lima
30 23 * * * /path/to/geops-api/scripts/nightly-backup.sh >/dev/null 2>&1
```

macOS cron ignores `CRON_TZ`. If your Mac is not on Lima time, adjust the hour.

### Restore a backup

```bash
git fetch origin wip/nightly
git switch -c recover origin/wip/nightly   # or: git checkout origin/wip/nightly -- path/to/file
```
