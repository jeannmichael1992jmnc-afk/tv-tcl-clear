# Volta ao launcher original do Google e remove este launcher.
# Uso:  .\restaurar-launcher.ps1 -Ip 192.168.100.12
param([Parameter(Mandatory = $true)][string]$Ip)
$T = "${Ip}:5555"
adb connect $T
adb -s $T shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
adb -s $T shell pm uninstall br.tvlauncher.home
Write-Host "Pronto. Aperte Home no controle."
