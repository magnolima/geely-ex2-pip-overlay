# PiP Control

Aplicativo Android independente para ativar/desativar a correcao do PiP na
Geely IHU629G com a SystemUI Android 9 validada neste projeto. Nao depende
da instalacao ou execucao do Mica. O APK inclui o overlay assinado.

## Uso na central

1. Baixe/copie `dist/PiP-Control-1.0.0.apk` para a central e instale pelo gerenciador de arquivos.
2. Abra **PiP Control** e aguarde a verificacao automatica.
3. Toque em **Enable PiP**. O app instala o overlay, habilita e reinicia a SystemUI.
4. Para reverter, abra o app e toque em **Disable PiP**.

Nao ha campo de IP, dependencia de ADB ou conexao com servidor. Se o overlay
do kit de PC ja estiver instalado, o app usa o mesmo pacote e confere seu hash.
Uma versao diferente nao e substituida automaticamente. Desativar preserva o
APK do overlay; desinstalar apenas PiP Control nao desativa o overlay.

**Estado: APK compilado e verificado localmente. O fluxo completo deste novo
app ainda nao foi validado na central, que estava desconectada durante o
desenvolvimento.** O overlay embarcado e exatamente o que teve arraste e tela
cheia validados via ADB. Nao confundir essa validacao anterior com a do novo app.

## Tecnica e limites

O app usa assinatura de plataforma e `android.uid.systemui`, identidade
compartilhada com a SystemUI desta ROM. Solicita permissoes de instalacao,
alteracao de overlays e diagnostico. Usa PackageInstaller para instalar o APK
incorporado e IOverlayManager para habilitar/desabilitar somente
`com.geely.ex2.mica.pipoverlayprobe` no usuario 0. O processo da SystemUI e
identificado pelo nome e UID e reiniciado; o processo do instalador e separado.
O mecanismo depende de assinatura, permissoes, APIs internas e politica SELinux
da ROM. Nao usa `su`, nao tenta obter root e nao altera particoes de sistema.

Antes de habilitar, exige Android 9, feature de PiP, identidade/permissoes
compativeis, integridade do overlay e SHA-256 exato da SystemUI:
`0C9AB49B1CCF83133B3CBCAB1CC1D980E97C6472FE9D06F65E2BEF07D0737332`.
Disable PiP nao exige esse hash, para permitir reversao depois de atualizacoes.
A assinatura incorreta pode impedir a propria instalacao do app.

O PiP e nativo e pode beneficiar outros apps compativeis. O estado do overlay
e persistido pelo Android, sem necessidade de reativa-lo a cada boot. Boot
completo e outros apps ainda precisam de validacao na central.

O servico de operacao continua se a tela for fechada. Um alarme de 90 segundos
e um marcador persistente permitem tentar recuperar operacoes interrompidas.
Depois de ativar, o app verifica reinicio, carga de PipUI/PipManager e estabilidade
breve do processo. Se falhar, tenta desativar o overlay. A recuperacao nao e
garantida em caso de bloqueio das APIs, encerramento forcado ou falhas do sistema;
a interface informa falha e permite tentar Disable PiP. O receptor de boot
trata apenas uma recuperacao pendente, nunca habilita o overlay automaticamente.

## Compilar

Projeto Java nativo, sem Flutter, Gradle, bibliotecas externas ou downloads
durante o build. Requer Windows, JDK 17+, SDK Android API 35 e Build Tools 35.0.0.

```powershell
.\build.ps1 -AndroidSdk E:\android-sdk `
  -JavaHome 'C:\Program Files\Android\Android Studio\jbr' `
  -PlatformKey C:\chaves\platform.pk8 `
  -PlatformCertificate C:\chaves\platform.x509.pem
```

A chave privada e usada apenas para assinar; nao e copiada para o app ou o
diretorio do projeto. Nao publique chaves privadas. O script confere a assinatura
da ROM validada antes de disponibilizar o APK em `dist`.

## Validacao pendente no veiculo

- Instalar pelo gerenciador de arquivos e verificar concessao das permissoes.
- Enable PiP sem overlay previamente instalado e com overlay ja instalado.
- Disable PiP, nova ativacao, estabilidade da interface e controles do video.
- Persistencia ao boot e recuperacao se a operacao for interrompida.

Execute com o veiculo estacionado. A interface da central desaparece brevemente
ao aplicar a alteracao. Solucao experimental, sem garantia de compatibilidade
com outras ROMs; preserve um meio de recuperacao antes da primeira validacao.

Referencias: [RRO](https://source.android.com/docs/core/runtime/rros) e
[PackageInstaller.Session](https://developer.android.com/reference/android/content/pm/PackageInstaller.Session).
