# Instala o launcher na TV, um comando adb por chamada.
# Uso:  .\instalar.ps1 -Ip 192.168.100.12 -Apk "$HOME\Downloads\app-debug.apk"
param(
    [Parameter(Mandatory = $true)][string]$Ip,
    [string]$Apk = "$HOME\Downloads\app-debug.apk"
)
$T = "${Ip}:5555"
$Pkg = "br.tvlauncher.home"

if (-not (Test-Path $Apk)) { Write-Host "APK nao encontrado: $Apk"; exit 1 }

adb connect $T
adb devices

Write-Host "`n== Tela ANTES (anote: se algo ficar ampliado/cortado, compare) =="
adb -s $T shell wm size
adb -s $T shell wm density
adb -s $T shell settings get secure display_density_forced

Write-Host "`n== 1. Enviar e instalar =="
adb -s $T push $Apk /data/local/tmp/launcher.apk
adb -s $T shell pm install -r /data/local/tmp/launcher.apk
adb -s $T shell pm list packages $Pkg

Write-Host "`n== 2. Permissao para mostrar o nome do Wi-Fi =="
adb -s $T shell pm grant $Pkg android.permission.ACCESS_FINE_LOCATION

Write-Host "`n== 3. Quem reivindica a tela inicial (HOME) =="
adb -s $T shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME

Write-Host "`n== 4. Definir como Home padrao =="
adb -s $T shell cmd package set-home-activity "$Pkg/.MainActivity"

Write-Host "`nAGORA: aperte o botao Home do controle e rode:"
Write-Host "  adb -s $T shell dumpsys activity activities | Select-String mFocusedApp"
Write-Host "Deve aparecer $Pkg. Se aparecer com.google.android.tvlauncher, a troca NAO funcionou."
