# Debloat da TV SEMP TCL 32S5400AF (Android TV 11)

Feito com `pm disable-user --user 0`: nada foi desinstalado, tudo se reativa.
Resolução real da TV: 1280x720, densidade 213. Backup em `%USERPROFILE%\tv-backup`.

## O que foi desativado (14 pacotes)

| Pacote | O que era |
|---|---|
| com.tcl.waterfall.overseas | Portal de conteúdo da TCL (TCL Channel) |
| com.tcl.dashboard | Central de mensagens e promoções da TCL |
| com.google.android.backdrop | Protetor de tela do Google |
| com.google.android.feedback | Envio de relatórios de erro |
| com.tcl.guard | Gerenciador/limpador da TCL |
| com.tcl.esticker | Selo de demonstração de loja |
| com.tcl.ui_mediaCenter | Navegador de mídia USB |
| com.tcl.audioplayer / imageplayer / videoplayer | Players de áudio, imagem e vídeo de USB |
| com.google.android.play.games | Play Games |
| com.google.android.videos | Google TV / Play Filmes |
| tv.maxplayer.android | MaxPlayer |
| com.funplusplay.app | FunPlusPlay |

`au.com.stan.and` e `tv.wuaki.apptv` já vinham desativados antes: não reative.

## Mantido de propósito

Netflix, YouTube, Prime Video, Disney+, voz (katniss, assistant), Alexa
(com.tcl.smartalexa), Chromecast (mediashell), atualização do sistema
(com.tcl.ttvs), setupwraith (dono das redes Wi-Fi salvas: desativar tira a TV
do Wi-Fi), partnercustomizer (teclas do controle), launcher do Google,
FallbackHome e tudo de `com.android.*`, Play Store e Play Services.
Nunca abra pacotes com factory/fac/AT no nome.

## Reativar um app

    adb -s <ip>:5555 shell pm enable --user 0 <pacote>

Reativar tudo: rode `%USERPROFILE%\tv-backup\restaurar.ps1` com a TV conectada
(as linhas usam o IP salvo; se o IP mudou, troque no arquivo).

Observação: o firmware reativou `com.tcl.usercenter` (conta TCL) sozinho uma vez.
Se algum pacote voltar, deixe como está ou desative de novo.

## O IP da TV muda

A TV usa DHCP. Confira em Configurações > Rede e reserve o IP no roteador pelo
MAC da TV (d0:12:55:c0:54:4f) para não perder a conexão.

## DNS bloqueador de anúncios (opcional)

    adb -s <ip>:5555 shell settings put global private_dns_specifier dns.adguard-dns.com
    adb -s <ip>:5555 shell settings put global private_dns_mode hostname

Não bloqueia anúncios do YouTube. Desfazer:

    adb -s <ip>:5555 shell settings put global private_dns_mode opportunistic

Se o seu roteador já filtra por domínio (Pi-hole, AdGuard Home), o DNS Privado
passa por fora desse filtro.

## Launcher estilo Apple TV

Não instalado: exige compilar um APK com o Android SDK. A TV (SDK 30) está
acima do mínimo, e a tela inicial só é reivindicada pelo launcher do Google e
pelo FallbackHome do sistema.

## Fechar o ADB (importante)

A porta 5555 é texto puro: qualquer aparelho na rede consegue um shell se ela
ficar aberta. Na TV: desligue Depuração USB e Depuração de rede/ADB em Opções do
desenvolvedor, use "Revogar autorizações de depuração USB", reinicie, e no PC:

    adb connect <ip>:5555

Só "Nenhuma conexão pôde ser feita porque a máquina de destino as recusou
ativamente (10061)" prova que fechou. Timeout (10060) não prova nada.
