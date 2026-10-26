# Launcher estilo Apple TV (TCL 32S5400AF, Android TV 11)

Launcher nativo em Java, sem dependências, sem cobrança, leve para 1 GB de RAM.
Fundo preto, dock com faixa arredondada, grade de apps logo abaixo, um único
destaque de vidro que desliza entre os tiles, tile em foco maior com o nome,
nome grande do app do dock com brilho ambiente (cor do banner), pílulas
Entradas / Wi-Fi / Configurações no canto superior direito, saudação, relógio e
data no canto superior esquerdo (fonte Outfit, licença SIL OFL).

## Como obter o APK (sem instalar nada no PC)

1. Crie uma conta grátis em github.com e um repositório novo (privado serve).
2. Descompacte este ZIP. No repositório: **Add file > Upload files** e arraste
   **o conteúdo** da pasta (inclusive a pasta oculta `.github`). Se a `.github`
   não subir, crie o arquivo à mão: **Add file > Create new file**, digite o nome
   `.github/workflows/build.yml` e cole o conteúdo do arquivo de mesmo nome.
3. Aba **Actions**: o fluxo "Build APK" roda sozinho (uns 3 a 6 minutos). Se não
   rodar, abra-o e clique **Run workflow**.
4. Ao terminar (verde), abra a execução, role até **Artifacts** e baixe
   `launcher-apk`. Extraia o ZIP: o arquivo é `app-debug.apk`.
5. Se der vermelho, copie o texto do erro do passo que falhou e me mande.

## Instalar na TV (PowerShell, com a TV ligada e a depuração ativa)

    .\ferramentas\instalar.ps1 -Ip 192.168.100.12 -Apk "$HOME\Downloads\app-debug.apk"

O script instala, concede a permissão do nome do Wi-Fi e define a Home padrão.
Depois aperte Home no controle e confirme:

    adb -s 192.168.100.12:5555 shell dumpsys activity activities | Select-String mFocusedApp

Tem que aparecer `br.tvlauncher.home`. O launcher original do Google
**continua ativado** de propósito (rede de segurança).

Captura de tela da TV (sem corromper o PNG no PowerShell):

    adb -s 192.168.100.12:5555 shell screencap -p /sdcard/tela.png
    adb -s 192.168.100.12:5555 pull /sdcard/tela.png .

## Voltar atrás

    .\ferramentas\restaurar-launcher.ps1 -Ip 192.168.100.12

## Personalizar

Edite `app/src/main/res/values/config.xml`:
- `columns`: 4 a 7 (colunas da grade e slots do dock);
- `dock_apps`: pacotes do dock, em ordem (os não instalados são ignorados).

Depois faça commit no GitHub, baixe o novo APK e rode `instalar.ps1` de novo.
O fundo é preto puro, sem papel de parede.

## Teclas

Cima/Baixo andam uma fileira; Cima no dock vai para a barra superior; as pontas
da barra superior não escorregam para os tiles; OK abre; Voltar leva ao dock.
O launcher lembra o último tile focado.

## Limites conhecidos

- Os botões de app do controle (Netflix, YouTube...) e Input/Configurações são
  tratados pelo firmware e, em geral, continuam funcionando. Se algum não
  responder, me diga qual.
- "TV ao vivo" no menu Entradas abre o app `com.tcl.tv`; isso precisa ser
  testado na sua TV.
- Compilado sem acesso ao SDK Android por quem escreveu; a primeira compilação no
  GitHub pode revelar um erro de build a corrigir.
